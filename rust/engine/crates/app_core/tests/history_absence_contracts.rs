use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use async_trait::async_trait;
use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, Engine, EngineCommand, EngineError,
    EngineErrorType, EngineHistoryEntry, EngineHistoryIdentity, EngineHistorySettings,
    EngineHistorySettingsUpdate, EnginePageRequest, EnginePagedResult, EnginePlaybackRecord,
    HistoryPort,
};

struct StaticAuth(AuthState);

impl AuthStateProvider for StaticAuth {
    fn current_auth_state(&self) -> AuthState {
        self.0.clone()
    }
}

fn authenticated(current: bool) -> AuthState {
    AuthState::Authenticated {
        account: Account {
            id: "account-1".into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 1,
        },
        session: AuthSession {
            id: "session-1".into(),
            device_label: "PandaWave".into(),
            created_at_epoch_millis: 1,
            last_used_at_epoch_millis: 1,
            expires_at_epoch_millis: 10_000,
            current,
        },
    }
}

struct AbsentHistoryPort {
    settings_calls: AtomicUsize,
}

impl AbsentHistoryPort {
    fn not_found() -> EngineError {
        EngineError::new(EngineErrorType::NotFound, "absent", false)
    }
}

#[async_trait]
impl HistoryPort for AbsentHistoryPort {
    async fn get_settings(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError> {
        self.settings_calls.fetch_add(1, Ordering::SeqCst);
        Ok(EngineHistorySettings { enabled: true })
    }

    async fn update_settings(
        &self,
        _: &EngineHistoryIdentity,
        _: bool,
    ) -> Result<EngineHistorySettingsUpdate, EngineError> {
        unreachable!()
    }

    async fn record(
        &self,
        _: &EngineHistoryIdentity,
        _: EnginePlaybackRecord,
    ) -> Result<bool, EngineError> {
        unreachable!()
    }

    async fn list(
        &self,
        _: &EngineHistoryIdentity,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError> {
        Err(Self::not_found())
    }

    async fn delete_entry(&self, _: &EngineHistoryIdentity, _: &str) -> Result<(), EngineError> {
        Err(Self::not_found())
    }

    async fn clear(&self, _: &EngineHistoryIdentity) -> Result<u64, EngineError> {
        Err(Self::not_found())
    }
}

fn engine(auth: AuthState, port: Arc<AbsentHistoryPort>) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(Arc::new(StaticAuth(auth)));
    engine.set_history_port(port);
    engine
}

#[tokio::test]
async fn not_found_list_delete_and_clear_are_indistinguishable_from_absence() {
    let port = Arc::new(AbsentHistoryPort {
        settings_calls: AtomicUsize::new(0),
    });
    let mut engine = engine(authenticated(true), port);

    for command in [
        EngineCommand::list_history(25),
        EngineCommand::delete_history_entry("history-1"),
        EngineCommand::clear_history(),
    ] {
        let outcome = engine.dispatch(command, 1).await;
        assert!(outcome.snapshot.last_error.is_none());
        assert!(outcome.snapshot.history_entries.is_empty());
    }
}

#[tokio::test]
async fn non_current_session_cannot_call_history_service() {
    let port = Arc::new(AbsentHistoryPort {
        settings_calls: AtomicUsize::new(0),
    });
    let mut engine = engine(authenticated(false), port.clone());

    let outcome = engine
        .dispatch(EngineCommand::load_history_settings(), 1)
        .await;

    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired
    );
    assert_eq!(port.settings_calls.load(Ordering::SeqCst), 0);
}
