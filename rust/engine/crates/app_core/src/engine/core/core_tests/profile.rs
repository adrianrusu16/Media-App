use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use serde_json::{Map, Value, json};

use super::*;
use crate::{
    Account, AuthSession, AuthState, AuthStateProvider, EngineError, EngineProfile,
    EngineProfileUpdate, ProfilePort,
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
