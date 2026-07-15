use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use tokio::sync::Notify;

use panda_engine_core::{
    Account, AuthPort, AuthSession, AuthSessionEnvelope, AuthState, EngineError, EngineErrorType,
    InMemorySessionStore, SessionCoordinator, SessionStore, SessionStoreError,
    SessionStoreSecurity,
};

fn envelope(label: &str, access_expires_at: u64) -> AuthSessionEnvelope {
    AuthSessionEnvelope::new(
        format!("access-{label}"),
        access_expires_at,
        format!("refresh-{label}"),
        10_000,
        Account {
            id: format!("account-{label}"),
            primary_email: format!("{label}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 500,
        },
        AuthSession {
            id: format!("session-{label}"),
            device_label: "car".into(),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 10_000,
            current: true,
        },
    )
}

struct RecordingAuthPort {
    refresh_calls: AtomicUsize,
    logout_calls: AtomicUsize,
    refresh_tokens: Mutex<Vec<String>>,
    refresh_result: Mutex<Result<AuthSessionEnvelope, EngineError>>,
    logout_result: Mutex<Result<(), EngineError>>,
}

impl RecordingAuthPort {
    fn succeeding(replacement: AuthSessionEnvelope) -> Self {
        Self {
            refresh_calls: AtomicUsize::new(0),
            logout_calls: AtomicUsize::new(0),
            refresh_tokens: Mutex::new(vec![]),
            refresh_result: Mutex::new(Ok(replacement)),
            logout_result: Mutex::new(Ok(())),
        }
    }
}

#[async_trait::async_trait]
impl AuthPort for RecordingAuthPort {
    async fn login_password(
        &self,
        _email: &str,
        _password: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        unreachable!("not used by these tests")
    }

    async fn refresh_session(
        &self,
        refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        self.refresh_calls.fetch_add(1, Ordering::SeqCst);
        self.refresh_tokens
            .lock()
            .unwrap()
            .push(refresh_token.to_owned());
        tokio::time::sleep(Duration::from_millis(20)).await;
        self.refresh_result.lock().unwrap().clone()
    }

    async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
        self.logout_calls.fetch_add(1, Ordering::SeqCst);
        self.logout_result.lock().unwrap().clone()
    }
}

#[tokio::test]
async fn concurrent_expiry_runs_one_refresh_and_all_observe_rotation() {
    let store: Arc<dyn SessionStore> =
        Arc::new(InMemorySessionStore::with_session(envelope("old", 999)));
    let auth = Arc::new(RecordingAuthPort::succeeding(envelope("new", 5_000)));
    let coordinator = Arc::new(SessionCoordinator::new(store, auth.clone()));

    let mut tasks = Vec::new();
    for _ in 0..12 {
        let coordinator = coordinator.clone();
        tasks.push(tokio::spawn(async move {
            coordinator.ensure_fresh_session_at(1_000).await
        }));
    }

    for task in tasks {
        assert_eq!(task.await.unwrap().unwrap(), envelope("new", 5_000).state());
    }
    assert_eq!(auth.refresh_calls.load(Ordering::SeqCst), 1);
    assert_eq!(
        auth.refresh_tokens.lock().unwrap().as_slice(),
        ["refresh-old"]
    );
}

#[tokio::test]
async fn ambiguous_refresh_invalidates_session_and_never_replays_old_token() {
    let store: Arc<dyn SessionStore> =
        Arc::new(InMemorySessionStore::with_session(envelope("old", 999)));
    let mut auth_impl = RecordingAuthPort::succeeding(envelope("unused", 5_000));
    auth_impl.refresh_result = Mutex::new(Err(EngineError::new(
        EngineErrorType::Transport,
        "ambiguous transport outcome",
        false,
    )));
    let auth = Arc::new(auth_impl);
    let coordinator = SessionCoordinator::new(store.clone(), auth.clone());

    let first = coordinator
        .ensure_fresh_session_at(1_000)
        .await
        .unwrap_err();
    let second = coordinator
        .ensure_fresh_session_at(1_000)
        .await
        .unwrap_err();

    assert_eq!(first.error_type, EngineErrorType::LoginRequired);
    assert_eq!(second.error_type, EngineErrorType::LoginRequired);
    assert_eq!(auth.refresh_calls.load(Ordering::SeqCst), 1);
    assert_eq!(store.read().unwrap(), None);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

struct FailingReplaceAndClearStore {
    current: Mutex<Option<AuthSessionEnvelope>>,
}

struct FailingReadStore {
    clear_calls: AtomicUsize,
}

impl SessionStore for FailingReadStore {
    fn security_level(&self) -> SessionStoreSecurity {
        SessionStoreSecurity::Ephemeral
    }

    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        Err(SessionStoreError::Unavailable("read failed".into()))
    }

    fn replace(&self, _envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        unreachable!()
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        self.clear_calls.fetch_add(1, Ordering::SeqCst);
        Ok(())
    }
}

impl SessionStore for FailingReplaceAndClearStore {
    fn security_level(&self) -> SessionStoreSecurity {
        SessionStoreSecurity::Ephemeral
    }

    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        Ok(self.current.lock().unwrap().clone())
    }

    fn replace(&self, _envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        Err(SessionStoreError::Unavailable("replace failed".into()))
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        Err(SessionStoreError::Unavailable("clear failed".into()))
    }
}

#[tokio::test]
async fn failed_rotated_store_write_blocks_replay_even_when_clear_fails() {
    let old = envelope("old", 999);
    let store: Arc<dyn SessionStore> = Arc::new(FailingReplaceAndClearStore {
        current: Mutex::new(Some(old.clone())),
    });
    let auth = Arc::new(RecordingAuthPort::succeeding(envelope("new", 5_000)));
    let coordinator = SessionCoordinator::new(store.clone(), auth.clone());

    let first = coordinator
        .ensure_fresh_session_at(1_000)
        .await
        .unwrap_err();
    let second = coordinator
        .ensure_fresh_session_at(1_000)
        .await
        .unwrap_err();

    assert_eq!(first.error_type, EngineErrorType::SessionStorage);
    assert_eq!(second.error_type, EngineErrorType::LoginRequired);
    assert_eq!(auth.refresh_calls.load(Ordering::SeqCst), 1);
    assert_eq!(store.read().unwrap(), Some(old));
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

#[tokio::test]
async fn logout_clears_local_session_even_when_remote_outcome_is_uncertain() {
    let store: Arc<dyn SessionStore> =
        Arc::new(InMemorySessionStore::with_session(envelope("old", 5_000)));
    let mut auth_impl = RecordingAuthPort::succeeding(envelope("unused", 5_000));
    auth_impl.logout_result = Mutex::new(Err(EngineError::new(
        EngineErrorType::Transport,
        "remote logout outcome unknown",
        false,
    )));
    let auth = Arc::new(auth_impl);
    let coordinator = SessionCoordinator::new(store.clone(), auth.clone());

    let error = coordinator.logout().await.unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::Transport);
    assert_eq!(auth.logout_calls.load(Ordering::SeqCst), 1);
    assert_eq!(store.read().unwrap(), None);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::Anonymous);
}

#[tokio::test]
async fn logout_attempts_clear_and_invalidates_even_when_store_read_fails() {
    let store = Arc::new(FailingReadStore {
        clear_calls: AtomicUsize::new(0),
    });
    let auth = Arc::new(RecordingAuthPort::succeeding(envelope("unused", 5_000)));
    let coordinator = SessionCoordinator::new(store.clone(), auth.clone());

    let error = coordinator.logout().await.unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::SessionStorage);
    assert_eq!(store.clear_calls.load(Ordering::SeqCst), 1);
    assert_eq!(auth.logout_calls.load(Ordering::SeqCst), 0);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

struct BlockingRefreshAuthPort {
    refresh_calls: AtomicUsize,
    refresh_started: Notify,
    never_complete: Notify,
}

#[async_trait::async_trait]
impl AuthPort for BlockingRefreshAuthPort {
    async fn login_password(
        &self,
        _email: &str,
        _password: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        unreachable!()
    }

    async fn refresh_session(
        &self,
        _refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        self.refresh_calls.fetch_add(1, Ordering::SeqCst);
        self.refresh_started.notify_one();
        self.never_complete.notified().await;
        unreachable!()
    }

    async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
        unreachable!()
    }
}

#[tokio::test]
async fn cancelled_refresh_cannot_replay_an_ambiguously_consumed_token() {
    let store: Arc<dyn SessionStore> =
        Arc::new(InMemorySessionStore::with_session(envelope("old", 999)));
    let auth = Arc::new(BlockingRefreshAuthPort {
        refresh_calls: AtomicUsize::new(0),
        refresh_started: Notify::new(),
        never_complete: Notify::new(),
    });
    let coordinator = Arc::new(SessionCoordinator::new(store.clone(), auth.clone()));
    let refresh = {
        let coordinator = coordinator.clone();
        tokio::spawn(async move { coordinator.ensure_fresh_session_at(1_000).await })
    };
    auth.refresh_started.notified().await;

    assert_eq!(store.read().unwrap(), Some(envelope("old", 999)));
    assert_eq!(
        coordinator.auth_state().unwrap(),
        envelope("old", 999).state()
    );

    refresh.abort();
    assert!(refresh.await.unwrap_err().is_cancelled());
    let error = tokio::time::timeout(
        Duration::from_millis(100),
        coordinator.ensure_fresh_session_at(1_000),
    )
    .await
    .expect("cancelled refresh must fail closed without starting another RPC")
    .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::LoginRequired);
    assert_eq!(auth.refresh_calls.load(Ordering::SeqCst), 1);
    assert_eq!(store.read().unwrap(), None);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

#[tokio::test]
async fn logout_reports_local_clear_failure_when_remote_outcome_is_also_uncertain() {
    let old = envelope("old", 5_000);
    let store: Arc<dyn SessionStore> = Arc::new(FailingReplaceAndClearStore {
        current: Mutex::new(Some(old.clone())),
    });
    let mut auth_impl = RecordingAuthPort::succeeding(envelope("unused", 5_000));
    auth_impl.logout_result = Mutex::new(Err(EngineError::new(
        EngineErrorType::Transport,
        "remote outcome uncertain",
        false,
    )));
    let auth = Arc::new(auth_impl);
    let coordinator = SessionCoordinator::new(store.clone(), auth);

    let error = coordinator.logout().await.unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::SessionStorage);
    assert!(error.message.contains("remote logout outcome is uncertain"));
    assert_eq!(store.read().unwrap(), Some(old));
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}

struct BlockingLogoutAuthPort {
    logout_started: Notify,
    never_complete: Notify,
}

#[async_trait::async_trait]
impl AuthPort for BlockingLogoutAuthPort {
    async fn login_password(
        &self,
        _email: &str,
        _password: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        unreachable!()
    }

    async fn refresh_session(
        &self,
        _refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        unreachable!()
    }

    async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
        self.logout_started.notify_one();
        self.never_complete.notified().await;
        unreachable!()
    }
}

#[tokio::test]
async fn cancelled_logout_has_already_cleared_and_invalidated_local_session() {
    let store: Arc<dyn SessionStore> =
        Arc::new(InMemorySessionStore::with_session(envelope("old", 5_000)));
    let auth = Arc::new(BlockingLogoutAuthPort {
        logout_started: Notify::new(),
        never_complete: Notify::new(),
    });
    let coordinator = Arc::new(SessionCoordinator::new(store.clone(), auth.clone()));
    let logout = {
        let coordinator = coordinator.clone();
        tokio::spawn(async move { coordinator.logout().await })
    };
    auth.logout_started.notified().await;

    logout.abort();
    assert!(logout.await.unwrap_err().is_cancelled());

    assert_eq!(store.read().unwrap(), None);
    assert_eq!(coordinator.auth_state().unwrap(), AuthState::LoginRequired);
}
