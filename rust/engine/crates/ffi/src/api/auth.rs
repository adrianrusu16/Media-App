use std::time::Instant;

use panda_engine_core::{
    AuthState, EngineError, EngineErrorType, validate_production_session_store,
};
use tracing::info;

use crate::engine_handle::{EngineAuthRuntime, PandaEngine};

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum AuthOperationResult {
    Accepted(bool),
    Authenticated,
    Anonymous,
    Error(EngineError),
}

impl AuthOperationResult {
    pub(crate) fn to_strings(&self) -> [String; 3] {
        match self {
            Self::Accepted(true) => result("accepted", "", ""),
            Self::Accepted(false) => result("rejected", "", ""),
            Self::Authenticated => result("authenticated", "", ""),
            Self::Anonymous => result("anonymous", "", ""),
            Self::Error(error) => result(
                "error",
                error_type_name(&error.error_type),
                error
                    .retry_after_millis
                    .map(|value| value.to_string())
                    .as_deref()
                    .unwrap_or(""),
            ),
        }
    }
}

pub(crate) fn register_password(
    engine: &PandaEngine,
    email: &str,
    password: &str,
) -> AuthOperationResult {
    with_runtime(engine, true, |runtime| {
        engine
            .runtime
            .block_on(runtime.coordinator.register_password(email, password))
            .map(|acceptance| AuthOperationResult::Accepted(acceptance.is_accepted()))
    })
}

pub(crate) fn resend_verification(engine: &PandaEngine, email: &str) -> AuthOperationResult {
    with_runtime(engine, false, |runtime| {
        engine
            .runtime
            .block_on(runtime.coordinator.resend_verification(email))
            .map(|acceptance| AuthOperationResult::Accepted(acceptance.is_accepted()))
    })
}

pub(crate) fn verify_email(
    engine: &PandaEngine,
    verification_token: &str,
    device_label: &str,
) -> AuthOperationResult {
    with_runtime(engine, true, |runtime| {
        let started = Instant::now();
        let state = engine.runtime.block_on(
            runtime
                .coordinator
                .verify_email(verification_token, device_label),
        )?;
        publish_committed_session(engine, "verify_email", started, state);
        Ok(AuthOperationResult::Authenticated)
    })
}

pub(crate) fn login_password(
    engine: &PandaEngine,
    email: &str,
    password: &str,
    device_label: &str,
) -> AuthOperationResult {
    with_runtime(engine, true, |runtime| {
        let started = Instant::now();
        let state = engine.runtime.block_on(runtime.coordinator.login_password(
            email,
            password,
            device_label,
        ))?;
        publish_committed_session(engine, "login_password", started, state);
        Ok(AuthOperationResult::Authenticated)
    })
}

pub(crate) fn logout(engine: &PandaEngine) -> AuthOperationResult {
    with_runtime(engine, false, |runtime| {
        let started = Instant::now();
        engine.runtime.block_on(runtime.coordinator.logout())?;
        publish_committed_session(engine, "logout", started, AuthState::Anonymous);
        Ok(AuthOperationResult::Anonymous)
    })
}

fn publish_committed_session(
    engine: &PandaEngine,
    operation: &str,
    started: Instant,
    state: AuthState,
) {
    info!(
        operation,
        elapsed_ms = started.elapsed().as_millis() as u64,
        "auth.session_committed"
    );
    engine.engine.publish_session_auth_state(state);
    let snapshot_auth = match engine.engine.snapshot().auth_state {
        AuthState::Anonymous => "anonymous",
        AuthState::Authenticated { .. } => "authenticated",
        AuthState::LoginRequired => "login_required",
    };
    info!(
        operation,
        elapsed_ms = started.elapsed().as_millis() as u64,
        snapshot_auth,
        "auth.snapshot_published"
    );
}

fn with_runtime(
    engine: &PandaEngine,
    requires_secure_store: bool,
    operation: impl FnOnce(&EngineAuthRuntime) -> Result<AuthOperationResult, EngineError>,
) -> AuthOperationResult {
    let runtime = engine.auth_runtime.lock().unwrap().clone();
    let Some(runtime) = runtime else {
        return AuthOperationResult::Error(EngineError::new(
            EngineErrorType::ServiceUnavailable,
            "authentication backend is unavailable",
            false,
        ));
    };
    if requires_secure_store
        && runtime.production
        && let Err(error) = validate_production_session_store(runtime.store.as_ref())
    {
        return AuthOperationResult::Error(error.into());
    }
    operation(&runtime).unwrap_or_else(AuthOperationResult::Error)
}

fn result(status: &str, error_type: &str, retry_after: &str) -> [String; 3] {
    [status.into(), error_type.into(), retry_after.into()]
}

fn error_type_name(error_type: &EngineErrorType) -> &'static str {
    match error_type {
        EngineErrorType::InvalidInput => "invalid_input",
        EngineErrorType::NotFound => "not_found",
        EngineErrorType::LoginRequired => "login_required",
        EngineErrorType::AuthExpired => "auth_expired",
        EngineErrorType::Forbidden => "forbidden",
        EngineErrorType::AlreadyExists => "already_exists",
        EngineErrorType::FailedPrecondition => "failed_precondition",
        EngineErrorType::Conflict => "conflict",
        EngineErrorType::RateLimited => "rate_limited",
        EngineErrorType::ServiceUnavailable => "service_unavailable",
        EngineErrorType::BackendFault => "backend_fault",
        EngineErrorType::Transport => "transport",
        EngineErrorType::UnsafeTransport => "unsafe_transport",
        EngineErrorType::MappingDefect => "mapping_defect",
        EngineErrorType::NetworkError => "network_error",
        EngineErrorType::PlayerError => "player_error",
        EngineErrorType::AuthenticationError => "authentication_error",
        EngineErrorType::SessionStorage => "session_storage",
        EngineErrorType::MediaSkipped => "media_skipped",
        EngineErrorType::CommandRejected => "command_rejected",
        EngineErrorType::Unknown => "unknown",
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use async_trait::async_trait;
    use panda_engine_core::{
        Account, AuthPort, AuthSession, AuthSessionEnvelope, AuthState, EngineError,
        EngineErrorType, InMemorySessionStore, SessionCoordinator, SessionStore,
    };

    use crate::engine_handle::{EngineAuthRuntime, build_engine};

    use super::{AuthOperationResult, login_password, register_password, verify_email};

    struct UnreachableAuth;

    #[async_trait]
    impl AuthPort for UnreachableAuth {
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

    #[test]
    fn auth_result_projection_never_exposes_backend_messages() {
        let result = AuthOperationResult::Error(EngineError::new(
            EngineErrorType::AuthenticationError,
            "token=super-secret backend detail",
            false,
        ));

        assert_eq!(result.to_strings(), ["error", "authentication_error", ""]);
    }

    #[test]
    fn production_interactive_auth_rejects_ephemeral_session_storage() {
        let engine = build_engine(0);
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::new());
        let coordinator = Arc::new(SessionCoordinator::new(
            store.clone(),
            Arc::new(UnreachableAuth),
        ));
        *engine.auth_runtime.lock().unwrap() = Some(EngineAuthRuntime {
            coordinator,
            store,
            production: true,
        });

        let result = register_password(&engine, "driver@example.com", "secret");

        assert!(matches!(
            result,
            AuthOperationResult::Error(EngineError {
                error_type: EngineErrorType::SessionStorage,
                ..
            })
        ));
    }

    struct AcceptingAuth;

    #[async_trait]
    impl AuthPort for AcceptingAuth {
        async fn login_password(
            &self,
            _email: &str,
            _password: &str,
            _device_label: &str,
        ) -> Result<AuthSessionEnvelope, EngineError> {
            Ok(session_envelope())
        }

        async fn verify_email(
            &self,
            _verification_token: &str,
            _device_label: &str,
        ) -> Result<AuthSessionEnvelope, EngineError> {
            Ok(session_envelope())
        }

        async fn refresh_session(
            &self,
            _refresh_token: &str,
        ) -> Result<AuthSessionEnvelope, EngineError> {
            unreachable!()
        }

        async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
            Ok(())
        }
    }

    fn session_envelope() -> AuthSessionEnvelope {
        AuthSessionEnvelope::new(
            "access-secret".into(),
            2_000,
            "refresh-secret".into(),
            3_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 500,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "PandaWave".into(),
                created_at_epoch_millis: 1_000,
                last_used_at_epoch_millis: 1_100,
                expires_at_epoch_millis: 4_000,
                current: true,
            },
        )
    }

    #[test]
    fn login_password_publishes_authenticated_snapshot_without_a_tick() {
        let engine = build_engine(0);
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::new());
        let coordinator = Arc::new(SessionCoordinator::new(
            store.clone(),
            Arc::new(AcceptingAuth),
        ));
        *engine.auth_runtime.lock().unwrap() = Some(EngineAuthRuntime {
            coordinator,
            store,
            production: false,
        });

        assert_eq!(engine.engine.snapshot().auth_state, AuthState::Anonymous);

        let result = login_password(&engine, "driver@example.com", "secret", "PandaWave");

        assert!(matches!(result, AuthOperationResult::Authenticated));
        assert!(matches!(
            engine.engine.snapshot().auth_state,
            AuthState::Authenticated { .. }
        ));
    }

    #[test]
    fn verify_email_publishes_authenticated_snapshot_without_a_tick() {
        let engine = build_engine(0);
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::new());
        let coordinator = Arc::new(SessionCoordinator::new(
            store.clone(),
            Arc::new(AcceptingAuth),
        ));
        *engine.auth_runtime.lock().unwrap() = Some(EngineAuthRuntime {
            coordinator,
            store,
            production: false,
        });

        assert_eq!(engine.engine.snapshot().auth_state, AuthState::Anonymous);

        let result = verify_email(&engine, "opaque-token", "PandaWave");

        assert!(matches!(result, AuthOperationResult::Authenticated));
        assert!(matches!(
            engine.engine.snapshot().auth_state,
            AuthState::Authenticated { .. }
        ));
    }
}
