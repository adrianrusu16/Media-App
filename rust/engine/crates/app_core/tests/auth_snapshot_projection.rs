use std::sync::{Arc, Mutex};

use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, ConcurrentEngine, Engine, EngineCommand,
    EngineCommandType, EngineEvent, EngineObserver, EngineSnapshot, MiddlewarePipeline,
    TelemetryMiddleware,
};

#[derive(Clone)]
struct MutableAuthState(Arc<Mutex<AuthState>>);

impl AuthStateProvider for MutableAuthState {
    fn current_auth_state(&self) -> AuthState {
        self.0.lock().unwrap().clone()
    }
}

fn authenticated() -> AuthState {
    AuthState::Authenticated {
        account: Account {
            id: "account-1".into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 10,
        },
        session: AuthSession {
            id: "session-1".into(),
            device_label: "PandaEmulatorNoStore".into(),
            created_at_epoch_millis: 20,
            last_used_at_epoch_millis: 30,
            expires_at_epoch_millis: 40,
            current: true,
        },
    }
}

#[test]
fn concurrent_snapshot_reads_current_auth_state_instead_of_a_stale_copy() {
    let state = Arc::new(Mutex::new(AuthState::Anonymous));
    let provider = MutableAuthState(state.clone());
    let mut engine = Engine::new(1);
    engine.set_auth_state_provider(Arc::new(provider));
    let engine = ConcurrentEngine::new(engine);

    assert_eq!(engine.snapshot().auth_state, AuthState::Anonymous);

    *state.lock().unwrap() = authenticated();
    assert_eq!(engine.snapshot().auth_state, authenticated());

    *state.lock().unwrap() = AuthState::LoginRequired;
    assert_eq!(engine.snapshot().auth_state, AuthState::LoginRequired);
}

#[tokio::test]
async fn dispatch_result_projects_the_latest_auth_state() {
    let state = Arc::new(Mutex::new(AuthState::Anonymous));
    let mut engine = Engine::new(1);
    engine.set_auth_state_provider(Arc::new(MutableAuthState(state.clone())));
    let engine = ConcurrentEngine::new(engine);
    *state.lock().unwrap() = AuthState::LoginRequired;

    let result = engine
        .dispatch(EngineCommand::new(EngineCommandType::Bootstrap, None), 2)
        .await;

    assert_eq!(result.snapshot.auth_state, AuthState::LoginRequired);
}

struct RecordingObserver(Arc<Mutex<Vec<AuthState>>>);

impl EngineObserver for RecordingObserver {
    fn on_state_changed(&self, snapshot: &EngineSnapshot) {
        self.0.lock().unwrap().push(snapshot.auth_state.clone());
    }

    fn on_event_emitted(&self, _event: &EngineEvent) {}
}

#[tokio::test]
async fn observer_receives_the_live_auth_projection() {
    let state = Arc::new(Mutex::new(AuthState::LoginRequired));
    let observed = Arc::new(Mutex::new(Vec::new()));
    let mut engine = Engine::new(1);
    engine.set_auth_state_provider(Arc::new(MutableAuthState(state)));
    let bus = engine.event_bus();
    bus.subscribe(Box::new(RecordingObserver(observed.clone())));
    let mut middleware = MiddlewarePipeline::new();
    middleware.add(Box::new(TelemetryMiddleware::new(bus)));
    engine.set_middleware(middleware);

    engine
        .dispatch(EngineCommand::new(EngineCommandType::Bootstrap, None), 2)
        .await;

    assert_eq!(
        observed.lock().unwrap().as_slice(),
        &[AuthState::LoginRequired]
    );
}

#[test]
fn serialized_snapshot_auth_state_contains_no_credentials() {
    let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
    snapshot.auth_state = authenticated();

    let json = serde_json::to_string(&snapshot).unwrap();

    assert!(json.contains("driver@example.com"));
    for forbidden in [
        "access_token",
        "refresh_token",
        "SessionEnvelope",
        "canopy.v1",
    ] {
        assert!(
            !json.contains(forbidden),
            "leaked forbidden term: {forbidden}"
        );
    }
}
