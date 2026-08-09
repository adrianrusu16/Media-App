use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, Engine, EngineCommand, EngineError,
    EngineErrorType, EngineHistoryEntry, EngineHistoryIdentity, EngineHistorySettings,
    EngineHistorySettingsUpdate, EnginePageRequest, EnginePagedResult, EnginePlatformEvent,
    EnginePlaybackRecord, HistoryPort,
};
use tokio::sync::Notify;

struct MutableAuth(Mutex<AuthState>);

impl MutableAuth {
    fn set_identity(&self, account_id: &str, session_id: &str) {
        *self.0.lock().unwrap() = auth_state(account_id, session_id);
    }
}

impl AuthStateProvider for MutableAuth {
    fn current_auth_state(&self) -> AuthState {
        self.0.lock().unwrap().clone()
    }
}

fn auth_state(account_id: &str, session_id: &str) -> AuthState {
    AuthState::Authenticated {
        account: Account {
            id: account_id.into(),
            primary_email: format!("{account_id}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 1,
        },
        session: AuthSession {
            id: session_id.into(),
            device_label: "PandaWave".into(),
            created_at_epoch_millis: 1,
            last_used_at_epoch_millis: 1,
            expires_at_epoch_millis: 10_000,
            current: true,
        },
    }
}

struct BlockingHistoryPort {
    started: Notify,
    release: Notify,
}

#[async_trait]
impl HistoryPort for BlockingHistoryPort {
    async fn get_settings(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError> {
        self.started.notify_one();
        self.release.notified().await;
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
        unreachable!()
    }

    async fn delete_entry(&self, _: &EngineHistoryIdentity, _: &str) -> Result<(), EngineError> {
        unreachable!()
    }

    async fn clear(&self, _: &EngineHistoryIdentity) -> Result<u64, EngineError> {
        unreachable!()
    }
}

#[tokio::test]
async fn in_flight_history_settings_do_not_publish_after_identity_changes() {
    let auth = Arc::new(MutableAuth(Mutex::new(auth_state(
        "account-1",
        "session-1",
    ))));
    let port = Arc::new(BlockingHistoryPort {
        started: Notify::new(),
        release: Notify::new(),
    });
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth.clone());
    engine.set_history_port(port.clone());

    let dispatch = engine.dispatch(EngineCommand::load_history_settings(), 1);
    let change_identity = async {
        port.started.notified().await;
        auth.set_identity("account-2", "session-2");
        port.release.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, change_identity);

    assert!(outcome.snapshot.history_settings.is_none());
    assert!(outcome.snapshot.history_entries.is_empty());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired,
    );
}
#[tokio::test]
async fn in_flight_history_lazy_consent_does_not_record_after_identity_changes() {
    let auth = Arc::new(MutableAuth(Mutex::new(auth_state(
        "account-1",
        "session-1",
    ))));
    let port = Arc::new(BlockingHistoryPort {
        started: Notify::new(),
        release: Notify::new(),
    });
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth.clone());
    engine.set_history_port(port.clone());

    let dispatch = engine.dispatch_platform_event(
        EnginePlatformEvent::playback_completed("track-1", 1_000, 0.8),
        1,
    );
    let change_identity = async {
        port.started.notified().await;
        auth.set_identity("account-2", "session-2");
        port.release.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, change_identity);

    assert!(outcome.snapshot.history_settings.is_none());
    assert!(outcome.snapshot.history_entries.is_empty());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired,
    );
}
struct OwnerAwareBlockingHistoryPort {
    auth: Arc<MutableAuth>,
    record_started: Notify,
    release_record: Notify,
    recorded_owners: Mutex<Vec<String>>,
}

#[async_trait]
impl HistoryPort for OwnerAwareBlockingHistoryPort {
    async fn get_settings(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError> {
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
        expected: &EngineHistoryIdentity,
        _: EnginePlaybackRecord,
    ) -> Result<bool, EngineError> {
        self.record_started.notify_one();
        self.release_record.notified().await;
        let current = self.auth.current_auth_state();
        let still_expected = matches!(
            current,
            AuthState::Authenticated { account, session }
                if account.id == expected.account_id && session.id == expected.session_id
        );
        if !still_expected {
            return Err(EngineError::new(
                EngineErrorType::LoginRequired,
                "history owner changed before authorization",
                false,
            ));
        }
        self.recorded_owners
            .lock()
            .unwrap()
            .push(expected.account_id.clone());
        Ok(true)
    }

    async fn list(
        &self,
        _: &EngineHistoryIdentity,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError> {
        unreachable!()
    }

    async fn delete_entry(&self, _: &EngineHistoryIdentity, _: &str) -> Result<(), EngineError> {
        unreachable!()
    }

    async fn clear(&self, _: &EngineHistoryIdentity) -> Result<u64, EngineError> {
        unreachable!()
    }
}

#[tokio::test]
async fn in_flight_history_record_cannot_authorize_as_a_replacement_identity() {
    let auth = Arc::new(MutableAuth(Mutex::new(auth_state(
        "account-1",
        "session-1",
    ))));
    let port = Arc::new(OwnerAwareBlockingHistoryPort {
        auth: auth.clone(),
        record_started: Notify::new(),
        release_record: Notify::new(),
        recorded_owners: Mutex::new(Vec::new()),
    });
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth.clone());
    engine.set_history_port(port.clone());

    let dispatch = engine.dispatch_platform_event(
        EnginePlatformEvent::playback_completed("track-1", 1_000, 0.8),
        1,
    );
    let replace_identity = async {
        port.record_started.notified().await;
        auth.set_identity("account-2", "session-2");
        port.release_record.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, replace_identity);

    assert!(port.recorded_owners.lock().unwrap().is_empty());
    assert!(outcome.snapshot.history_settings.is_none());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired,
    );
}
