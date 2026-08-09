use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, Engine, EngineCommand, EngineHistoryEntry,
    EngineHistoryIdentity, EngineHistorySettings, EngineHistorySettingsUpdate, EnginePageRequest,
    EnginePageToken, EnginePagedResult, EnginePlatformEvent, EnginePlaybackRecord, EngineTrack,
    HistoryPort, normalize_completion_ratio,
};

#[derive(Clone)]
struct MutableAuth(Arc<Mutex<AuthState>>);

impl MutableAuth {
    fn authenticated(account_id: &str, session_id: &str) -> Self {
        Self(Arc::new(Mutex::new(auth_state(account_id, session_id))))
    }

    fn replace(&self, account_id: &str, session_id: &str) {
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

struct RecordingHistoryPort {
    enabled: Mutex<bool>,
    records: Mutex<Vec<EnginePlaybackRecord>>,
    entries: Mutex<Vec<EngineHistoryEntry>>,
    deleted_ids: Mutex<Vec<String>>,
    clear_count: Mutex<u64>,
}

impl RecordingHistoryPort {
    fn new(enabled: bool) -> Self {
        Self {
            enabled: Mutex::new(enabled),
            records: Mutex::new(Vec::new()),
            entries: Mutex::new(vec![EngineHistoryEntry {
                id: "history-1".into(),
                played_at_epoch_millis: Some(1_234),
                duration_millis: 4_000,
                completion_ratio: 0.75,
                track: None,
            }]),
            deleted_ids: Mutex::new(Vec::new()),
            clear_count: Mutex::new(0),
        }
    }
}

#[async_trait]
impl HistoryPort for RecordingHistoryPort {
    async fn get_settings(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, panda_engine_core::EngineError> {
        Ok(EngineHistorySettings {
            enabled: *self.enabled.lock().unwrap(),
        })
    }

    async fn update_settings(
        &self,
        _: &EngineHistoryIdentity,
        enabled: bool,
    ) -> Result<EngineHistorySettingsUpdate, panda_engine_core::EngineError> {
        *self.enabled.lock().unwrap() = enabled;
        let deleted_count = if enabled {
            0
        } else {
            self.entries.lock().unwrap().drain(..).count() as u64
        };
        Ok(EngineHistorySettingsUpdate {
            settings: EngineHistorySettings { enabled },
            deleted_count,
        })
    }

    async fn record(
        &self,
        _: &EngineHistoryIdentity,
        event: EnginePlaybackRecord,
    ) -> Result<bool, panda_engine_core::EngineError> {
        self.records.lock().unwrap().push(event);
        Ok(true)
    }

    async fn list(
        &self,
        _: &EngineHistoryIdentity,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineHistoryEntry>, panda_engine_core::EngineError> {
        Ok(EnginePagedResult {
            items: self.entries.lock().unwrap().clone(),
            next_page_token: Some(EnginePageToken::new("opaque+/=".into()).unwrap()),
        })
    }

    async fn delete_entry(
        &self,
        _: &EngineHistoryIdentity,
        id: &str,
    ) -> Result<(), panda_engine_core::EngineError> {
        self.deleted_ids.lock().unwrap().push(id.into());
        self.entries.lock().unwrap().retain(|entry| entry.id != id);
        Ok(())
    }

    async fn clear(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<u64, panda_engine_core::EngineError> {
        let deleted = self.entries.lock().unwrap().drain(..).count() as u64;
        *self.clear_count.lock().unwrap() += 1;
        Ok(deleted)
    }
}

fn engine(port: Arc<RecordingHistoryPort>, auth: Arc<MutableAuth>) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_history_port(port);
    engine
}

#[test]
fn history_completion_ratio_rejects_non_finite_and_clamps_range() {
    assert_eq!(normalize_completion_ratio(-0.25).unwrap(), 0.0);
    assert_eq!(normalize_completion_ratio(1.5).unwrap(), 1.0);
    assert!(normalize_completion_ratio(f32::NAN).is_err());
    assert!(normalize_completion_ratio(f32::INFINITY).is_err());
}

#[tokio::test]
async fn disabled_history_does_not_record_playback_completion() {
    let port = Arc::new(RecordingHistoryPort::new(false));
    let auth = Arc::new(MutableAuth::authenticated("account-1", "session-1"));
    let mut engine = engine(port.clone(), auth);
    engine
        .dispatch(EngineCommand::load_history_settings(), 1)
        .await;

    engine
        .dispatch_platform_event(
            EnginePlatformEvent::playback_completed("track-1", 1_000, 1.0),
            2,
        )
        .await;

    assert!(port.records.lock().unwrap().is_empty());
}

#[tokio::test]
async fn enabled_history_records_without_settings_route_starting_first() {
    let port = Arc::new(RecordingHistoryPort::new(true));
    let auth = Arc::new(MutableAuth::authenticated("account-1", "session-1"));
    let mut engine = engine(port.clone(), auth);

    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::playback_completed("track-1", 1_000, 0.8),
            1,
        )
        .await;

    assert_eq!(
        outcome.snapshot.history_settings,
        Some(EngineHistorySettings { enabled: true }),
    );
    assert_eq!(port.records.lock().unwrap().len(), 1);
}
#[tokio::test]
async fn enabled_history_records_clamped_completion_through_panda_engine() {
    let port = Arc::new(RecordingHistoryPort::new(true));
    let auth = Arc::new(MutableAuth::authenticated("account-1", "session-1"));
    let mut engine = engine(port.clone(), auth);
    engine
        .dispatch(EngineCommand::load_history_settings(), 1)
        .await;

    engine
        .dispatch_platform_event(
            EnginePlatformEvent::playback_completed("track-1", 1_000, 1.4),
            2,
        )
        .await;

    assert_eq!(
        port.records.lock().unwrap().as_slice(),
        [EnginePlaybackRecord {
            track_id: "track-1".into(),
            duration_millis: 1_000,
            completion_ratio: 1.0,
        }]
    );
}

#[tokio::test]
async fn disabling_history_purges_projected_pages_and_accepts_deleted_count() {
    let port = Arc::new(RecordingHistoryPort::new(true));
    let auth = Arc::new(MutableAuth::authenticated("account-1", "session-1"));
    let mut engine = engine(port, auth);
    engine.dispatch(EngineCommand::list_history(25), 1).await;
    assert_eq!(engine.snapshot().history_entries.len(), 1);

    let outcome = engine
        .dispatch(EngineCommand::update_history_settings(false), 2)
        .await;

    assert_eq!(
        outcome.snapshot.history_settings,
        Some(EngineHistorySettings { enabled: false })
    );
    assert_eq!(outcome.snapshot.history_deleted_count, 1);
    assert!(outcome.snapshot.history_entries.is_empty());
}

#[tokio::test]
async fn history_delete_and_clear_update_only_the_current_identity_projection() {
    let port = Arc::new(RecordingHistoryPort::new(true));
    let auth = Arc::new(MutableAuth::authenticated("account-1", "session-1"));
    let mut engine = engine(port.clone(), auth.clone());
    engine.dispatch(EngineCommand::list_history(25), 1).await;

    engine
        .dispatch(EngineCommand::delete_history_entry("history-1"), 2)
        .await;
    assert!(engine.snapshot().history_entries.is_empty());

    auth.replace("account-2", "session-2");
    let switched = engine.dispatch(EngineCommand::clear_history(), 3).await;
    assert!(switched.snapshot.history_entries.is_empty());
    assert_eq!(port.clear_count.lock().unwrap().to_owned(), 1);
}

#[allow(dead_code)]
fn optional_track_stays_optional(_: Option<EngineTrack>) {}
