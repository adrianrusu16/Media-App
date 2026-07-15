use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use tokio::sync::Mutex;

use crate::{AuthPort, AuthState, EngineError, EngineErrorType, SessionStore};

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum AccessSnapshot {
    Anonymous,
    Authenticated { token: String },
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
}

impl SessionCoordinator {
    pub fn new(store: Arc<dyn SessionStore>, auth: Arc<dyn AuthPort>) -> Self {
        Self {
            store,
            auth,
            rotation: Mutex::new(()),
            invalidated: AtomicBool::new(false),
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

    pub async fn ensure_fresh_session(
        &self,
        now_epoch_millis: u64,
    ) -> Result<AuthState, EngineError> {
        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }

        let current = self
            .store
            .read()
            .map_err(EngineError::from)?
            .ok_or_else(login_required)?;
        let current = self
            .fresh_envelope_locked(current, now_epoch_millis)
            .await?;
        Ok(current.state())
    }

    pub(crate) async fn fresh_access_snapshot(
        &self,
        now_epoch_millis: u64,
    ) -> Result<AccessSnapshot, EngineError> {
        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }

        let Some(current) = self.store.read().map_err(EngineError::from)? else {
            return Ok(AccessSnapshot::Anonymous);
        };
        let current = self
            .fresh_envelope_locked(current, now_epoch_millis)
            .await?;
        Ok(access_snapshot(&current))
    }

    pub(crate) async fn refresh_after_rejection(
        &self,
        rejected: &AccessSnapshot,
        now_epoch_millis: u64,
    ) -> Result<AccessSnapshot, EngineError> {
        let AccessSnapshot::Authenticated {
            token: rejected_token,
        } = rejected
        else {
            return Err(login_required());
        };

        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Err(login_required());
        }
        let current = self
            .store
            .read()
            .map_err(EngineError::from)?
            .ok_or_else(login_required)?;
        if current.credentials().access_token != rejected_token {
            let current = self
                .fresh_envelope_locked(current, now_epoch_millis)
                .await?;
            return Ok(access_snapshot(&current));
        }

        let replacement = self.refresh_locked(current, now_epoch_millis).await?;
        Ok(access_snapshot(&replacement))
    }

    pub(crate) async fn invalidate_if_current(
        &self,
        rejected: &AccessSnapshot,
    ) -> Result<(), EngineError> {
        let AccessSnapshot::Authenticated {
            token: rejected_token,
        } = rejected
        else {
            return Ok(());
        };

        let _rotation = self.rotation.lock().await;
        if self.invalidated.load(Ordering::Acquire) {
            return Ok(());
        }
        let current = self.store.read().map_err(EngineError::from)?;
        if current
            .as_ref()
            .is_some_and(|envelope| envelope.credentials().access_token == rejected_token)
        {
            self.invalidate();
        }
        Ok(())
    }

    async fn fresh_envelope_locked(
        &self,
        current: crate::AuthSessionEnvelope,
        now_epoch_millis: u64,
    ) -> Result<crate::AuthSessionEnvelope, EngineError> {
        let credentials = current.credentials();
        if credentials.access_token_expires_at_epoch_millis > now_epoch_millis {
            return Ok(current);
        }
        self.refresh_locked(current, now_epoch_millis).await
    }

    async fn refresh_locked(
        &self,
        current: crate::AuthSessionEnvelope,
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

        if let Err(error) = self.store.replace(replacement.clone()) {
            return Err(error.into());
        }
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

        if let Some(error) = read_error {
            return match clear_result {
                Ok(()) => Err(error),
                Err(clear_error) => Err(clear_error.into()),
            };
        }

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
}

fn access_snapshot(envelope: &crate::AuthSessionEnvelope) -> AccessSnapshot {
    AccessSnapshot::Authenticated {
        token: envelope.credentials().access_token.to_owned(),
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

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use super::{AccessSnapshot, SessionCoordinator};
    use crate::{
        Account, AuthPort, AuthSession, AuthSessionEnvelope, EngineError, InMemorySessionStore,
        SessionStore,
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
    async fn late_rejection_does_not_invalidate_a_newer_session() {
        let store = Arc::new(InMemorySessionStore::with_session(envelope()));
        let coordinator = SessionCoordinator::new(store.clone(), Arc::new(UnusedAuthPort));
        let rejected = AccessSnapshot::Authenticated {
            token: "current-access".into(),
        };
        let newer = AuthSessionEnvelope::new(
            "newer-access".into(),
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
        store.replace(newer).unwrap();

        coordinator.invalidate_if_current(&rejected).await.unwrap();

        assert!(matches!(
            coordinator.auth_state().unwrap(),
            crate::AuthState::Authenticated { .. }
        ));
        assert_eq!(
            coordinator.access_token_snapshot().unwrap().as_deref(),
            Some("newer-access")
        );
    }
}
