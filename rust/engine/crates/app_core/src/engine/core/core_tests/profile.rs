use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use serde_json::{Map, Value, json};
use tokio::sync::Notify;

use super::*;
use crate::{
    Account, AuthSession, AuthState, AuthStateProvider, EngineError, EngineErrorType,
    EngineProfile, EngineProfileUpdate, ProfilePort,
};

#[derive(Clone)]
struct FixedAuthState(AuthState);

impl AuthStateProvider for FixedAuthState {
    fn current_auth_state(&self) -> AuthState {
        self.0.clone()
    }
}

struct RecordingProfilePort {
    profile: Mutex<EngineProfile>,
    preferences: Mutex<Map<String, Value>>,
    updates: Mutex<Vec<Map<String, Value>>>,
    deletes: Mutex<usize>,
    missing: bool,
}

impl RecordingProfilePort {
    fn new(external_user_id: &str) -> Self {
        Self {
            profile: Mutex::new(EngineProfile {
                id: "profile-1".into(),
                external_user_id: external_user_id.into(),
                display_name: None,
                created_at_epoch_millis: Some(100),
                updated_at_epoch_millis: Some(200),
            }),
            preferences: Mutex::new(Map::from_iter([
                ("theme".into(), json!("forest_tech_dark")),
                ("future_key".into(), json!({"nested": 7})),
            ])),
            updates: Mutex::new(Vec::new()),
            deletes: Mutex::new(0),
            missing: false,
        }
    }

    fn missing(external_user_id: &str) -> Self {
        Self {
            missing: true,
            ..Self::new(external_user_id)
        }
    }
}

#[async_trait]
impl ProfilePort for RecordingProfilePort {
    async fn upsert(&self, display_name: Option<&str>) -> Result<EngineProfile, EngineError> {
        let mut profile = self.profile.lock().unwrap();
        profile.display_name = display_name.map(str::to_owned);
        Ok(profile.clone())
    }

    async fn get(&self) -> Result<EngineProfile, EngineError> {
        if self.missing {
            return Err(EngineError::new(
                EngineErrorType::NotFound,
                "profile not found",
                false,
            ));
        }
        Ok(self.profile.lock().unwrap().clone())
    }

    async fn update(&self, update: EngineProfileUpdate) -> Result<EngineProfile, EngineError> {
        let mut profile = self.profile.lock().unwrap();
        *profile = update.apply_to(&profile);
        Ok(profile.clone())
    }

    async fn delete(&self) -> Result<(), EngineError> {
        *self.deletes.lock().unwrap() += 1;
        Ok(())
    }

    async fn get_preferences(&self) -> Result<Map<String, Value>, EngineError> {
        if self.missing {
            return Err(EngineError::new(
                EngineErrorType::NotFound,
                "profile not found",
                false,
            ));
        }
        Ok(self.preferences.lock().unwrap().clone())
    }

    async fn update_preferences(
        &self,
        values: Map<String, Value>,
    ) -> Result<Map<String, Value>, EngineError> {
        self.updates.lock().unwrap().push(values.clone());
        *self.preferences.lock().unwrap() = values.clone();
        Ok(values)
    }
}

fn authenticated_engine(profile_port: Arc<RecordingProfilePort>) -> Engine {
    authenticated_engine_with_current_session(profile_port, true)
}

fn authenticated_engine_with_current_session(
    profile_port: Arc<RecordingProfilePort>,
    current: bool,
) -> Engine {
    let auth = AuthState::Authenticated {
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
            last_used_at_epoch_millis: 2,
            expires_at_epoch_millis: 10_000,
            current,
        },
    };
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(Arc::new(FixedAuthState(auth)));
    engine.set_profile_port(profile_port);
    engine
}

#[tokio::test]
async fn profile_not_found_projects_authorized_absence_for_get_and_preferences_load() {
    let port = Arc::new(RecordingProfilePort::missing("account-1"));
    let mut engine = authenticated_engine(port);

    let get = engine.dispatch(EngineCommand::get_profile(), 1).await;
    assert!(get.snapshot.profile.is_none());
    assert!(get.snapshot.last_error.is_none());

    let preferences = engine
        .dispatch(EngineCommand::load_profile_preferences(), 2)
        .await;
    assert!(preferences.snapshot.profile.is_none());
    assert!(preferences.snapshot.profile_preferences.is_empty());
    assert!(preferences.snapshot.last_error.is_none());
}

#[tokio::test]
async fn profile_commands_project_upsert_get_update_and_delete_results() {
    let port = Arc::new(RecordingProfilePort::new("account-1"));
    let mut engine = authenticated_engine(port.clone());

    engine
        .dispatch(EngineCommand::upsert_profile(Some("Driver".into())), 1)
        .await;
    assert_eq!(
        engine
            .snapshot()
            .profile
            .as_ref()
            .unwrap()
            .display_name
            .as_deref(),
        Some("Driver")
    );

    engine.dispatch(EngineCommand::get_profile(), 2).await;
    assert_eq!(engine.snapshot().profile.as_ref().unwrap().id, "profile-1");

    engine
        .dispatch(
            EngineCommand::update_profile(EngineProfileUpdate::display_name(Some(String::new()))),
            3,
        )
        .await;
    assert_eq!(
        engine
            .snapshot()
            .profile
            .as_ref()
            .unwrap()
            .display_name
            .as_deref(),
        Some("")
    );

    engine.dispatch(EngineCommand::delete_profile(), 4).await;
    assert!(engine.snapshot().profile.is_none());
    assert!(engine.snapshot().profile_preferences.is_empty());
    assert_eq!(*port.deletes.lock().unwrap(), 1);
}

#[tokio::test]
async fn preference_update_sends_full_merged_document_to_profile_port() {
    let port = Arc::new(RecordingProfilePort::new("account-1"));
    let mut engine = authenticated_engine(port.clone());

    engine
        .dispatch(
            EngineCommand::update_profile_preferences(Map::from_iter([(
                "theme".into(),
                json!("bamboo_grove_light"),
            )])),
            1,
        )
        .await;

    let updates = port.updates.lock().unwrap();
    assert_eq!(updates.len(), 1);
    let sent = updates[0].clone();
    assert_eq!(sent["theme"], "bamboo_grove_light");
    assert_eq!(sent["future_key"]["nested"], 7);
    assert_eq!(engine.snapshot().profile_preferences, sent);
    assert_eq!(
        engine.snapshot().theme_preference.theme,
        crate::ThemePreference::BambooGroveLight
    );
}

#[tokio::test]
async fn profile_projection_rejects_profile_owned_by_another_account() {
    let port = Arc::new(RecordingProfilePort::new("account-2"));
    let mut engine = authenticated_engine(port);

    let outcome = engine.dispatch(EngineCommand::get_profile(), 1).await;

    assert!(outcome.snapshot.profile.is_none());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        crate::EngineErrorType::Forbidden
    );
}

#[tokio::test]
async fn profile_commands_reject_a_non_current_authenticated_session() {
    let port = Arc::new(RecordingProfilePort::new("account-1"));
    let mut engine = authenticated_engine_with_current_session(port, false);

    let outcome = engine.dispatch(EngineCommand::get_profile(), 1).await;

    assert!(outcome.snapshot.profile.is_none());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        crate::EngineErrorType::LoginRequired
    );
}
struct MutableProfileAuthState {
    state: Mutex<AuthState>,
}

impl MutableProfileAuthState {
    fn new(account_id: &str, session_id: &str) -> Self {
        Self {
            state: Mutex::new(profile_auth_state(account_id, session_id)),
        }
    }

    fn set_identity(&self, account_id: &str, session_id: &str) {
        *self.state.lock().unwrap() = profile_auth_state(account_id, session_id);
    }
}

impl AuthStateProvider for MutableProfileAuthState {
    fn current_auth_state(&self) -> AuthState {
        self.state.lock().unwrap().clone()
    }
}

fn profile_auth_state(account_id: &str, session_id: &str) -> AuthState {
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
            last_used_at_epoch_millis: 2,
            expires_at_epoch_millis: 10_000,
            current: true,
        },
    }
}

async fn engine_with_projected_profile(
    auth: Arc<MutableProfileAuthState>,
    port: Arc<RecordingProfilePort>,
) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_profile_port(port);
    engine
        .dispatch(EngineCommand::load_profile_preferences(), 1)
        .await;
    assert!(engine.snapshot().profile.is_some());
    assert!(!engine.snapshot().profile_preferences.is_empty());
    engine
}

#[tokio::test]
async fn account_switch_hides_profile_projection_and_mutable_sync_clears_it_durably() {
    let auth = Arc::new(MutableProfileAuthState::new("account-1", "session-1"));
    let port = Arc::new(RecordingProfilePort::new("account-1"));
    let mut engine = engine_with_projected_profile(auth.clone(), port).await;

    auth.set_identity("account-2", "session-2");

    let switched = engine.snapshot();
    assert!(switched.profile.is_none());
    assert!(switched.profile_preferences.is_empty());
    assert_ne!(
        switched.theme_preference.source,
        crate::PreferenceSource::RemoteProfile
    );

    engine.dispatch(EngineCommand::pause(), 2).await;
    auth.set_identity("account-1", "session-1");
    assert!(engine.snapshot().profile.is_none());
    assert!(engine.snapshot().profile_preferences.is_empty());
}

#[tokio::test]
async fn session_replacement_hides_profile_projection_and_mutable_sync_clears_it_durably() {
    let auth = Arc::new(MutableProfileAuthState::new("account-1", "session-1"));
    let port = Arc::new(RecordingProfilePort::new("account-1"));
    let mut engine = engine_with_projected_profile(auth.clone(), port).await;

    auth.set_identity("account-1", "session-2");

    let replaced = engine.snapshot();
    assert!(replaced.profile.is_none());
    assert!(replaced.profile_preferences.is_empty());

    engine.dispatch(EngineCommand::pause(), 2).await;
    auth.set_identity("account-1", "session-1");
    assert!(engine.snapshot().profile.is_none());
    assert!(engine.snapshot().profile_preferences.is_empty());
}
#[derive(Clone, Copy, Eq, PartialEq)]
enum BlockingProfileCall {
    Get,
    GetPreferences,
}

struct BlockingProfilePort {
    inner: RecordingProfilePort,
    block_at: BlockingProfileCall,
    started: Notify,
    release: Notify,
}

impl BlockingProfilePort {
    fn new(block_at: BlockingProfileCall) -> Self {
        Self {
            inner: RecordingProfilePort::new("account-1"),
            block_at,
            started: Notify::new(),
            release: Notify::new(),
        }
    }

    async fn block_if(&self, call: BlockingProfileCall) {
        if self.block_at == call {
            self.started.notify_one();
            self.release.notified().await;
        }
    }
}

#[async_trait]
impl ProfilePort for BlockingProfilePort {
    async fn upsert(&self, display_name: Option<&str>) -> Result<EngineProfile, EngineError> {
        self.inner.upsert(display_name).await
    }

    async fn get(&self) -> Result<EngineProfile, EngineError> {
        self.block_if(BlockingProfileCall::Get).await;
        self.inner.get().await
    }

    async fn update(&self, update: EngineProfileUpdate) -> Result<EngineProfile, EngineError> {
        self.inner.update(update).await
    }

    async fn delete(&self) -> Result<(), EngineError> {
        self.inner.delete().await
    }

    async fn get_preferences(&self) -> Result<Map<String, Value>, EngineError> {
        self.block_if(BlockingProfileCall::GetPreferences).await;
        self.inner.get_preferences().await
    }

    async fn update_preferences(
        &self,
        values: Map<String, Value>,
    ) -> Result<Map<String, Value>, EngineError> {
        self.inner.update_preferences(values).await
    }
}

fn engine_with_blocking_profile_port(
    auth: Arc<MutableProfileAuthState>,
    port: Arc<BlockingProfilePort>,
) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_profile_port(port);
    engine
}

#[tokio::test]
async fn in_flight_profile_get_does_not_publish_after_auth_identity_changes() {
    let auth = Arc::new(MutableProfileAuthState::new("account-1", "session-1"));
    let port = Arc::new(BlockingProfilePort::new(BlockingProfileCall::Get));
    let mut engine = engine_with_blocking_profile_port(auth.clone(), port.clone());

    let dispatch = engine.dispatch(EngineCommand::get_profile(), 1);
    let change_auth = async {
        port.started.notified().await;
        auth.set_identity("account-2", "session-2");
        port.release.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, change_auth);

    assert!(outcome.snapshot.profile.is_none());
    assert!(outcome.snapshot.profile_preferences.is_empty());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired
    );
    assert!(engine.snapshot().profile.is_none());
    assert!(engine.snapshot().profile_preferences.is_empty());
}

#[tokio::test]
async fn in_flight_preferences_load_does_not_publish_after_auth_identity_changes() {
    let auth = Arc::new(MutableProfileAuthState::new("account-1", "session-1"));
    let port = Arc::new(BlockingProfilePort::new(
        BlockingProfileCall::GetPreferences,
    ));
    let mut engine = engine_with_blocking_profile_port(auth.clone(), port.clone());

    let dispatch = engine.dispatch(EngineCommand::load_profile_preferences(), 1);
    let change_auth = async {
        port.started.notified().await;
        auth.set_identity("account-1", "session-2");
        port.release.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, change_auth);

    assert!(outcome.snapshot.profile.is_none());
    assert!(outcome.snapshot.profile_preferences.is_empty());
    assert_ne!(
        outcome.snapshot.theme_preference.source,
        crate::PreferenceSource::RemoteProfile
    );
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired
    );
    assert!(engine.snapshot().profile.is_none());
    assert!(engine.snapshot().profile_preferences.is_empty());
}
