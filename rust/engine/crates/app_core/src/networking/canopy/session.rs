use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};

use tokio::sync::Mutex;

use crate::{
    AuthPort, AuthRequestAcceptance, AuthState, AuthStateProvider, EngineError, EngineErrorType,
    EngineHistoryIdentity, SessionStore,
};

use super::clock::current_epoch_millis;

#[derive(Clone, Copy)]
enum ExpiryClock {
    System,
    Fixed(u64),
}

impl ExpiryClock {
    fn now(self) -> Result<u64, EngineError> {
        match self {
            Self::System => current_epoch_millis(),
            Self::Fixed(now) => Ok(now),
        }
    }
}

#[derive(Clone, Eq, PartialEq)]
pub(crate) enum AccessSnapshot {
    Anonymous,
    Authenticated {
        token: String,
        generation: u64,
        identity: EngineHistoryIdentity,
    },
}

impl std::fmt::Debug for AccessSnapshot {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Anonymous => formatter.write_str("Anonymous"),
            Self::Authenticated { generation, .. } => formatter
                .debug_struct("Authenticated")
                .field("token", &"[REDACTED]")
                .field("generation", generation)
                .finish(),
        }
    }
}

/// Rust-owned authentication session orchestration for the Canopy adapter.
///
/// The coordinator serializes refresh rotation and is the only component that
/// writes a rotated envelope. Credential access remains inside the Canopy
/// module tree.
pub struct SessionCoordinator {
    store: Arc<dyn SessionStore>,
    auth: Arc<dyn AuthPort>,
    rotation: Mutex<()>,
    invalidated: AtomicBool,
    generation: AtomicU64,
}

impl SessionCoordinator {
    pub fn new(store: Arc<dyn SessionStore>, auth: Arc<dyn AuthPort>) -> Self {
        Self {
            store,
            auth,
            rotation: Mutex::new(()),
            invalidated: AtomicBool::new(false),
            generation: AtomicU64::new(0),
        }
    }

    pub fn auth_state(&self) -> Result<AuthState, EngineError> {
        if self.invalidated.load(Ordering::Acquire) {
            return Ok(AuthState::LoginRequired);
        }

        Ok(self
            .store
            .read()
            .map_err(EngineError::from)?
            .map_or(AuthState::Anonymous, |envelope| envelope.state()))
    }

    pub async fn register_password(
        &self,
        email: &str,
        password: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        self.auth.register_password(email, password).await
    }

    pub async fn resend_verification(
        &self,
        email: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        self.auth.resend_verification(email).await
    }

    pub async fn verify_email(
        &self,
        verification_token: &str,
        device_label: &str,
    ) -> Result<AuthState, EngineError> {
        let _rotation = self.rotation.lock().await;
        let envelope = self
            .auth
            .verify_email(verification_token, device_label)
            .await?;
        self.commit_interactive_session(envelope).await
    }

    pub async fn login_password(
        &self,
        email: &str,
        password: &str,
        device_label: &str,
    ) -> Result<AuthState, EngineError> {
        let _rotation = self.rotation.lock().await;
        let envelope = self
            .auth
            .login_password(email, password, device_label)
            .await?;
        self.commit_interactive_session(envelope).await
    }

    async fn commit_interactive_session(
        &self,
        envelope: crate::AuthSessionEnvelope,
    ) -> Result<AuthState, EngineError> {
        let state = envelope.state();
        let mut transient_access_token = envelope.credentials().access_token.to_owned();
        let commit_result = self
            .store
            .replace(envelope)
            .map_err(EngineError::from)
            .and_then(|()| self.advance_generation().map(|_| ()));

        match commit_result {
            Ok(()) => {
                self.invalidated.store(false, Ordering::Release);
                wipe_token(&mut transient_access_token);
                Ok(state)
            }
            Err(error) => {
                self.invalidated.store(true, Ordering::Release);
                let _ = self.store.clear();
                let _ = self.auth.logout(&transient_access_token).await;
                wipe_token(&mut transient_access_token);
                Err(error)
            }
        }
    }

    pub async fn ensure_fresh_session(&self) -> Result<AuthState, EngineError> {
        self.ensure_fresh_session_with_clock(ExpiryClock::System)
            .await
    }

    #[doc(hidden)]
    pub async fn ensure_fresh_session_at(
        &self,
        now_epoch_millis: u64,
    ) -> Result<AuthState, EngineError> {
        self.ensure_fresh_session_with_clock(ExpiryClock::Fixed(now_epoch_millis))
            .await
    }

    async fn ensure_fresh_session_with_clock(
        &self,
        clock: ExpiryClock,
    ) -> Result<AuthState, EngineError> {
        let _rotation = self.rotation.lock().await;
        let now_epoch_millis = clock.now()?;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }

        let current = self
            .store
            .read()
            .map_err(EngineError::from)?
            .ok_or_else(login_required)?;
        let current = self
            .fresh_envelope_locked(current, clock, now_epoch_millis)
            .await?;
        Ok(current.state())
    }

    pub(crate) async fn fresh_access_snapshot(&self) -> Result<AccessSnapshot, EngineError> {
        self.fresh_access_snapshot_with_clock(ExpiryClock::System)
            .await
    }

    #[cfg(test)]
    pub(crate) async fn fresh_access_snapshot_at(
        &self,
        now_epoch_millis: u64,
    ) -> Result<AccessSnapshot, EngineError> {
        self.fresh_access_snapshot_with_clock(ExpiryClock::Fixed(now_epoch_millis))
            .await
    }

    async fn fresh_access_snapshot_with_clock(
        &self,
        clock: ExpiryClock,
    ) -> Result<AccessSnapshot, EngineError> {
        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }

        let Some(current) = self.store.read().map_err(EngineError::from)? else {
            return Ok(AccessSnapshot::Anonymous);
        };
        let now_epoch_millis = clock.now()?;
        let current = self
            .fresh_envelope_locked(current, clock, now_epoch_millis)
            .await?;
        Ok(self.access_snapshot(&current))
    }

    pub(crate) async fn refresh_after_rejection(
        &self,
        rejected: &AccessSnapshot,
    ) -> Result<AccessSnapshot, EngineError> {
        self.refresh_after_rejection_with_clock(rejected, ExpiryClock::System)
            .await
    }

    #[cfg(test)]
    pub(crate) async fn refresh_after_rejection_at(
        &self,
        rejected: &AccessSnapshot,
        now_epoch_millis: u64,
    ) -> Result<AccessSnapshot, EngineError> {
        self.refresh_after_rejection_with_clock(rejected, ExpiryClock::Fixed(now_epoch_millis))
            .await
    }

    async fn refresh_after_rejection_with_clock(
        &self,
        rejected: &AccessSnapshot,
        clock: ExpiryClock,
    ) -> Result<AccessSnapshot, EngineError> {
        let AccessSnapshot::Authenticated {
            generation: rejected_generation,
            ..
        } = rejected
        else {
            return Err(login_required());
        };

        let _rotation = self.rotation.lock().await;
        let now_epoch_millis = clock.now()?;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }
        if self.generation.load(Ordering::Acquire) != *rejected_generation {
            let current = match self.store.read() {
                Ok(Some(current)) => current,
                Ok(None) => {
                    self.invalidate();
                    return Err(login_required());
                }
                Err(error) => return Err(error.into()),
            };
            let current = self
                .fresh_envelope_locked(current, clock, now_epoch_millis)
                .await?;
            return Ok(self.access_snapshot(&current));
        }

        let current = match self.store.read() {
            Ok(Some(current)) => current,
            Ok(None) => {
                self.invalidate();
                return Err(login_required());
            }
            Err(error) => {
                self.invalidate();
                return Err(error.into());
            }
        };

        let replacement = self
            .refresh_locked(current, clock, now_epoch_millis)
            .await?;
        Ok(self.access_snapshot(&replacement))
    }

    pub(crate) async fn invalidate_if_current(
        &self,
        rejected: &AccessSnapshot,
    ) -> Result<(), EngineError> {
        let AccessSnapshot::Authenticated {
            generation: rejected_generation,
            ..
        } = rejected
        else {
            return Ok(());
        };

        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Ok(());
        }
        if self.generation.load(Ordering::Acquire) == *rejected_generation {
            self.invalidate();
        }
        Ok(())
    }

    async fn fresh_envelope_locked(
        &self,
        current: crate::AuthSessionEnvelope,
        clock: ExpiryClock,
        now_epoch_millis: u64,
    ) -> Result<crate::AuthSessionEnvelope, EngineError> {
        let credentials = current.credentials();
        if credentials.access_token_expires_at_epoch_millis > now_epoch_millis {
            return Ok(current);
        }
        self.refresh_locked(current, clock, now_epoch_millis).await
    }

    async fn refresh_locked(
        &self,
        current: crate::AuthSessionEnvelope,
        clock: ExpiryClock,
        now_epoch_millis: u64,
    ) -> Result<crate::AuthSessionEnvelope, EngineError> {
        let credentials = current.credentials();
        if credentials.refresh_token_expires_at_epoch_millis <= now_epoch_millis {
            self.invalidate();
            return Err(login_required());
        }

        let refresh_token = credentials.refresh_token.to_owned();
        let mut attempt = RefreshAttemptGuard::new(self);
        let replacement = match self.auth.refresh_session(&refresh_token).await {
            Ok(replacement) => replacement,
            Err(_) => return Err(login_required()),
        };

        let after_refresh_epoch_millis = clock.now()?;
        let replacement_credentials = replacement.credentials();
        if replacement_credentials.access_token_expires_at_epoch_millis
            <= after_refresh_epoch_millis
            || replacement_credentials.refresh_token_expires_at_epoch_millis
                <= after_refresh_epoch_millis
        {
            return Err(login_required());
        }

        if let Err(error) = self.store.replace(replacement.clone()) {
            return Err(error.into());
        }
        self.advance_generation()?;
        attempt.disarm();

        Ok(replacement)
    }

    pub async fn logout(&self) -> Result<(), EngineError> {
        let _rotation = self.rotation.lock().await;
        let (access_token, read_error) = match self.store.read() {
            Ok(Some(envelope)) => (Some(envelope.credentials().access_token.to_owned()), None),
            Ok(None) => (None, None),
            Err(error) => (None, Some(EngineError::from(error))),
        };

        self.invalidated.store(true, Ordering::Release);
        let clear_result = self.store.clear();
        let generation_result = self.advance_generation();

        if let Some(error) = read_error {
            return match clear_result {
                Ok(()) => Err(error),
                Err(clear_error) => Err(clear_error.into()),
            };
        }
        generation_result?;

        let remote_result = match access_token {
            Some(access_token) => self.auth.logout(&access_token).await,
            None => Ok(()),
        };

        match (clear_result, remote_result) {
            (Ok(()), remote_result) => {
                self.invalidated.store(false, Ordering::Release);
                remote_result
            }
            (Err(error), Ok(())) => Err(error.into()),
            (Err(_), Err(_)) => Err(EngineError::new(
                EngineErrorType::SessionStorage,
                "local session clear failed and the remote logout outcome is uncertain",
                false,
            )),
        }
    }

    fn invalidate(&self) {
        self.invalidated.store(true, Ordering::Release);
        let _ = self.store.clear();
        let _ = self.advance_generation();
    }

    #[allow(dead_code)]
    pub(crate) fn access_token_snapshot(&self) -> Result<Option<String>, EngineError> {
        if self.invalidated.load(Ordering::Acquire) {
            return Ok(None);
        }
        Ok(self
            .store
            .read()
            .map_err(EngineError::from)?
            .map(|envelope| envelope.credentials().access_token.to_owned()))
    }

    #[allow(dead_code)]
    pub(crate) async fn install_session(
        &self,
        envelope: crate::AuthSessionEnvelope,
    ) -> Result<(), EngineError> {
        let _rotation = self.rotation.lock().await;
        self.store.replace(envelope).map_err(EngineError::from)?;
        self.advance_generation()?;
        self.invalidated.store(false, Ordering::Release);
        Ok(())
    }

    fn access_snapshot(&self, envelope: &crate::AuthSessionEnvelope) -> AccessSnapshot {
        let crate::AuthState::Authenticated { account, session } = envelope.state() else {
            unreachable!("credential envelope always has an authenticated identity")
        };
        AccessSnapshot::Authenticated {
            token: envelope.credentials().access_token.to_owned(),
            generation: self.generation.load(Ordering::Acquire),
            identity: EngineHistoryIdentity {
                account_id: account.id,
                session_id: session.id,
            },
        }
    }

    fn advance_generation(&self) -> Result<u64, EngineError> {
        self.generation
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |current| {
                current.checked_add(1)
            })
            .map(|previous| previous + 1)
            .map_err(|_| {
                self.invalidated.store(true, Ordering::Release);
                let _ = self.store.clear();
                EngineError::new(
                    EngineErrorType::SessionStorage,
                    "session generation space exhausted",
                    false,
                )
            })
    }
}

impl AuthStateProvider for SessionCoordinator {
    fn current_auth_state(&self) -> AuthState {
        self.auth_state().unwrap_or(AuthState::LoginRequired)
    }
}

struct RefreshAttemptGuard<'a> {
    coordinator: &'a SessionCoordinator,
    armed: bool,
}

impl<'a> RefreshAttemptGuard<'a> {
    fn new(coordinator: &'a SessionCoordinator) -> Self {
        Self {
            coordinator,
            armed: true,
        }
    }

    fn disarm(&mut self) {
        self.armed = false;
    }
}

impl Drop for RefreshAttemptGuard<'_> {
    fn drop(&mut self) {
        if self.armed {
            self.coordinator.invalidate();
        }
    }
}

fn login_required() -> EngineError {
    EngineError::new(
        EngineErrorType::LoginRequired,
        "a new interactive login is required",
        false,
    )
}

fn wipe_token(token: &mut str) {
    // Replacing UTF-8 bytes with zero preserves String's validity while clearing
    // the transient credential before its allocation is released.
    unsafe { token.as_bytes_mut().fill(0) };
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

    use super::{AccessSnapshot, SessionCoordinator};
    use crate::{
        Account, AuthPort, AuthSession, AuthSessionEnvelope, EngineError, EngineHistoryIdentity,
        InMemorySessionStore, SessionStore, SessionStoreError, SessionStoreSecurity,
    };

    struct UnusedAuthPort;

    #[async_trait::async_trait]
    impl AuthPort for UnusedAuthPort {
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
            unreachable!()
        }
    }

    fn envelope() -> AuthSessionEnvelope {
        AuthSessionEnvelope::new(
            "current-access".into(),
            5_000,
            "current-refresh".into(),
            10_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 100,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "car".into(),
                created_at_epoch_millis: 100,
                last_used_at_epoch_millis: 200,
                expires_at_epoch_millis: 10_000,
                current: true,
            },
        )
    }

    #[test]
    fn access_snapshot_reads_only_the_current_store_value() {
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::with_session(envelope()));
        let coordinator = SessionCoordinator::new(store, Arc::new(UnusedAuthPort));

        assert_eq!(
            coordinator.access_token_snapshot().unwrap().as_deref(),
            Some("current-access")
        );
    }

    #[test]
    fn anonymous_access_snapshot_contains_no_token() {
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::new());
        let coordinator = SessionCoordinator::new(store, Arc::new(UnusedAuthPort));

        assert_eq!(coordinator.access_token_snapshot().unwrap(), None);
    }

    #[tokio::test]
    async fn late_rejection_does_not_invalidate_newer_identical_token_generation() {
        let store = Arc::new(InMemorySessionStore::with_session(envelope()));
        let coordinator = SessionCoordinator::new(store.clone(), Arc::new(UnusedAuthPort));
        let rejected = AccessSnapshot::Authenticated {
            token: "current-access".into(),
            generation: 0,
            identity: EngineHistoryIdentity {
                account_id: "account-1".into(),
                session_id: "session-1".into(),
            },
        };
        let newer = AuthSessionEnvelope::new(
            "current-access".into(),
            8_000,
            "newer-refresh".into(),
            12_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 100,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "car".into(),
                created_at_epoch_millis: 100,
                last_used_at_epoch_millis: 300,
                expires_at_epoch_millis: 12_000,
                current: true,
            },
        );
        coordinator.install_session(newer).await.unwrap();

        coordinator.invalidate_if_current(&rejected).await.unwrap();

        assert!(matches!(
            coordinator.auth_state().unwrap(),
            crate::AuthState::Authenticated { .. }
        ));
        assert_eq!(
            coordinator.access_token_snapshot().unwrap().as_deref(),
            Some("current-access")
        );
    }

    #[test]
    fn access_snapshot_debug_redacts_the_raw_token() {
        let snapshot = AccessSnapshot::Authenticated {
            token: "raw-access-secret".into(),
            generation: 42,
            identity: EngineHistoryIdentity {
                account_id: "account-1".into(),
                session_id: "session-1".into(),
            },
        };

        let rendered = format!("{snapshot:?}");

        assert!(!rendered.contains("raw-access-secret"));
        assert!(rendered.contains("REDACTED"));
    }

    struct ToggleReadStore {
        fail_reads: AtomicBool,
        clear_calls: AtomicUsize,
        current: std::sync::Mutex<Option<AuthSessionEnvelope>>,
    }

    impl SessionStore for ToggleReadStore {
        fn security_level(&self) -> SessionStoreSecurity {
            SessionStoreSecurity::Ephemeral
        }

        fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
            if self.fail_reads.load(Ordering::SeqCst) {
                Err(SessionStoreError::Unavailable("read failed".into()))
            } else {
                Ok(self.current.lock().unwrap().clone())
            }
        }

        fn replace(&self, envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
            *self.current.lock().unwrap() = Some(envelope);
            Ok(())
        }

        fn clear(&self) -> Result<(), SessionStoreError> {
            self.clear_calls.fetch_add(1, Ordering::SeqCst);
            *self.current.lock().unwrap() = None;
            Ok(())
        }
    }

    #[tokio::test]
    async fn rejected_current_generation_read_failure_latches_invalidation() {
        let store = Arc::new(ToggleReadStore {
            fail_reads: AtomicBool::new(false),
            clear_calls: AtomicUsize::new(0),
            current: std::sync::Mutex::new(Some(envelope())),
        });
        let coordinator = SessionCoordinator::new(store.clone(), Arc::new(UnusedAuthPort));
        let snapshot = coordinator.fresh_access_snapshot_at(1_000).await.unwrap();
        store.fail_reads.store(true, Ordering::SeqCst);

        let error = coordinator
            .refresh_after_rejection_at(&snapshot, 1_000)
            .await
            .unwrap_err();

        assert_eq!(error.error_type, crate::EngineErrorType::SessionStorage);
        assert_eq!(store.clear_calls.load(Ordering::SeqCst), 1);
        assert_eq!(
            coordinator.auth_state().unwrap(),
            crate::AuthState::LoginRequired
        );
        store.fail_reads.store(false, Ordering::SeqCst);
        assert_eq!(store.read().unwrap(), None);
        assert_eq!(
            coordinator
                .fresh_access_snapshot_at(1_000)
                .await
                .unwrap_err()
                .error_type,
            crate::EngineErrorType::LoginRequired
        );
    }
}
