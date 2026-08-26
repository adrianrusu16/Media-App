use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use panda_engine_core::engine::actor::{
    ActorClockConfig, ActorConfig, ActorControl, ActorEvent, ActorFailure, ActorLane, ActorOutcome,
    ActorOutcomeStatus, ActorTask, CancellationReason, EngineActor, EngineActorEventReceiver,
    EngineActorHandle, EngineOperation, EngineOperationCompletion, EngineOperationResult,
    MessageSequence, PlaybackInstanceId, PlayerEdgeEvent, PlayerFacts, SubmissionError,
};
use panda_engine_core::{
    Account, AccountPort, AuthSession, AuthState, AuthStateProvider, Engine, EngineAccountIdentity,
    EngineCommand, EngineCommandType, EngineCreatePlaylist, EngineEffect, EngineError,
    EngineHistoryEntry, EngineHistoryIdentity, EngineHistorySettings, EngineHistorySettingsUpdate,
    EnginePageRequest, EnginePageToken, EnginePagedResult, EnginePlaybackRecord,
    EnginePlaybackSource, EnginePlaylist, EnginePlaylistIdentity, EnginePlaylistTrack,
    EngineUpdatePlaylist, HistoryPort, MediaItem, MediaRepository, Middleware, MiddlewarePipeline,
    PlaybackPort, PlaylistPort, ThemePreference,
};
use tokio::sync::Notify;
use tokio::time::{advance, timeout};

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

async fn next_operation(
    events: &mut EngineActorEventReceiver,
) -> (EngineOperation, MessageSequence) {
    loop {
        let event = timeout(CONTRACT_TIMEOUT, events.recv())
            .await
            .expect("actor operation timed out")
            .expect("actor event channel closed");
        if let ActorEvent::OperationLaunched {
            operation,
            message_sequence,
        } = event
        {
            return (operation, message_sequence);
        }
    }
}

async fn next_event(events: &mut EngineActorEventReceiver) -> ActorEvent {
    timeout(CONTRACT_TIMEOUT, events.recv())
        .await
        .expect("actor event timed out")
        .expect("actor event channel closed")
}

struct EventCursor {
    events: EngineActorEventReceiver,
    buffered: VecDeque<ActorEvent>,
}

impl EventCursor {
    fn new(events: EngineActorEventReceiver) -> Self {
        Self {
            events,
            buffered: VecDeque::new(),
        }
    }

    async fn next_outcome(&mut self) -> ActorOutcome {
        loop {
            if let Some(ActorEvent::CommandOutcome(outcome)) =
                self.take_buffered(|event| matches!(event, ActorEvent::CommandOutcome(_)))
            {
                return outcome;
            }

            match self.recv_raw().await {
                ActorEvent::CommandOutcome(outcome) => return outcome,
                event => self.buffered.push_back(event),
            }
        }
    }

    async fn next_tick(&mut self) -> (u64, MessageSequence) {
        loop {
            if let Some(ActorEvent::TickProcessed {
                now_epoch_millis,
                message_sequence,
                ..
            }) = self.take_buffered(|event| matches!(event, ActorEvent::TickProcessed { .. }))
            {
                return (now_epoch_millis, message_sequence);
            }

            match self.recv_raw().await {
                ActorEvent::TickProcessed {
                    now_epoch_millis,
                    message_sequence,
                    ..
                } => return (now_epoch_millis, message_sequence),
                event => self.buffered.push_back(event),
            }
        }
    }

    async fn recv_raw(&mut self) -> ActorEvent {
        timeout(CONTRACT_TIMEOUT, self.events.recv())
            .await
            .expect("actor event timed out")
            .expect("actor event channel closed")
    }

    fn take_buffered(&mut self, predicate: impl FnMut(&ActorEvent) -> bool) -> Option<ActorEvent> {
        let index = self.buffered.iter().position(predicate)?;
        self.buffered.remove(index)
    }
}

fn record_terminal_outcome(event: ActorEvent, outcomes: &mut Vec<ActorOutcome>) {
    if let ActorEvent::CommandOutcome(outcome) = event {
        outcomes.push(outcome);
    }
}

fn drain_available_terminal_outcomes(
    events: &mut EngineActorEventReceiver,
    outcomes: &mut Vec<ActorOutcome>,
) {
    while let Some(event) = events.try_recv() {
        record_terminal_outcome(event, outcomes);
    }
}

fn complete(
    operation: &EngineOperation,
    result: EngineOperationResult,
) -> EngineOperationCompletion {
    operation.completion(Ok(result))
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

    assert_eq!(changed.revision.get(), 1);
    assert_eq!(unchanged.revision, changed.revision);
    assert!(Arc::ptr_eq(&unchanged.snapshot, &changed.snapshot));
    assert_eq!(changed_again.revision.get(), 2);
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
async fn shutdown_accounts_for_blocked_and_queued_accepted_commands_before_wait_returns() {
    let (engine, port) = engine_with_blocking_history();
    let RunningActor {
        handle,
        mut events,
        task,
    } = spawn_actor(engine, ActorConfig::default());
    let blocked = handle
        .try_submit(EngineCommand::load_history_settings(), 10)
        .unwrap();
    timeout(CONTRACT_TIMEOUT, port.started.notified())
        .await
        .expect("history operation never started");

    let queued = [
        handle
            .try_submit(
                EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
                20,
            )
            .unwrap(),
        handle.try_submit(EngineCommand::play(), 30).unwrap(),
        handle.try_submit(EngineCommand::pause(), 40).unwrap(),
    ];
    let accepted = [blocked, queued[0], queued[1], queued[2]];

    handle.request_shutdown().unwrap();
    assert_eq!(
        handle.try_submit(EngineCommand::play(), 20),
        Err(SubmissionError::ShuttingDown)
    );

    enum ShutdownRace {
        Exit(Result<panda_engine_core::engine::actor::ActorExit, ActorFailure>),
        Event(Option<ActorEvent>),
    }

    let mut wait = Box::pin(task.wait());
    let mut outcomes = Vec::new();
    let exit = loop {
        if outcomes.len() == accepted.len() {
            break timeout(CONTRACT_TIMEOUT, &mut wait)
                .await
                .expect("actor wait timed out")
                .expect("shutdown must be clean");
        }

        let race = timeout(CONTRACT_TIMEOUT, async {
            tokio::select! {
                exit = &mut wait => ShutdownRace::Exit(exit),
                event = events.recv() => ShutdownRace::Event(event),
            }
        })
        .await
        .expect("actor shutdown race timed out");

        match race {
            ShutdownRace::Exit(exit) => {
                drain_available_terminal_outcomes(&mut events, &mut outcomes);
                assert_eq!(
                    outcomes.len(),
                    accepted.len(),
                    "actor wait returned before every accepted command had an observable terminal outcome"
                );
                break exit.expect("shutdown must be clean");
            }
            ShutdownRace::Event(Some(event)) => {
                record_terminal_outcome(event, &mut outcomes);
            }
            ShutdownRace::Event(None) => panic!("actor event channel closed before shutdown"),
        }
    };

    assert_eq!(outcomes[0].command_id, blocked);
    assert_eq!(
        outcomes[0].status,
        ActorOutcomeStatus::Cancelled(CancellationReason::ShutdownRequested)
    );
    let mut terminal_ids = outcomes
        .iter()
        .map(|outcome| {
            assert!(matches!(
                outcome.status,
                ActorOutcomeStatus::Completed
                    | ActorOutcomeStatus::Cancelled(CancellationReason::ShutdownRequested)
            ));
            outcome.command_id
        })
        .collect::<Vec<_>>();
    terminal_ids.sort();
    let mut expected_ids = accepted.to_vec();
    expected_ids.sort();
    assert_eq!(terminal_ids, expected_ids);
    assert_eq!(exit.terminal_commands, accepted);
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
async fn account_identity_replacement_applies_current_and_rejects_stale_completion() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    apply_auth_state(&mut actor, auth_state("account-1", "session-1")).await;
    let old_command = actor.handle.try_submit(get_account_command(), 10).unwrap();
    let (old_operation, _) = next_operation(&mut actor.events).await;

    apply_auth_state(&mut actor, auth_state("account-2", "session-1")).await;
    let before_stale = actor.handle.latest_snapshot();
    actor
        .handle
        .try_complete_operation(complete(
            &old_operation,
            EngineOperationResult::AccountProjection(account("account-1")),
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;

    assert_eq!(stale.command_id, old_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(stale.snapshot_revision, before_stale.revision);
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.as_ref(),
        before_stale.snapshot.as_ref()
    );
    assert!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .protected_account
            .is_none()
    );

    let current_command = actor.handle.try_submit(get_account_command(), 20).unwrap();
    let (current_operation, _) = next_operation(&mut actor.events).await;
    actor
        .handle
        .try_complete_operation(complete(
            &current_operation,
            EngineOperationResult::AccountProjection(account("account-2")),
        ))
        .unwrap();
    let current = next_outcome(&mut actor.events).await;

    assert_eq!(current.command_id, current_command);
    assert_eq!(current.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .protected_account
            .as_ref()
            .map(|account| account.id.as_str()),
        Some("account-2")
    );
}

#[tokio::test(flavor = "current_thread")]
async fn account_session_replacement_applies_current_and_rejects_stale_completion() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    apply_auth_state(&mut actor, auth_state("account-1", "session-1")).await;
    let old_command = actor.handle.try_submit(get_account_command(), 10).unwrap();
    let (old_operation, _) = next_operation(&mut actor.events).await;

    apply_auth_state(&mut actor, auth_state("account-1", "session-2")).await;
    let before_stale = actor.handle.latest_snapshot();
    actor
        .handle
        .try_complete_operation(complete(
            &old_operation,
            EngineOperationResult::AccountProjection(account("account-1")),
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;

    assert_eq!(stale.command_id, old_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .protected_account
            .is_none()
    );

    let current_command = actor.handle.try_submit(get_account_command(), 20).unwrap();
    let (current_operation, _) = next_operation(&mut actor.events).await;
    actor
        .handle
        .try_complete_operation(complete(
            &current_operation,
            EngineOperationResult::AccountProjection(account("account-1")),
        ))
        .unwrap();
    let current = next_outcome(&mut actor.events).await;

    assert_eq!(current.command_id, current_command);
    assert_eq!(current.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.auth_state,
        auth_state("account-1", "session-2")
    );
    assert!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .protected_account
            .is_some()
    );
}

#[tokio::test(flavor = "current_thread")]
async fn search_pagination_applies_current_pages_and_rejects_a_stale_continuation() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    let first_command = actor
        .handle
        .try_submit(EngineCommand::search("first".into()), 10)
        .unwrap();
    let (first_operation, _) = next_operation(&mut actor.events).await;
    actor
        .handle
        .try_complete_operation(complete(
            &first_operation,
            search_result("search-1", vec![media_item("first-1")], Some("next-1")),
        ))
        .unwrap();
    let first = next_outcome(&mut actor.events).await;
    assert_eq!(first.command_id, first_command);
    assert_eq!(first.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.search_results,
        vec![media_item("first-1")]
    );
    // The engine owns catalog operation ids on both the inline and split paths.
    let catalog_operation_id = first
        .event
        .as_ref()
        .and_then(|event| event.message.clone())
        .expect("a completed search carries its catalog operation id");

    let continuation_command = actor
        .handle
        .try_submit(
            EngineCommand::load_next_catalog_page(catalog_operation_id),
            20,
        )
        .unwrap();
    let (continuation_operation, _) = next_operation(&mut actor.events).await;
    let replacement_command = actor
        .handle
        .try_submit(EngineCommand::search("replacement".into()), 30)
        .unwrap();
    let (replacement_operation, _) = next_operation(&mut actor.events).await;
    let before_stale = actor.handle.latest_snapshot();

    actor
        .handle
        .try_complete_operation(complete(
            &continuation_operation,
            search_result("search-1", vec![media_item("first-2")], None),
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;
    assert_eq!(stale.command_id, continuation_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.search_results,
        before_stale.snapshot.search_results
    );

    actor
        .handle
        .try_complete_operation(complete(
            &replacement_operation,
            search_result("search-2", vec![media_item("replacement-1")], None),
        ))
        .unwrap();
    let replacement = next_outcome(&mut actor.events).await;
    assert_eq!(replacement.command_id, replacement_command);
    assert_eq!(replacement.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.search_results,
        vec![media_item("replacement-1")]
    );
}

#[tokio::test(flavor = "current_thread")]
async fn playlist_replacement_applies_current_and_rejects_stale_completion() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    apply_auth_state(&mut actor, auth_state("account-1", "session-1")).await;
    let old_command = actor
        .handle
        .try_submit(list_playlists_command(), 10)
        .unwrap();
    let (old_operation, _) = next_operation(&mut actor.events).await;
    let current_command = actor
        .handle
        .try_submit(list_playlists_command(), 20)
        .unwrap();
    let (current_operation, _) = next_operation(&mut actor.events).await;
    let before_stale = actor.handle.latest_snapshot();

    actor
        .handle
        .try_complete_operation(complete(
            &old_operation,
            EngineOperationResult::PlaylistPage {
                playlists: vec![playlist("old")],
                next_page_token: None,
            },
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;
    assert_eq!(stale.command_id, old_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.playlists,
        before_stale.snapshot.playlists
    );

    actor
        .handle
        .try_complete_operation(complete(
            &current_operation,
            EngineOperationResult::PlaylistPage {
                playlists: vec![playlist("current")],
                next_page_token: None,
            },
        ))
        .unwrap();
    let current = next_outcome(&mut actor.events).await;
    assert_eq!(current.command_id, current_command);
    assert_eq!(current.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.playlists,
        vec![playlist("current")]
    );
}

#[tokio::test(flavor = "current_thread")]
async fn playback_replacement_applies_current_and_rejects_stale_resolution() {
    let mut actor = spawn_actor(
        split_engine(&["track-old", "track-current"]),
        ActorConfig::default(),
    );
    let old_command = actor
        .handle
        .try_submit(EngineCommand::play_media_by_id("track-old".into()), 10)
        .unwrap();
    let (old_operation, _) = next_operation(&mut actor.events).await;
    let current_command = actor
        .handle
        .try_submit(EngineCommand::play_media_by_id("track-current".into()), 20)
        .unwrap();
    let (current_operation, _) = next_operation(&mut actor.events).await;
    let before_stale = actor.handle.latest_snapshot();

    actor
        .handle
        .try_complete_operation(complete(
            &old_operation,
            EngineOperationResult::PlaybackResolved(playback_source("track-old")),
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;
    assert_eq!(stale.command_id, old_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.source_uri,
        before_stale.snapshot.source_uri
    );

    actor
        .handle
        .try_complete_operation(complete(
            &current_operation,
            EngineOperationResult::PlaybackResolved(playback_source("track-current")),
        ))
        .unwrap();
    let current = next_outcome(&mut actor.events).await;
    assert_eq!(current.command_id, current_command);
    assert_eq!(current.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor
            .handle
            .latest_snapshot()
            .snapshot
            .source_uri
            .as_deref(),
        Some("https://media.test/track-current")
    );
}

#[tokio::test(flavor = "current_thread")]
async fn history_replacement_applies_current_and_rejects_stale_completion() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    apply_auth_state(&mut actor, auth_state("account-1", "session-1")).await;
    let old_command = actor
        .handle
        .try_submit(EngineCommand::load_history_settings(), 10)
        .unwrap();
    let (old_operation, _) = next_operation(&mut actor.events).await;
    let current_command = actor
        .handle
        .try_submit(EngineCommand::load_history_settings(), 20)
        .unwrap();
    let (current_operation, _) = next_operation(&mut actor.events).await;
    let before_stale = actor.handle.latest_snapshot();

    actor
        .handle
        .try_complete_operation(complete(
            &old_operation,
            EngineOperationResult::HistorySettings(EngineHistorySettings { enabled: false }),
        ))
        .unwrap();
    let stale = next_outcome(&mut actor.events).await;
    assert_eq!(stale.command_id, old_command);
    assert_eq!(
        stale.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );
    assert_eq!(
        actor.handle.latest_snapshot().revision,
        before_stale.revision
    );
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.history_settings,
        before_stale.snapshot.history_settings
    );

    actor
        .handle
        .try_complete_operation(complete(
            &current_operation,
            EngineOperationResult::HistorySettings(EngineHistorySettings { enabled: true }),
        ))
        .unwrap();
    let current = next_outcome(&mut actor.events).await;
    assert_eq!(current.command_id, current_command);
    assert_eq!(current.status, ActorOutcomeStatus::Completed);
    assert_eq!(
        actor.handle.latest_snapshot().snapshot.history_settings,
        Some(EngineHistorySettings { enabled: true })
    );
}

#[tokio::test(flavor = "current_thread", start_paused = true)]
async fn actor_owned_ticks_continue_while_commands_are_processed() {
    let RunningActor { handle, events, .. } = spawn_actor(
        Engine::new(1_000),
        ActorConfig {
            clock: ActorClockConfig {
                start_epoch_millis: 1_000,
                tick_interval: Duration::from_millis(20),
            },
            ..ActorConfig::default()
        },
    );
    let mut events = EventCursor::new(events);

    let first = handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
            1_005,
        )
        .unwrap();
    assert_eq!(events.next_outcome().await.command_id, first);

    advance(Duration::from_millis(20)).await;
    let first_tick = events.next_tick().await;
    assert_eq!(first_tick.0, 1_020);

    let second = handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::BambooGroveLight),
            1_025,
        )
        .unwrap();
    let second_outcome = events.next_outcome().await;
    assert_eq!(second_outcome.command_id, second);
    assert!(second_outcome.message_sequence > first_tick.1);

    advance(Duration::from_millis(20)).await;
    let second_tick = events.next_tick().await;
    assert_eq!(second_tick.0, 1_040);
    assert!(second_tick.1 > second_outcome.message_sequence);
}

#[tokio::test(flavor = "current_thread")]
async fn message_sequence_is_one_global_counter_across_actor_lanes() {
    let mut actor = spawn_actor(
        split_engine(&[]),
        ActorConfig {
            clock: ActorClockConfig {
                start_epoch_millis: 0,
                tick_interval: Duration::from_secs(1),
            },
            ..ActorConfig::default()
        },
    );

    let local = actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
            10,
        )
        .unwrap();
    let local_outcome = next_outcome(&mut actor.events).await;
    assert_eq!(local_outcome.command_id, local);
    assert_eq!(local_outcome.message_sequence.get(), 1);

    actor
        .handle
        .try_submit(EngineCommand::search("sequence".into()), 20)
        .unwrap();
    let (operation, launched_sequence) = next_operation(&mut actor.events).await;
    assert_eq!(launched_sequence.get(), 2);
    actor
        .handle
        .try_complete_operation(complete(
            &operation,
            search_result("sequence-1", vec![media_item("sequence")], None),
        ))
        .unwrap();
    assert_eq!(
        next_outcome(&mut actor.events).await.message_sequence.get(),
        3
    );

    let playback = PlaybackInstanceId::new(0);
    actor
        .handle
        .try_publish_player_edge(PlayerEdgeEvent::Ended {
            playback_instance_id: playback,
        })
        .unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::PlayerEdgeApplied {
            message_sequence, ..
        } => assert_eq!(message_sequence.get(), 4),
        other => panic!("unexpected actor event: {other:?}"),
    }

    actor
        .handle
        .try_send_control(ActorControl::AuthStateChanged(auth_state(
            "account-1",
            "session-1",
        )))
        .unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::ControlApplied {
            message_sequence, ..
        } => assert_eq!(message_sequence.get(), 5),
        other => panic!("unexpected actor event: {other:?}"),
    }

    actor
        .handle
        .publish_player_facts(PlayerFacts {
            playback_instance_id: playback,
            position_millis: 100,
            buffered_position_millis: 200,
            play_when_ready: true,
            is_playing: true,
        })
        .unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::PlayerFactsApplied {
            message_sequence, ..
        } => assert_eq!(message_sequence.get(), 6),
        other => panic!("unexpected actor event: {other:?}"),
    }

    let tick = timeout(Duration::from_millis(1_500), async {
        loop {
            if let ActorEvent::TickProcessed {
                message_sequence, ..
            } = actor
                .events
                .recv()
                .await
                .expect("actor event channel closed")
            {
                break message_sequence;
            }
        }
    })
    .await
    .expect("scheduled tick timed out");
    assert_eq!(tick.get(), 7);
}

#[tokio::test(flavor = "current_thread")]
async fn operation_completion_lane_is_bounded_reliable_and_drains() {
    let mut actor = spawn_actor(
        split_engine(&[]),
        ActorConfig {
            operation_completion_capacity: 1,
            ..ActorConfig::default()
        },
    );
    let first_command = actor
        .handle
        .try_submit(EngineCommand::search("old".into()), 10)
        .unwrap();
    let (first_operation, _) = next_operation(&mut actor.events).await;
    let second_command = actor
        .handle
        .try_submit(EngineCommand::search("current".into()), 20)
        .unwrap();
    let (second_operation, _) = next_operation(&mut actor.events).await;
    let first_completion = complete(
        &first_operation,
        search_result("old", vec![media_item("old")], None),
    );
    let second_completion = complete(
        &second_operation,
        search_result("current", vec![media_item("current")], None),
    );

    actor
        .handle
        .try_complete_operation(first_completion)
        .unwrap();
    assert_eq!(
        actor
            .handle
            .try_complete_operation(second_completion.clone()),
        Err(SubmissionError::MailboxFull {
            lane: ActorLane::OperationCompletion,
            capacity: 1,
        })
    );
    let first = next_outcome(&mut actor.events).await;
    assert_eq!(first.command_id, first_command);
    assert_eq!(
        first.status,
        ActorOutcomeStatus::Cancelled(CancellationReason::Superseded)
    );

    actor
        .handle
        .try_complete_operation(second_completion)
        .unwrap();
    let second = next_outcome(&mut actor.events).await;
    assert_eq!(second.command_id, second_command);
    assert_eq!(second.status, ActorOutcomeStatus::Completed);
}

#[tokio::test(flavor = "current_thread")]
async fn player_edge_lane_is_bounded_reliable_and_drains_without_dropping() {
    let mut actor = spawn_actor(
        Engine::new(0),
        ActorConfig {
            player_edge_capacity: 1,
            ..ActorConfig::default()
        },
    );
    let playback = PlaybackInstanceId::new(0);
    let ended = PlayerEdgeEvent::Ended {
        playback_instance_id: playback,
    };
    let failed = PlayerEdgeEvent::PlayerFailed {
        playback_instance_id: playback,
        error_code: 42,
    };

    actor.handle.try_publish_player_edge(ended).unwrap();
    assert_eq!(
        actor.handle.try_publish_player_edge(failed),
        Err(SubmissionError::MailboxFull {
            lane: ActorLane::PlayerEdge,
            capacity: 1,
        })
    );
    match next_event(&mut actor.events).await {
        ActorEvent::PlayerEdgeApplied { event, .. } => assert_eq!(event, ended),
        other => panic!("unexpected actor event: {other:?}"),
    }

    actor.handle.try_publish_player_edge(failed).unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::PlayerEdgeApplied { event, .. } => assert_eq!(event, failed),
        other => panic!("unexpected actor event: {other:?}"),
    }
}

#[tokio::test(flavor = "current_thread")]
async fn lifecycle_control_lane_is_bounded_reliable_and_drains_before_shutdown() {
    let mut actor = spawn_actor(
        Engine::new(0),
        ActorConfig {
            lifecycle_capacity: 1,
            ..ActorConfig::default()
        },
    );
    let control = ActorControl::AuthStateChanged(auth_state("account-1", "session-1"));

    actor.handle.try_send_control(control.clone()).unwrap();
    assert_eq!(
        actor.handle.request_shutdown(),
        Err(SubmissionError::MailboxFull {
            lane: ActorLane::Lifecycle,
            capacity: 1,
        })
    );
    match next_event(&mut actor.events).await {
        ActorEvent::ControlApplied {
            control: applied, ..
        } => assert_eq!(applied, control),
        other => panic!("unexpected actor event: {other:?}"),
    }

    actor.handle.request_shutdown().unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::ShutdownStarted { .. } => {}
        other => panic!("unexpected actor event: {other:?}"),
    }
    actor.task.wait().await.expect("shutdown must be clean");
}

#[tokio::test(flavor = "current_thread")]
async fn async_outcomes_follow_terminal_readiness_not_command_acceptance_order() {
    let mut actor = spawn_actor(split_engine(&[]), ActorConfig::default());
    apply_auth_state(&mut actor, auth_state("account-1", "session-1")).await;
    let first = actor
        .handle
        .try_submit(EngineCommand::load_history_settings(), 10)
        .unwrap();
    let (first_operation, _) = next_operation(&mut actor.events).await;
    let second = actor
        .handle
        .try_submit(EngineCommand::search("second".into()), 20)
        .unwrap();
    let (second_operation, _) = next_operation(&mut actor.events).await;
    let unrelated = actor
        .handle
        .try_submit(
            EngineCommand::set_theme_preference(ThemePreference::ForestTechDark),
            30,
        )
        .unwrap();

    let unrelated_outcome = next_outcome(&mut actor.events).await;
    assert_eq!(unrelated_outcome.command_id, unrelated);

    actor
        .handle
        .try_complete_operation(complete(
            &second_operation,
            search_result("second", vec![media_item("second")], None),
        ))
        .unwrap();
    let second_outcome = next_outcome(&mut actor.events).await;
    assert_eq!(second_outcome.command_id, second);

    actor
        .handle
        .try_complete_operation(complete(
            &first_operation,
            EngineOperationResult::HistorySettings(EngineHistorySettings { enabled: true }),
        ))
        .unwrap();
    let first_outcome = next_outcome(&mut actor.events).await;
    assert_eq!(first_outcome.command_id, first);

    assert_eq!(
        [
            unrelated_outcome.command_id,
            second_outcome.command_id,
            first_outcome.command_id,
        ],
        [unrelated, second, first]
    );
    assert!(
        unrelated_outcome.message_sequence < second_outcome.message_sequence
            && second_outcome.message_sequence < first_outcome.message_sequence
    );
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
async fn synchronous_command_outcomes_and_effects_preserve_acceptance_order() {
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

async fn apply_auth_state(actor: &mut RunningActor, state: AuthState) -> MessageSequence {
    actor
        .handle
        .try_send_control(ActorControl::AuthStateChanged(state.clone()))
        .unwrap();
    match next_event(&mut actor.events).await {
        ActorEvent::ControlApplied {
            control: ActorControl::AuthStateChanged(applied),
            message_sequence,
            ..
        } => {
            assert_eq!(applied, state);
            message_sequence
        }
        other => panic!("unexpected actor event: {other:?}"),
    }
}

fn auth_state(account_id: &str, session_id: &str) -> AuthState {
    AuthState::Authenticated {
        account: account(account_id),
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

fn account(id: &str) -> Account {
    Account {
        id: id.into(),
        primary_email: format!("{id}@example.com"),
        status: "active".into(),
        created_at_epoch_millis: 1,
    }
}

fn get_account_command() -> EngineCommand {
    EngineCommand::new(EngineCommandType::GetAccount, None)
}

fn list_playlists_command() -> EngineCommand {
    EngineCommand::new(
        EngineCommandType::ListPlaylists {
            page: EnginePageRequest {
                page_size: 20,
                page_token: None,
            },
        },
        None,
    )
}

fn media_item(id: &str) -> MediaItem {
    MediaItem {
        id: id.into(),
        title: id.into(),
        artist: "Artist".into(),
        ..MediaItem::default()
    }
}

fn search_result(
    operation_id: &str,
    items: Vec<MediaItem>,
    next_page_token: Option<&str>,
) -> EngineOperationResult {
    EngineOperationResult::SearchPage {
        catalog_operation_id: operation_id.into(),
        items,
        next_page_token: next_page_token
            .map(|token| EnginePageToken::new(token.into()).expect("valid page token")),
    }
}

fn playlist(id: &str) -> EnginePlaylist {
    EnginePlaylist {
        id: id.into(),
        name: id.into(),
        description: None,
        revision: 1,
        created_at_epoch_millis: 1,
        updated_at_epoch_millis: 1,
    }
}

fn playback_source(track_id: &str) -> EnginePlaybackSource {
    EnginePlaybackSource {
        track_id: track_id.into(),
        url: format!("https://media.test/{track_id}"),
        content_type: "audio/flac".into(),
        codec: "flac".into(),
        duration_millis: 180_000,
        expires_at_epoch_millis: 60_000,
    }
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

/// Ports whose remote calls never resolve.
///
/// Splitting a command now launches a real worker, so these tests would race
/// their own injected completion against the worker's. Ports that never resolve
/// keep the injected completion the only one that lands, which is what these
/// contracts are actually about.
struct PendingAccountPort;

#[async_trait]
impl AccountPort for PendingAccountPort {
    async fn get_account(&self, _: &EngineAccountIdentity) -> Result<Account, EngineError> {
        std::future::pending().await
    }
    async fn list_sessions(
        &self,
        _: &EngineAccountIdentity,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<AuthSession>, EngineError> {
        unreachable!()
    }
    async fn revoke_session(&self, _: &EngineAccountIdentity, _: &str) -> Result<(), EngineError> {
        unreachable!()
    }
    async fn delete_account(&self, _: &EngineAccountIdentity) -> Result<(), EngineError> {
        unreachable!()
    }
}

struct PendingPlaylistPort;

#[async_trait]
impl PlaylistPort for PendingPlaylistPort {
    async fn list(
        &self,
        _: &EnginePlaylistIdentity,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylist>, EngineError> {
        std::future::pending().await
    }
    async fn create(
        &self,
        _: &EnginePlaylistIdentity,
        _: EngineCreatePlaylist,
    ) -> Result<EnginePlaylist, EngineError> {
        unreachable!()
    }
    async fn get(
        &self,
        _: &EnginePlaylistIdentity,
        _: &str,
    ) -> Result<EnginePlaylist, EngineError> {
        unreachable!()
    }
    async fn update(
        &self,
        _: &EnginePlaylistIdentity,
        _: EngineUpdatePlaylist,
    ) -> Result<EnginePlaylist, EngineError> {
        unreachable!()
    }
    async fn delete(&self, _: &EnginePlaylistIdentity, _: &str) -> Result<(), EngineError> {
        unreachable!()
    }
    async fn add_track(
        &self,
        _: &EnginePlaylistIdentity,
        _: &str,
        _: &str,
    ) -> Result<EnginePlaylistTrack, EngineError> {
        unreachable!()
    }
    async fn remove_track(
        &self,
        _: &EnginePlaylistIdentity,
        _: &str,
        _: &str,
    ) -> Result<(), EngineError> {
        unreachable!()
    }
    async fn reorder(
        &self,
        _: &EnginePlaylistIdentity,
        _: &str,
        _: &[String],
        _: u64,
    ) -> Result<EnginePlaylist, EngineError> {
        unreachable!()
    }
    async fn list_tracks(
        &self,
        _: &EnginePlaylistIdentity,
        _: &str,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError> {
        unreachable!()
    }
}

struct PendingPlaybackPort;

#[async_trait]
impl PlaybackPort for PendingPlaybackPort {
    async fn resolve_playback(&self, _: &str) -> Result<EnginePlaybackSource, EngineError> {
        std::future::pending().await
    }
}

struct PendingHistoryPort;

#[async_trait]
impl HistoryPort for PendingHistoryPort {
    async fn get_settings(
        &self,
        _: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError> {
        std::future::pending().await
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

/// Serves `get_by_id` locally so playback can resolve its media item, while
/// catalog searches never resolve.
struct PendingSearchRepository {
    items: Vec<MediaItem>,
}

#[async_trait]
impl MediaRepository for PendingSearchRepository {
    fn get_by_id(&self, id: &str) -> Option<MediaItem> {
        self.items.iter().find(|item| item.id == id).cloned()
    }
    fn get_next(&self, _: &str) -> Option<MediaItem> {
        None
    }
    fn get_previous(&self, _: &str) -> Option<MediaItem> {
        None
    }
    async fn browse(&self, _: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(Vec::new())
    }
    async fn search(&self, _: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(Vec::new())
    }
    async fn search_catalog(
        &self,
        _: &str,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<MediaItem>, EngineError> {
        std::future::pending().await
    }
}

/// Engine wired so every splittable command actually splits.
fn split_engine(media_ids: &[&str]) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_repository(Box::new(PendingSearchRepository {
        items: media_ids.iter().map(|id| media_item(id)).collect(),
    }));
    engine.set_account_port(Arc::new(PendingAccountPort));
    engine.set_playlist_port(Arc::new(PendingPlaylistPort));
    engine.set_playback_port(Arc::new(PendingPlaybackPort));
    engine.set_history_port(Arc::new(PendingHistoryPort));
    engine
}

fn engine_with_blocking_history() -> (Engine, Arc<BlockingHistoryPort>) {
    let auth = Arc::new(MutableAuth(Mutex::new(auth_state(
        "account-1",
        "session-1",
    ))));
    let port = Arc::new(BlockingHistoryPort {
        started: Notify::new(),
        release: Notify::new(),
    });
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_history_port(port.clone());
    (engine, port)
}
