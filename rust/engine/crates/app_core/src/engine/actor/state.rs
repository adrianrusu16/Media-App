use std::collections::{HashMap, HashSet};
use std::panic::AssertUnwindSafe;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, RwLock};
use std::time::Duration;

use futures_util::FutureExt;
use tokio::sync::{mpsc, oneshot, watch};
use tokio::time::{Instant, MissedTickBehavior};

use crate::engine::core::{Engine, EngineOutcome};
use crate::model::auth::AuthState;
use crate::model::command::EngineCommandType;
use crate::model::effect::EngineEffect;
use crate::model::event::EngineEvent;
use crate::model::platform_event::EnginePlatformEvent;
use crate::model::snapshot::EngineSnapshot;

use super::handle::{CommandMessage, EngineActorHandle, EngineInput, LifecycleMessage};
use super::ids::{
    AccountGeneration, CommandId, HistoryGeneration, MessageSequence, OperationId,
    PlaybackInstanceId, PlaylistGeneration, SearchGeneration, SnapshotRevision,
};
use super::operation::{
    DomainGenerations, EngineOperation, EngineOperationCompletion, EngineOperationRequest,
};
use super::protocol::{
    ActorConfig, ActorControl, ActorEvent, ActorFailure, ActorLane, ActorOutcome,
    ActorOutcomeStatus, ActorStartError, CancellationReason, PlayerEdgeEvent, PlayerFacts,
    PublishedSnapshot,
};
use super::runtime::{
    ActorAuthStateProvider, ActorExit, ActorTask, EngineActorEventReceiver, EngineActorRuntime,
};
use tracing::{debug, info};

mod operations;

/// Fields of a terminal [`ActorOutcome`], grouped so that emitting one stays a
/// single-argument call across the inline and off-actor completion paths.
pub(super) struct OutcomeParts {
    pub command_id: CommandId,
    pub message_sequence: MessageSequence,
    pub snapshot_revision: SnapshotRevision,
    pub status: ActorOutcomeStatus,
    pub snapshot: EngineSnapshot,
    pub event: Option<EngineEvent>,
    pub effects: Vec<EngineEffect>,
}

struct EngineActorState {
    engine: Engine,
    auth_provider: Arc<ActorAuthStateProvider>,
    latest_snapshot: Arc<RwLock<Arc<PublishedSnapshot>>>,
    event_tx: mpsc::Sender<ActorEvent>,
    command_rx: mpsc::Receiver<CommandMessage>,
    operation_completion_rx: mpsc::Receiver<EngineOperationCompletion>,
    operation_completion_tx: mpsc::Sender<EngineOperationCompletion>,
    player_facts_rx: watch::Receiver<Option<PlayerFacts>>,
    player_edge_rx: mpsc::Receiver<PlayerEdgeEvent>,
    lifecycle_rx: mpsc::Receiver<LifecycleMessage>,
    shutting_down: Arc<AtomicBool>,
    config: ActorConfig,
    generations: DomainGenerations,
    next_message_sequence: u64,
    next_operation_id: u64,
    accepted_commands: Vec<CommandId>,
    accepted_set: HashSet<CommandId>,
    terminal_commands: HashSet<CommandId>,
    command_responses: HashMap<CommandId, oneshot::Sender<ActorOutcome>>,
    pending_operations: HashMap<OperationId, PendingOperation>,
    latest_epoch_millis: u64,
}

/// A launched operation together with everything needed to re-enter the
/// originating command once its remote half completes.
pub(super) struct PendingOperation {
    pub(super) operation: EngineOperation,
    pub(super) command: crate::EngineCommand,
    pub(super) now_epoch_millis: u64,
}

impl EngineActorState {
    async fn run(mut self) -> Result<ActorExit, ActorFailure> {
        let tick_interval = self.config.clock.tick_interval;
        let mut next_tick_epoch_millis = self
            .config
            .clock
            .start_epoch_millis
            .saturating_add(duration_millis(tick_interval));
        let mut ticker = tokio::time::interval_at(Instant::now() + tick_interval, tick_interval);
        ticker.set_missed_tick_behavior(MissedTickBehavior::Delay);

        loop {
            if self.command_rx.is_closed() && !self.shutting_down.load(Ordering::Acquire) {
                self.drain_queued_commands();
                self.fail_all(ActorFailure::CommandChannelClosed).await;
                return Err(ActorFailure::CommandChannelClosed);
            }

            while let Ok(message) = self.lifecycle_rx.try_recv() {
                if let Some(exit) = self.process_lifecycle(message).await? {
                    return Ok(exit);
                }
            }

            tokio::select! {
                biased;

                lifecycle = self.lifecycle_rx.recv() => {
                    if let Some(message) = lifecycle
                        && let Some(exit) = self.process_lifecycle(message).await?
                    {
                        return Ok(exit);
                    }
                }
                edge = self.player_edge_rx.recv() => {
                    if let Some(edge) = edge {
                        self.process_player_edge(edge).await;
                    }
                }
                completion = self.operation_completion_rx.recv() => {
                    if let Some(completion) = completion {
                        self.process_operation_completion(completion).await;
                    }
                }
                changed = self.player_facts_rx.changed() => {
                    if changed.is_ok() {
                        let facts = *self.player_facts_rx.borrow_and_update();
                        if let Some(facts) = facts {
                            self.process_player_facts(facts).await;
                        }
                    }
                }
                _ = ticker.tick() => {
                    let now_epoch_millis = next_tick_epoch_millis;
                    next_tick_epoch_millis =
                        next_tick_epoch_millis.saturating_add(duration_millis(tick_interval));
                    self.process_tick(now_epoch_millis).await;
                }
                command = self.command_rx.recv(), if !self.shutting_down.load(Ordering::Acquire) => {
                    match command {
                        Some(command) => self.process_command(command).await?,
                        None => {
                            self.fail_all(ActorFailure::CommandChannelClosed).await;
                            return Err(ActorFailure::CommandChannelClosed);
                        }
                    }
                }
            }
        }
    }

    async fn process_lifecycle(
        &mut self,
        message: LifecycleMessage,
    ) -> Result<Option<ActorExit>, ActorFailure> {
        match message {
            LifecycleMessage::Control(control) => {
                let ActorControl::AuthStateChanged(state) = control.clone();
                let previous = self.auth_provider.get();
                self.auth_provider.set(state);
                if self.auth_provider.get() != previous {
                    self.bump_identity_generations();
                }
                self.engine.sync_auth_state_projection();
                let sequence = self.next_sequence();
                let snapshot_revision = self.publish_snapshot(self.engine.snapshot(), sequence);
                let auth_kind = match self.auth_provider.get() {
                    AuthState::Anonymous => "anonymous",
                    AuthState::Authenticated { .. } => "authenticated",
                    AuthState::LoginRequired => "login_required",
                };
                info!(
                    auth_state = auth_kind,
                    snapshot_revision = snapshot_revision.get(),
                    "engine.auth.snapshot_published"
                );
                self.send_event(ActorEvent::ControlApplied {
                    control,
                    message_sequence: sequence,
                    snapshot_revision,
                })
                .await;
                Ok(None)
            }
            LifecycleMessage::ManualTick {
                now_epoch_millis,
                response,
            } => {
                let outcomes = self.process_tick(now_epoch_millis).await;
                let _ = response.send(outcomes);
                Ok(None)
            }
            LifecycleMessage::MutateEngine { mutation, response } => {
                let result = mutation(&mut self.engine);
                let sequence = self.next_sequence();
                let snapshot_revision = self.publish_snapshot(self.engine.snapshot(), sequence);
                let _ = response.send(result);
                self.send_event(ActorEvent::ControlApplied {
                    control: ActorControl::AuthStateChanged(self.auth_provider.get()),
                    message_sequence: sequence,
                    snapshot_revision,
                })
                .await;
                Ok(None)
            }
            LifecycleMessage::Shutdown => {
                self.shutting_down.store(true, Ordering::Release);
                self.drain_queued_commands();
                let sequence = self.next_sequence();
                self.send_event(ActorEvent::ShutdownStarted {
                    message_sequence: sequence,
                })
                .await;

                let accepted = self.accepted_commands.clone();
                for command_id in accepted.iter().copied() {
                    if !self.terminal_commands.contains(&command_id) {
                        let sequence = self.next_sequence();
                        let snapshot_revision = self.current_revision();
                        self.emit_outcome(OutcomeParts {
                            command_id,
                            message_sequence: sequence,
                            snapshot_revision,
                            status: ActorOutcomeStatus::Cancelled(
                                CancellationReason::ShutdownRequested,
                            ),
                            snapshot: self.current_snapshot(),
                            event: None,
                            effects: Vec::new(),
                        })
                        .await;
                    }
                }

                Ok(Some(ActorExit {
                    terminal_commands: self.accepted_commands.clone(),
                }))
            }
        }
    }

    async fn process_command(&mut self, message: CommandMessage) -> Result<(), ActorFailure> {
        let CommandMessage {
            command_id,
            input,
            now_epoch_millis,
            response,
        } = message;
        self.observe_clock(now_epoch_millis);
        self.accept_command(command_id, response);

        let command = match input {
            EngineInput::Command(command) => command,
            EngineInput::PlatformEvent(event) => {
                return self
                    .process_platform_event(command_id, event, now_epoch_millis)
                    .await;
            }
        };

        info!(
            command_id = command_id.get(),
            command_type = command.command_type.as_wire(),
            media_id = match &command.command_type {
                EngineCommandType::PlayMediaById { media_id } => Some(media_id.as_str()),
                _ => None,
            },
            queue_len = match &command.command_type {
                EngineCommandType::PlayQueue { media_ids, .. } => Some(media_ids.len()),
                _ => None,
            },
            start_index = match &command.command_type {
                EngineCommandType::PlayQueue { start_index, .. } => Some(*start_index),
                _ => None,
            },
            "engine.command.accepted"
        );

        if self.config.split_remote_operations
            && let Some(operation) = self.prepare_operation(command_id, &command, now_epoch_millis)
            && self.spawn_operation_worker(&operation)
        {
            info!(
                command_id = command_id.get(),
                command_type = command.command_type.as_wire(),
                operation_id = operation.operation_id.get(),
                request = match &operation.request {
                    EngineOperationRequest::AccountProjection { .. } => "account_projection",
                    EngineOperationRequest::SearchPage { .. } => "search_page",
                    EngineOperationRequest::PlaylistPage { .. } => "playlist_page",
                    EngineOperationRequest::HistorySettings { .. } => "history_settings",
                    EngineOperationRequest::PlaybackResolution { .. } => "playback_resolution",
                },
                media_id = match &operation.request {
                    EngineOperationRequest::PlaybackResolution { media_id } =>
                        Some(media_id.as_str()),
                    _ => None,
                },
                "engine.operation.launch"
            );
            self.pending_operations.insert(
                operation.operation_id,
                PendingOperation {
                    operation: operation.clone(),
                    command,
                    now_epoch_millis,
                },
            );
            let sequence = self.next_sequence();
            self.send_event(ActorEvent::OperationLaunched {
                operation,
                message_sequence: sequence,
            })
            .await;
            return Ok(());
        }

        let outcome = AssertUnwindSafe(self.engine.dispatch(command, now_epoch_millis))
            .catch_unwind()
            .await;

        match outcome {
            Ok(outcome) => {
                let sequence = self.next_sequence();
                let snapshot_revision = self.publish_snapshot(outcome.snapshot.clone(), sequence);
                self.emit_outcome(OutcomeParts {
                    command_id,
                    message_sequence: sequence,
                    snapshot_revision,
                    status: ActorOutcomeStatus::Completed,
                    snapshot: outcome.snapshot,
                    event: Some(outcome.event),
                    effects: outcome.effects,
                })
                .await;
                Ok(())
            }
            Err(_) => {
                self.fail_all(ActorFailure::Panicked).await;
                Err(ActorFailure::Panicked)
            }
        }
    }

    async fn process_platform_event(
        &mut self,
        command_id: CommandId,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> Result<(), ActorFailure> {
        let outcome =
            AssertUnwindSafe(self.engine.dispatch_platform_event(event, now_epoch_millis))
                .catch_unwind()
                .await;

        match outcome {
            Ok(outcome) => {
                let sequence = self.next_sequence();
                let snapshot_revision = self.publish_snapshot(outcome.snapshot.clone(), sequence);
                self.emit_outcome(OutcomeParts {
                    command_id,
                    message_sequence: sequence,
                    snapshot_revision,
                    status: ActorOutcomeStatus::Completed,
                    snapshot: outcome.snapshot,
                    event: Some(outcome.event),
                    effects: outcome.effects,
                })
                .await;
                Ok(())
            }
            Err(_) => {
                self.fail_all(ActorFailure::Panicked).await;
                Err(ActorFailure::Panicked)
            }
        }
    }

    async fn process_player_edge(&mut self, event: PlayerEdgeEvent) {
        let sequence = self.next_sequence();
        self.send_event(ActorEvent::PlayerEdgeApplied {
            event,
            message_sequence: sequence,
        })
        .await;
    }

    async fn process_player_facts(&mut self, facts: PlayerFacts) {
        let sequence = self.next_sequence();
        self.send_event(ActorEvent::PlayerFactsApplied {
            facts,
            message_sequence: sequence,
        })
        .await;
    }

    async fn process_tick(&mut self, now_epoch_millis: u64) -> Vec<EngineOutcome> {
        self.observe_clock(now_epoch_millis);
        let outcomes = self.engine.tick(now_epoch_millis).await;
        let snapshot = outcomes
            .last()
            .map(|outcome| outcome.snapshot.clone())
            .unwrap_or_else(|| self.engine.snapshot());
        let sequence = self.next_sequence();
        let snapshot_revision = self.publish_snapshot(snapshot, sequence);
        self.send_event(ActorEvent::TickProcessed {
            now_epoch_millis,
            message_sequence: sequence,
            snapshot_revision,
        })
        .await;
        outcomes
    }

    async fn fail_all(&mut self, failure: ActorFailure) {
        self.drain_queued_commands();
        let accepted = self.accepted_commands.clone();
        for command_id in accepted {
            if !self.terminal_commands.contains(&command_id) {
                let sequence = self.next_sequence();
                let snapshot_revision = self.current_revision();
                self.emit_outcome(OutcomeParts {
                    command_id,
                    message_sequence: sequence,
                    snapshot_revision,
                    status: ActorOutcomeStatus::Failed(failure),
                    snapshot: self.current_snapshot(),
                    event: None,
                    effects: Vec::new(),
                })
                .await;
            }
        }
    }

    fn drain_queued_commands(&mut self) {
        while let Ok(message) = self.command_rx.try_recv() {
            self.accept_command(message.command_id, message.response);
        }
    }

    fn accept_command(
        &mut self,
        command_id: CommandId,
        response: Option<oneshot::Sender<ActorOutcome>>,
    ) {
        if self.accepted_set.insert(command_id) {
            self.accepted_commands.push(command_id);
            if let Some(response) = response {
                self.command_responses.insert(command_id, response);
            }
        }
    }

    async fn emit_outcome(&mut self, parts: OutcomeParts) {
        let OutcomeParts {
            command_id,
            message_sequence,
            snapshot_revision,
            status,
            snapshot,
            event,
            effects,
        } = parts;
        self.terminal_commands.insert(command_id);
        if effects.is_empty() {
            debug!(
                command_id = command_id.get(),
                status = ?status,
                snapshot_revision = snapshot_revision.get(),
                "engine.command.complete"
            );
        } else {
            let effect_names = effects
                .iter()
                .map(EngineEffect::as_wire)
                .collect::<Vec<_>>()
                .join(",");
            info!(
                command_id = command_id.get(),
                effects = %effect_names,
                "engine.effect.emit"
            );
            info!(
                command_id = command_id.get(),
                status = ?status,
                snapshot_revision = snapshot_revision.get(),
                effect_count = effects.len(),
                "engine.command.complete"
            );
        }
        let outcome = ActorOutcome {
            command_id,
            message_sequence,
            snapshot_revision,
            status,
            snapshot: Arc::new(snapshot),
            event,
            effects,
        };
        if let Some(response) = self.command_responses.remove(&command_id) {
            let _ = response.send(outcome.clone());
        }
        self.send_event(ActorEvent::CommandOutcome(outcome)).await;
    }

    async fn send_event(&self, event: ActorEvent) {
        let _ = self.event_tx.send(event).await;
    }

    fn publish_snapshot(
        &self,
        snapshot: EngineSnapshot,
        message_sequence: MessageSequence,
    ) -> SnapshotRevision {
        let mut latest = self
            .latest_snapshot
            .write()
            .expect("latest actor snapshot lock poisoned");
        if snapshots_equal_for_revision(latest.snapshot.as_ref(), &snapshot) {
            return latest.revision;
        }

        let revision = SnapshotRevision::new(latest.revision.get().saturating_add(1));
        *latest = Arc::new(PublishedSnapshot {
            revision,
            message_sequence,
            snapshot: Arc::new(snapshot),
        });
        revision
    }

    fn current_snapshot(&self) -> EngineSnapshot {
        self.latest_snapshot().snapshot.as_ref().clone()
    }

    fn current_revision(&self) -> SnapshotRevision {
        self.latest_snapshot().revision
    }

    fn latest_snapshot(&self) -> Arc<PublishedSnapshot> {
        Arc::clone(
            &self
                .latest_snapshot
                .read()
                .expect("latest actor snapshot lock poisoned"),
        )
    }

    fn next_sequence(&mut self) -> MessageSequence {
        self.next_message_sequence = self.next_message_sequence.saturating_add(1);
        MessageSequence::new(self.next_message_sequence)
    }

    fn bump_identity_generations(&mut self) {
        self.generations.account =
            AccountGeneration::new(self.generations.account.get().saturating_add(1));
        self.generations.history =
            HistoryGeneration::new(self.generations.history.get().saturating_add(1));
        self.generations.playlist =
            PlaylistGeneration::new(self.generations.playlist.get().saturating_add(1));
        self.generations.search =
            SearchGeneration::new(self.generations.search.get().saturating_add(1));
        self.generations.playback =
            PlaybackInstanceId::new(self.generations.playback.get().saturating_add(1));
    }

    fn bump_search_generation(&mut self) -> SearchGeneration {
        self.generations.search =
            SearchGeneration::new(self.generations.search.get().saturating_add(1));
        self.generations.search
    }

    fn bump_playlist_generation(&mut self) -> PlaylistGeneration {
        self.generations.playlist =
            PlaylistGeneration::new(self.generations.playlist.get().saturating_add(1));
        self.generations.playlist
    }

    fn bump_history_generation(&mut self) -> HistoryGeneration {
        self.generations.history =
            HistoryGeneration::new(self.generations.history.get().saturating_add(1));
        self.generations.history
    }

    fn bump_playback_generation(&mut self) -> PlaybackInstanceId {
        self.generations.playback =
            PlaybackInstanceId::new(self.generations.playback.get().saturating_add(1));
        self.generations.playback
    }

    fn observe_clock(&mut self, now_epoch_millis: u64) {
        if now_epoch_millis > self.latest_epoch_millis {
            self.latest_epoch_millis = now_epoch_millis;
        }
    }
}

pub struct EngineActor;

impl EngineActor {
    pub fn spawn(
        mut engine: Engine,
        config: ActorConfig,
    ) -> Result<EngineActorRuntime, ActorStartError> {
        for (capacity, lane) in [
            (config.command_capacity, ActorLane::Command),
            (
                config.operation_completion_capacity,
                ActorLane::OperationCompletion,
            ),
            (config.player_edge_capacity, ActorLane::PlayerEdge),
            (config.lifecycle_capacity, ActorLane::Lifecycle),
            (config.outcome_capacity, ActorLane::Outcome),
        ] {
            if capacity == 0 {
                return Err(ActorStartError::InvalidCapacity(lane));
            }
        }
        if config.clock.tick_interval.is_zero() {
            return Err(ActorStartError::InvalidTickInterval);
        }

        let runtime = tokio::runtime::Handle::try_current()
            .map_err(|_| ActorStartError::RuntimeNotImplemented)?;

        let initial_auth_state = engine.snapshot().auth_state;
        let auth_provider = Arc::new(ActorAuthStateProvider::new(initial_auth_state));
        engine.set_auth_state_provider(auth_provider.clone());
        engine.sync_auth_state_projection();

        let latest_snapshot = Arc::new(RwLock::new(Arc::new(PublishedSnapshot {
            revision: SnapshotRevision::new(0),
            message_sequence: MessageSequence::new(0),
            snapshot: Arc::new(engine.snapshot()),
        })));
        let shutting_down = Arc::new(AtomicBool::new(false));
        let next_command_id = Arc::new(AtomicU64::new(1));

        let (command_tx, command_rx) = mpsc::channel(config.command_capacity);
        let (operation_completion_tx, operation_completion_rx) =
            mpsc::channel(config.operation_completion_capacity);
        let (player_edge_tx, player_edge_rx) = mpsc::channel(config.player_edge_capacity);
        let (lifecycle_tx, lifecycle_rx) = mpsc::channel(config.lifecycle_capacity);
        let (event_tx, event_rx) = mpsc::channel(config.outcome_capacity);
        let (player_facts_tx, player_facts_rx) = watch::channel(None);

        let handle = EngineActorHandle {
            latest_snapshot: latest_snapshot.clone(),
            command_tx,
            operation_completion_tx: operation_completion_tx.clone(),
            player_facts_tx,
            player_edge_tx,
            lifecycle_tx,
            shutting_down: shutting_down.clone(),
            next_command_id,
            config,
        };
        let state = EngineActorState {
            engine,
            auth_provider,
            latest_snapshot,
            event_tx,
            command_rx,
            operation_completion_rx,
            operation_completion_tx,
            player_facts_rx,
            player_edge_rx,
            lifecycle_rx,
            shutting_down,
            config,
            generations: DomainGenerations::default(),
            next_message_sequence: 0,
            next_operation_id: 1,
            accepted_commands: Vec::new(),
            accepted_set: HashSet::new(),
            terminal_commands: HashSet::new(),
            command_responses: HashMap::new(),
            pending_operations: HashMap::new(),
            latest_epoch_millis: config.clock.start_epoch_millis,
        };
        let join = runtime.spawn(state.run());

        Ok(EngineActorRuntime::new(
            handle,
            EngineActorEventReceiver::new(event_rx),
            ActorTask::new(join),
        ))
    }
}

fn duration_millis(duration: Duration) -> u64 {
    u64::try_from(duration.as_millis()).unwrap_or(u64::MAX)
}

fn snapshots_equal_for_revision(left: &EngineSnapshot, right: &EngineSnapshot) -> bool {
    if left == right {
        return true;
    }

    let mut left = left.clone();
    let mut right = right.clone();
    left.updated_at_epoch_millis = 0;
    right.updated_at_epoch_millis = 0;
    left == right
}

fn account_identity(auth_state: &AuthState) -> Option<crate::EngineAccountIdentity> {
    match auth_state {
        AuthState::Authenticated { account, session } => Some(crate::EngineAccountIdentity {
            account_id: account.id.clone(),
            session_id: session.id.clone(),
        }),
        AuthState::Anonymous | AuthState::LoginRequired => None,
    }
}

fn playlist_identity(auth_state: &AuthState) -> Option<crate::EnginePlaylistIdentity> {
    let identity = account_identity(auth_state)?;
    crate::EnginePlaylistIdentity::new(identity.account_id, identity.session_id).ok()
}

fn history_identity(auth_state: &AuthState) -> Option<crate::EngineHistoryIdentity> {
    let identity = account_identity(auth_state)?;
    crate::EngineHistoryIdentity::new(identity.account_id, identity.session_id).ok()
}
