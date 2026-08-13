use super::*;
use crate::model::discovery::MockDiscoveryPort;
use crate::{
    Account, AuthSession, AuthState, AuthStateProvider, EngineArtist, EnginePageRequest,
    EnginePageToken, EnginePagedResult, EngineTrack,
};
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicUsize, Ordering},
};

struct BusyObserver {
    states: Arc<Mutex<Vec<bool>>>,
}

impl crate::EngineObserver for BusyObserver {
    fn on_state_changed(&self, snapshot: &crate::EngineSnapshot) {
        self.states.lock().unwrap().push(snapshot.is_busy);
    }

    fn on_event_emitted(&self, _event: &crate::EngineEvent) {}
}

struct MutableAuthState {
    state: Mutex<AuthState>,
}

impl MutableAuthState {
    fn authenticated(account_id: &str) -> Self {
        Self {
            state: Mutex::new(AuthState::Authenticated {
                account: Account {
                    id: account_id.into(),
                    primary_email: format!("{account_id}@example.com"),
                    status: "active".into(),
                    created_at_epoch_millis: 1,
                },
                session: AuthSession {
                    id: "session-1".into(),
                    device_label: "PandaWave".into(),
                    created_at_epoch_millis: 1,
                    last_used_at_epoch_millis: 1,
                    expires_at_epoch_millis: 10_000,
                    current: true,
                },
            }),
        }
    }

    fn set(&self, state: AuthState) {
        *self.state.lock().unwrap() = state;
    }

    fn authenticated_state(account_id: &str, session_id: &str, current: bool) -> AuthState {
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
                current,
            },
        }
    }
}

impl AuthStateProvider for MutableAuthState {
    fn current_auth_state(&self) -> AuthState {
        self.state.lock().unwrap().clone()
    }
}

struct CountingAuthState {
    state: AuthState,
    reads: AtomicUsize,
}

impl AuthStateProvider for CountingAuthState {
    fn current_auth_state(&self) -> AuthState {
        self.reads.fetch_add(1, Ordering::SeqCst);
        self.state.clone()
    }
}

fn track(id: &str) -> EngineTrack {
    EngineTrack {
        id: id.into(),
        title: format!("Track {id}"),
        artist: EngineArtist {
            id: "artist-1".into(),
            name: "An Artist".into(),
        },
        album: None,
        duration_millis: 42,
        explicit: false,
        artwork_id: None,
        genres: Vec::new(),
    }
}

#[tokio::test]
async fn discovery_continuation_reuses_stored_identity_and_appends_projection() {
    let exclusions = vec!["played-1".to_owned(), "played-2".to_owned()];
    let mut discovery = MockDiscoveryPort::new();
    discovery
        .expect_get_feed()
        .withf(|identity, excluded, page| {
            identity.account_id == "account-1"
                && identity.session_id == "session-1"
                && excluded == ["played-1", "played-2"]
                && page.page_size == 1
                && page.page_token.is_none()
        })
        .times(1)
        .returning(|_, _, _| {
            Ok(EnginePagedResult {
                items: vec![track("discovery-1")],
                next_page_token: Some(EnginePageToken::new("opaque+/=".into()).unwrap()),
            })
        });
    discovery
        .expect_get_feed()
        .withf(|identity, excluded, page| {
            identity.account_id == "account-1"
                && identity.session_id == "session-1"
                && excluded == ["played-1", "played-2"]
                && page.page_size == 1
                && page
                    .page_token
                    .as_ref()
                    .is_some_and(|token| token.as_str() == "opaque+/=")
        })
        .times(1)
        .returning(|_, _, _| {
            Ok(EnginePagedResult {
                items: vec![track("discovery-2")],
                next_page_token: None,
            })
        });

    let auth = Arc::new(MutableAuthState::authenticated("account-1"));
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_discovery_port(Arc::new(discovery));
    let observed_busy_states = Arc::new(Mutex::new(Vec::new()));
    engine.event_bus().subscribe(Box::new(BusyObserver {
        states: observed_busy_states.clone(),
    }));

    let first = engine
        .dispatch(
            EngineCommand::load_discovery_feed(
                exclusions,
                EnginePageRequest {
                    page_size: 1,
                    page_token: None,
                },
            ),
            1,
        )
        .await;

    assert_eq!(first.snapshot.discovery_results[0].id, "discovery-1");
    assert_eq!(
        first
            .snapshot
            .discovery_next_page_token
            .as_ref()
            .map(EnginePageToken::as_str),
        Some("opaque+/=")
    );
    assert_eq!(
        first.event.message.as_deref(),
        Some(EngineCommandType::LOAD_DISCOVERY_FEED_WIRE)
    );
    assert!(!first.snapshot.is_busy);
    assert!(first.snapshot.last_error.is_none());
    assert!(observed_busy_states.lock().unwrap().contains(&true));

    let continued = engine
        .dispatch(EngineCommand::load_next_discovery_page(), 2)
        .await;

    assert_eq!(
        continued
            .snapshot
            .discovery_results
            .iter()
            .map(|item| item.id.as_str())
            .collect::<Vec<_>>(),
        ["discovery-1", "discovery-2"]
    );
    assert_eq!(
        continued.event.message.as_deref(),
        Some(EngineCommandType::LOAD_NEXT_DISCOVERY_PAGE_WIRE)
    );
    assert!(!continued.snapshot.is_busy);
    assert!(continued.snapshot.last_error.is_none());
    assert!(continued.snapshot.discovery_next_page_token.is_none());
}

#[tokio::test]
async fn auth_identity_change_hides_results_and_blocks_stale_continuation() {
    let mut discovery = MockDiscoveryPort::new();
    discovery.expect_get_feed().times(1).returning(|_, _, _| {
        Ok(EnginePagedResult {
            items: vec![track("account-1-track")],
            next_page_token: Some(EnginePageToken::new("account-1-next".into()).unwrap()),
        })
    });

    let auth = Arc::new(MutableAuthState::authenticated("account-1"));
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth.clone());
    engine.set_discovery_port(Arc::new(discovery));
    engine
        .dispatch(
            EngineCommand::load_discovery_feed(Vec::new(), EnginePageRequest::default()),
            1,
        )
        .await;

    auth.set(AuthState::Anonymous);

    assert!(engine.snapshot().discovery_results.is_empty());
    let continuation = engine
        .dispatch(EngineCommand::load_next_discovery_page(), 2)
        .await;
    assert!(continuation.snapshot.discovery_results.is_empty());
    assert_eq!(
        continuation.snapshot.last_error.unwrap().error_type,
        crate::EngineErrorType::LoginRequired
    );
}

#[tokio::test]
async fn discovery_loading_and_final_snapshots_resample_auth_after_await() {
    let mut discovery = MockDiscoveryPort::new();
    discovery.expect_get_feed().times(1).returning(|_, _, _| {
        Ok(EnginePagedResult {
            items: vec![track("discovery-1")],
            next_page_token: None,
        })
    });
    let auth = Arc::new(CountingAuthState {
        state: MutableAuthState::authenticated("account-1").current_auth_state(),
        reads: AtomicUsize::new(0),
    });
    let observed_busy_states = Arc::new(Mutex::new(Vec::new()));
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth.clone());
    engine.set_discovery_port(Arc::new(discovery));
    engine.event_bus().subscribe(Box::new(BusyObserver {
        states: observed_busy_states.clone(),
    }));

    let outcome = engine
        .dispatch(
            EngineCommand::load_discovery_feed(Vec::new(), EnginePageRequest::default()),
            1,
        )
        .await;

    assert_eq!(auth.reads.load(Ordering::SeqCst), 2);
    assert!(observed_busy_states.lock().unwrap().contains(&true));
    assert_eq!(outcome.snapshot.discovery_results[0].id, "discovery-1");
}

#[derive(Clone, Copy, Debug)]
enum IdentityTransition {
    Account,
    Session,
    NotCurrent,
    Logout,
}

fn transitioned_state(transition: IdentityTransition) -> AuthState {
    match transition {
        IdentityTransition::Account => {
            MutableAuthState::authenticated_state("account-2", "session-1", true)
        }
        IdentityTransition::Session => {
            MutableAuthState::authenticated_state("account-1", "session-2", true)
        }
        IdentityTransition::NotCurrent => {
            MutableAuthState::authenticated_state("account-1", "session-1", false)
        }
        IdentityTransition::Logout => AuthState::Anonymous,
    }
}

#[tokio::test]
async fn discovery_initial_result_is_rejected_after_every_identity_transition() {
    for transition in [
        IdentityTransition::Account,
        IdentityTransition::Session,
        IdentityTransition::NotCurrent,
        IdentityTransition::Logout,
    ] {
        let auth = Arc::new(MutableAuthState::authenticated("account-1"));
        let transition_auth = auth.clone();
        let mut discovery = MockDiscoveryPort::new();
        discovery
            .expect_get_feed()
            .withf(|identity, _, _| {
                identity.account_id == "account-1" && identity.session_id == "session-1"
            })
            .times(1)
            .returning(move |_, _, _| {
                transition_auth.set(transitioned_state(transition));
                Ok(EnginePagedResult {
                    items: vec![track("old-owner-track")],
                    next_page_token: Some(EnginePageToken::new("old-owner-next".into()).unwrap()),
                })
            });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth);
        engine.set_discovery_port(Arc::new(discovery));

        let outcome = engine
            .dispatch(
                EngineCommand::load_discovery_feed(Vec::new(), EnginePageRequest::default()),
                1,
            )
            .await;

        assert_eq!(
            outcome
                .snapshot
                .last_error
                .as_ref()
                .map(|error| error.error_type.clone()),
            Some(crate::EngineErrorType::LoginRequired),
            "transition {transition:?}"
        );
        assert!(
            outcome.snapshot.discovery_results.is_empty(),
            "transition {transition:?}"
        );
        assert!(
            outcome.snapshot.discovery_next_page_token.is_none(),
            "transition {transition:?}"
        );
    }
}

#[tokio::test]
async fn discovery_continuation_result_is_rejected_after_every_identity_transition() {
    for transition in [
        IdentityTransition::Account,
        IdentityTransition::Session,
        IdentityTransition::NotCurrent,
        IdentityTransition::Logout,
    ] {
        let auth = Arc::new(MutableAuthState::authenticated("account-1"));
        let transition_auth = auth.clone();
        let calls = Arc::new(AtomicUsize::new(0));
        let call_counter = calls.clone();
        let mut discovery = MockDiscoveryPort::new();
        discovery
            .expect_get_feed()
            .withf(|identity, _, _| {
                identity.account_id == "account-1" && identity.session_id == "session-1"
            })
            .times(2)
            .returning(move |_, _, _| {
                if call_counter.fetch_add(1, Ordering::SeqCst) == 0 {
                    Ok(EnginePagedResult {
                        items: vec![track("old-owner-first")],
                        next_page_token: Some(
                            EnginePageToken::new("old-owner-next".into()).unwrap(),
                        ),
                    })
                } else {
                    transition_auth.set(transitioned_state(transition));
                    Ok(EnginePagedResult {
                        items: vec![track("old-owner-second")],
                        next_page_token: None,
                    })
                }
            });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth);
        engine.set_discovery_port(Arc::new(discovery));
        engine
            .dispatch(
                EngineCommand::load_discovery_feed(Vec::new(), EnginePageRequest::default()),
                1,
            )
            .await;

        let outcome = engine
            .dispatch(EngineCommand::load_next_discovery_page(), 2)
            .await;

        assert_eq!(
            outcome
                .snapshot
                .last_error
                .as_ref()
                .map(|error| error.error_type.clone()),
            Some(crate::EngineErrorType::LoginRequired),
            "transition {transition:?}"
        );
        assert!(
            outcome.snapshot.discovery_results.is_empty(),
            "transition {transition:?}"
        );
        assert!(
            outcome.snapshot.discovery_next_page_token.is_none(),
            "transition {transition:?}"
        );
    }
}

#[tokio::test]
async fn discovery_same_owner_typed_failure_is_preserved() {
    let mut discovery = MockDiscoveryPort::new();
    discovery.expect_get_feed().times(1).returning(|_, _, _| {
        Err(crate::EngineError::new(
            crate::EngineErrorType::ServiceUnavailable,
            "typed failure",
            true,
        ))
    });
    let auth = Arc::new(MutableAuthState::authenticated("account-1"));
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_discovery_port(Arc::new(discovery));

    let outcome = engine
        .dispatch(
            EngineCommand::load_discovery_feed(Vec::new(), EnginePageRequest::default()),
            1,
        )
        .await;

    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        crate::EngineErrorType::ServiceUnavailable
    );
}
