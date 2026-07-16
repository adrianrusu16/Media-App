use std::sync::{
    Arc, Mutex,
    atomic::{AtomicUsize, Ordering},
};

use panda_engine_core::{
    Account, AuthPort, AuthRequestAcceptance, AuthSession, AuthSessionEnvelope, AuthState,
    EngineError, InMemorySessionStore, SessionCoordinator, SessionStore, SessionStoreError,
    SessionStoreSecurity,
};

fn envelope(label: &str) -> AuthSessionEnvelope {
    AuthSessionEnvelope::new(
        format!("access-{label}"),
        2_000,
        format!("refresh-{label}"),
        3_000,
        Account {
            id: format!("account-{label}"),
            primary_email: format!("{label}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 500,
        },
        AuthSession {
            id: format!("session-{label}"),
            device_label: "PandaWave".into(),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 4_000,
            current: true,
        },
    )
}

struct RecordingAuthPort {
    logout_calls: AtomicUsize,
    logged_out_tokens: Mutex<Vec<String>>,
}

impl RecordingAuthPort {
    fn new() -> Self {
        Self {
            logout_calls: AtomicUsize::new(0),
            logged_out_tokens: Mutex::new(Vec::new()),
        }
    }
}

#[async_trait::async_trait]
impl AuthPort for RecordingAuthPort {
    async fn register_password(
        &self,
        _email: &str,
        _password: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        Ok(AuthRequestAcceptance::accepted())
    }

    async fn resend_verification(
        &self,
        _email: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        Ok(AuthRequestAcceptance::accepted())
    }

    async fn verify_email(
        &self,
        _verification_token: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Ok(envelope("verified"))
    }

    async fn login_password(
        &self,
        _email: &str,
        _password: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Ok(envelope("login"))
    }

    async fn refresh_session(
        &self,
        _refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Ok(envelope("refresh"))
    }

    async fn logout(&self, access_token: &str) -> Result<(), EngineError> {
        self.logout_calls.fetch_add(1, Ordering::SeqCst);
        self.logged_out_tokens
            .lock()
            .unwrap()
            .push(access_token.to_owned());
        Ok(())
    }
}

#[tokio::test]
async fn register_and_resend_return_only_generic_acceptance() {
    let auth = Arc::new(RecordingAuthPort::new());
    let coordinator = SessionCoordinator::new(Arc::new(InMemorySessionStore::new()), auth);

    assert!(
        coordinator
            .register_password("driver@example.com", "password")
            .await
            .unwrap()
            .is_accepted()
    );
    assert!(
        coordinator
            .resend_verification("driver@example.com")
            .await
            .unwrap()
            .is_accepted()
    );
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::Anonymous);
}

#[tokio::test]
async fn login_and_verification_publish_only_after_complete_store_commit() {
    let auth = Arc::new(RecordingAuthPort::new());
    let login = SessionCoordinator::new(Arc::new(InMemorySessionStore::new()), auth.clone());

    let login_state = login
        .login_password("driver@example.com", "password", "PandaWave")
        .await
        .unwrap();
    assert_eq!(login_state, envelope("login").state());
    assert_eq!(login.auth_state().unwrap(), login_state);

    let verification = SessionCoordinator::new(Arc::new(InMemorySessionStore::new()), auth);
    let verified_state = verification
        .verify_email("opaque-verification-token", "PandaWave")
        .await
        .unwrap();
    assert_eq!(verified_state, envelope("verified").state());
    assert_eq!(verification.auth_state().unwrap(), verified_state);
}

struct FailingReplaceStore;

impl SessionStore for FailingReplaceStore {
    fn security_level(&self) -> SessionStoreSecurity {
        SessionStoreSecurity::DurableSecure
    }

    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        Ok(None)
    }

    fn replace(&self, _envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        Err(SessionStoreError::Unavailable(
            "injected commit failure".into(),
        ))
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        Ok(())
    }
}

#[tokio::test]
async fn failed_login_commit_revokes_transient_remote_session_and_fails_closed() {
    let auth = Arc::new(RecordingAuthPort::new());
    let coordinator = SessionCoordinator::new(Arc::new(FailingReplaceStore), auth.clone());

    let error = coordinator
        .login_password("driver@example.com", "password", "PandaWave")
        .await
        .unwrap_err();

    assert_eq!(
        error.error_type,
        panda_engine_core::EngineErrorType::SessionStorage
    );
    assert_eq!(auth.logout_calls.load(Ordering::SeqCst), 1);
    assert_eq!(
        auth.logged_out_tokens.lock().unwrap().as_slice(),
        ["access-login"]
    );
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

#[tokio::test]
async fn failed_verification_commit_also_revokes_and_fails_closed() {
    let auth = Arc::new(RecordingAuthPort::new());
    let coordinator = SessionCoordinator::new(Arc::new(FailingReplaceStore), auth.clone());

    coordinator
        .verify_email("opaque-verification-token", "PandaWave")
        .await
        .unwrap_err();

    assert_eq!(auth.logout_calls.load(Ordering::SeqCst), 1);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}
