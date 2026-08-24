use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use panda_engine_core::engine::actor::{
    ActorConfig, ActorEvent, ActorFailure, ActorLane, ActorOutcome, ActorOutcomeStatus, ActorTask,
    CancellationReason, EngineActor, EngineActorEventReceiver, EngineActorHandle, PlayerEdgeEvent,
    PlayerFacts, SubmissionError,
};
use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, Engine, EngineCommand, EngineEffect,
    EngineError, EngineHistoryEntry, EngineHistoryIdentity, EngineHistorySettings,
    EngineHistorySettingsUpdate, EnginePageRequest, EnginePagedResult, EnginePlaybackRecord,
    HistoryPort, Middleware, MiddlewarePipeline, ThemePreference,
};
use tokio::sync::Notify;
use tokio::time::timeout;

const CONTRACT_TIMEOUT: Duration = Duration::from_millis(500);

struct RunningActor {
    handle: EngineActorHandle,
    events: EngineActorEventReceiver,
    task: ActorTask,
}

fn spawn_actor(engine: Engine, config: ActorConfig) -> RunningActor {
    let runtime = EngineActor::spawn(engine, config)
        .expect("Task 4 must implement the actor runtime defined by these contracts");
    let (handle, events, task) = runtime.into_parts();
    RunningActor {
        handle,
        events,
        task,
    }
}

async fn next_outcome(events: &mut EngineActorEventReceiver) -> ActorOutcome {
    loop {
        let event = timeout(CONTRACT_TIMEOUT, events.recv())
            .await
            .expect("actor event timed out")
            .expect("actor event channel closed");
        if let ActorEvent::CommandOutcome(outcome) = event {
            return outcome;
        }
    }
}

#[tokio::test(flavor = "current_thread")]
async fn state_changing_commands_are_processed_fifo() {
    let mut actor = spawn_actor(Engine::new(0), ActorConfig::default());
    let play = actor.handle.try_submit(EngineCommand::play(), 10).unwrap();
    let pause = actor.handle.try_submit(EngineCommand::pause(), 20).unwrap();
    let resume = actor.handle.try_submit(EngineCommand::play(), 30).unwrap();

    let outcomes = [
        next_outcome(&mut actor.events).await,
        next_outcome(&mut actor.events).await,
        next_outcome(&mut actor.events).await,
    ];

    assert_eq!(
        outcomes.each_ref().map(|outcome| outcome.command_id),
        [play, pause, resume]
    );
    assert_eq!(
        outcomes
            .each_ref()
            .map(|outcome| outcome.message_sequence.get()),
        [1, 2, 3]
    );
}

#[tokio::test(flavor = "current_thread")]
async fn bounded_command_mailbox_fails_fast_without_waiting() {
    let actor = spawn_actor(
        Engine::new(0),
        ActorConfig {
            command_capacity: 1,
            ..ActorConfig::default()
        },
    );

    actor.handle.try_submit(EngineCommand::play(), 10).unwrap();
    let overloaded = actor
        .handle
        .try_submit(EngineCommand::pause(), 20)
        .unwrap_err();

    assert_eq!(
        overloaded,
        SubmissionError::MailboxFull {
            lane: ActorLane::Command,
            capacity: 1,
        }
    );
}

#[tokio::test(flavor = "current_thread")]
async fn snapshots_are_immutable_and_revisions_advance_only_for_state_changes() {
    let mut actor = spawn_actor(Engine::new(0), ActorConfig::default());
    let initial = actor.handle.latest_snapshot();
    assert_eq!(initial.revision.get(), 0);

    actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
            10,
        )
        .unwrap();
    next_outcome(&mut actor.events).await;
    let changed = actor.handle.latest_snapshot();

    actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
            20,
        )
        .unwrap();
    next_outcome(&mut actor.events).await;
    let unchanged = actor.handle.latest_snapshot();

    actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::BambooGroveLight),
            30,
        )
        .unwrap();
    next_outcome(&mut actor.events).await;
    let changed_again = actor.handle.latest_snapshot();

    assert!(changed.revision > initial.revision);
    assert_eq!(unchanged.revision, changed.revision);
    assert!(Arc::ptr_eq(&unchanged.snapshot, &changed.snapshot));
    assert!(changed_again.revision > unchanged.revision);
    assert_eq!(
        changed.snapshot.theme_preference.theme,
        ThemePreference::ForestTechDark
    );
    assert_eq!(
        changed_again.snapshot.theme_preference.theme,
        ThemePreference::BambooGroveLight
    );
}

#[tokio::test(flavor = "current_thread")]
async fn explicit_shutdown_cancels_an_accepted_in_flight_command() {
    let (engine, port) = engine_with_blocking_history();
    let mut actor = spawn_actor(engine, ActorConfig::default());
    let pending = actor
        .handle
        .try_submit(EngineCommand::load_history_settings(), 10)
        .unwrap();
    timeout(CONTRACT_TIMEOUT, port.started.notified())
        .await
        .expect("history operation never started");

    actor.handle.request_shutdown().unwrap();
    let outcome = next_outcome(&mut actor.events).await;

    assert_eq!(outcome.command_id, pending);
    assert_eq!(
        outcome.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::ShutdownRequested)
    );
    assert_eq!(
        actor.handle.try_submit(EngineCommand::play(), 20),
        Err(SubmissionError::ShuttingDown)
    );
    actor.task.wait().await.expect("shutdown must be clean");
}

#[tokio::test(flavor = "current_thread")]
async fn slow_remote_work_does_not_block_snapshot_reads_or_unrelated_commands() {
    let (engine, port) = engine_with_blocking_history();
    let mut actor = spawn_actor(engine, ActorConfig::default());
    let slow = actor
        .handle
        .try_submit(EngineCommand::load_history_settings(), 10)
        .unwrap();
    timeout(CONTRACT_TIMEOUT, port.started.notified())
        .await
        .expect("history operation never started");

    let readable_while_pending = actor.handle.latest_snapshot();
    let unrelated = actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::MoonlitBambooDark),
            20,
        )
        .unwrap();
    let unrelated_outcome = next_outcome(&mut actor.events).await;

    assert_eq!(unrelated_outcome.command_id, unrelated);
    assert_ne!(unrelated_outcome.command_id, slow);
    assert!(
        actor.handle.latest_snapshot().revision >= readable_while_pending.revision,
        "snapshot publication must remain available while an operation is pending"
    );
    assert_eq!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .theme_preference
            .theme,
        ThemePreference::MoonlitBambooDark
    );

    port.release.notify_one();
    assert_eq!(next_outcome(&mut actor.events).await.command_id, slow);
}

#[tokio::test(flavor = "current_thread")]
async fn player_facts_conflate_to_the_latest_value() {
    let mut actor = spawn_actor(Engine::new(0), ActorConfig::default());
    let playback = panda_engine_core::engine::actor::PlaybackInstanceId::new(7);
    let latest = PlayerFacts {
        playback_instance_id: playback,
        position_millis: 30,
        buffered_position_millis: 60,
        play_when_ready: true,
        is_playing: true,
    };

    actor
        .handle
        .publish_player_facts(PlayerFacts {
            position_millis: 10,
            ..latest
        })
        .unwrap();
    actor
        .handle
        .publish_player_facts(PlayerFacts {
            position_millis: 20,
            ..latest
        })
        .unwrap();
    actor.handle.publish_player_facts(latest).unwrap();

    let applied = timeout(CONTRACT_TIMEOUT, actor.events.recv())
        .await
        .expect("latest player facts timed out")
        .expect("actor event channel closed");
    assert_eq!(
        applied,
        ActorEvent::PlayerFactsApplied {
            facts: latest,
            message_sequence: panda_engine_core::engine::actor::MessageSequence::new(1),
        }
    );
    assert!(
        timeout(Duration::from_millis(25), actor.events.recv())
            .await
            .is_err(),
        "superseded player facts must not be replayed"
    );
}

#[tokio::test(flavor = "current_thread")]
async fn terminal_player_edges_are_reliable_and_ordered() {
    let mut actor = spawn_actor(
        Engine::new(0),
        ActorConfig {
            player_edge_capacity: 4,
            ..ActorConfig::default()
        },
    );
    let playback = panda_engine_core::engine::actor::PlaybackInstanceId::new(9);
    let expected = [
        PlayerEdgeEvent::Ended {
            playback_instance_id: playback,
        },
        PlayerEdgeEvent::SourceRejected {
            playback_instance_id: playback,
        },
        PlayerEdgeEvent::DecoderFailed {
            playback_instance_id: playback,
            error_code: 41,
        },
        PlayerEdgeEvent::PlayerFailed {
            playback_instance_id: playback,
            error_code: 42,
        },
    ];

    for event in expected {
        actor.handle.try_publish_player_edge(event).unwrap();
    }

    let mut actual = Vec::new();
    for expected_sequence in 1..=4 {
        let event = timeout(CONTRACT_TIMEOUT, actor.events.recv())
            .await
            .expect("player edge timed out")
            .expect("actor event channel closed");
        match event {
            ActorEvent::PlayerEdgeApplied {
                event,
                message_sequence,
            } => {
                assert_eq!(message_sequence.get(), expected_sequence);
                actual.push(event);
            }
            other => panic!("unexpected actor event: {other:?}"),
        }
    }
    assert_eq!(actual, expected);
}

#[tokio::test(flavor = "current_thread")]
async fn command_outcomes_and_effects_preserve_acceptance_order() {
    let mut actor = spawn_actor(Engine::new(0), ActorConfig::default());
    let start = actor
        .handle
        .try_submit(EngineCommand::start_session("account-1".into()), 10)
        .unwrap();
    let end = actor
        .handle
        .try_submit(EngineCommand::end_session(), 20)
        .unwrap();

    let started = next_outcome(&mut actor.events).await;
    let ended = next_outcome(&mut actor.events).await;

    assert_eq!([started.command_id, ended.command_id], [start, end]);
    assert_eq!(
        started.effects,
        vec![EngineEffect::SessionStarted {
            session_id: "session-10".into(),
        }]
    );
    assert_eq!(ended.effects, vec![EngineEffect::SessionEnded]);
}

struct PanicMiddleware;

impl Middleware for PanicMiddleware {
    fn before_dispatch(&self, _: &Engine, _: &EngineCommand) -> Result<(), EngineError> {
        panic!("intentional actor contract panic")
    }
}

#[tokio::test(flavor = "current_thread")]
async fn actor_panic_fails_every_pending_request_and_reports_the_failure() {
    let mut engine = Engine::new(0);
    let mut middleware = MiddlewarePipeline::new();
    middleware.add(Box::new(PanicMiddleware));
    engine.set_middleware(middleware);
    let mut actor = spawn_actor(engine, ActorConfig::default());
    let first = actor.handle.try_submit(EngineCommand::play(), 10).unwrap();
    let second = actor.handle.try_submit(EngineCommand::pause(), 20).unwrap();

    let first_failure = next_outcome(&mut actor.events).await;
    let second_failure = next_outcome(&mut actor.events).await;

    assert_eq!(
        [first_failure.command_id, second_failure.command_id],
        [first, second]
    );
    assert_eq!(
        first_failure.status,
        ActorOutcomeStatus::Failed(ActorFailure::Panicked)
    );
    assert_eq!(second_failure.status, first_failure.status);
    assert_eq!(actor.task.wait().await, Err(ActorFailure::Panicked));
}

#[tokio::test(flavor = "current_thread")]
async fn command_channel_closure_fails_already_accepted_requests() {
    let RunningActor {
        handle,
        mut events,
        task,
    } = spawn_actor(Engine::new(0), ActorConfig::default());
    let first = handle.try_submit(EngineCommand::play(), 10).unwrap();
    let second = handle.try_submit(EngineCommand::pause(), 20).unwrap();
    drop(handle);

    let first_failure = next_outcome(&mut events).await;
    let second_failure = next_outcome(&mut events).await;

    assert_eq!(
        [first_failure.command_id, second_failure.command_id],
        [first, second]
    );
    assert_eq!(
        first_failure.status,
        ActorOutcomeStatus::Failed(ActorFailure::CommandChannelClosed)
    );
    assert_eq!(second_failure.status, first_failure.status);
    assert_eq!(task.wait().await, Err(ActorFailure::CommandChannelClosed));
}

struct MutableAuth(Mutex<AuthState>);

impl AuthStateProvider for MutableAuth {
    fn current_auth_state(&self) -> AuthState {
        self.0.lock().unwrap().clone()
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

fn engine_with_blocking_history() -> (Engine, Arc<BlockingHistoryPort>) {
    let auth = Arc::new(MutableAuth(Mutex::new(AuthState::Authenticated {
        account: Account {
            id: "account-1".into(),
            primary_email: "account-1@example.com".into(),
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
    })));
    let port = Arc::new(BlockingHistoryPort {
        started: Notify::new(),
        release: Notify::new(),
    });
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_history_port(port.clone());
    (engine, port)
}
