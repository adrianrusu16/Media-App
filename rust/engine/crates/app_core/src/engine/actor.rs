use std::any::Any;
use std::collections::{HashMap, HashSet};
use std::panic::AssertUnwindSafe;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, RwLock};
use std::time::Duration;

use futures_util::FutureExt;
use tokio::sync::{mpsc, oneshot, watch};
use tokio::task::JoinHandle;
use tokio::time::{Instant, MissedTickBehavior};

use crate::AuthStateProvider;
use crate::engine::core::{Engine, EngineOutcome};
use crate::model::auth::AuthState;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::effect::EngineEffect;
use crate::model::event::EngineEvent;
use crate::model::platform_event::EnginePlatformEvent;
use crate::model::snapshot::EngineSnapshot;

macro_rules! monotonic_id {
    ($(#[$meta:meta])* $name:ident) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
        pub struct $name(u64);

        impl $name {
            pub const fn new(value: u64) -> Self {
                Self(value)
            }

            pub const fn get(self) -> u64 {
                self.0
            }
        }
    };
}

monotonic_id!(
    /// Sequence assigned to every message processed by the actor.
    ///
    /// This is deliberately not interchangeable with snapshot revisions or domain
    /// generations:
    ///
    /// ```compile_fail
    /// use panda_engine_core::engine::actor::{MessageSequence, SnapshotRevision};
    /// let revision: SnapshotRevision = MessageSequence::new(1);
    /// ```
    MessageSequence
);

monotonic_id!(
    /// Revision assigned only when a newly published snapshot differs from the
    /// previously published snapshot.
    SnapshotRevision
);

monotonic_id!(
    /// Correlation identifier returned immediately for an accepted command.
    CommandId
);

monotonic_id!(
    /// Correlation identifier for asynchronous internal engine work.
    OperationId
);

monotonic_id!(
    /// Generation invalidated by any account or authenticated-session replacement.
    ///
    /// ```compile_fail
    /// use panda_engine_core::engine::actor::{AccountGeneration, SearchGeneration};
    /// let search: SearchGeneration = AccountGeneration::new(1);
    /// ```
    AccountGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding search or search-page lineage.
    SearchGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding playlist read or mutation.
    PlaylistGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding history read or mutation.
    HistoryGeneration
);

monotonic_id!(
    /// Identity of one logical playback source-resolution attempt.
    PlaybackInstanceId
);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OperationGeneration {
    Account(AccountGeneration),
    Search(SearchGeneration),
    Playlist(PlaylistGeneration),
    History(HistoryGeneration),
    Playback(PlaybackInstanceId),
}

/// Immutable request data for work performed outside the state-owning actor.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EngineOperationRequest {
    AccountProjection {
        identity: crate::EngineAccountIdentity,
    },
    SearchPage {
        query: String,
        page: crate::EnginePageRequest,
        catalog_operation_id: Option<String>,
    },
    PlaylistPage {
        identity: crate::EnginePlaylistIdentity,
        page: crate::EnginePageRequest,
    },
    HistorySettings {
        identity: crate::EngineHistoryIdentity,
    },
    PlaybackResolution {
        media_id: String,
    },
}

/// Internal asynchronous work envelope. A worker receives this immutable value
/// rather than `Engine` or any engine lock.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineOperation {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub request: EngineOperationRequest,
}

#[derive(Clone, Debug, PartialEq)]
pub enum EngineOperationResult {
    AccountProjection(crate::Account),
    SearchPage {
        catalog_operation_id: String,
        items: Vec<crate::MediaItem>,
        next_page_token: Option<crate::EnginePageToken>,
    },
    PlaylistPage {
        playlists: Vec<crate::EnginePlaylist>,
        next_page_token: Option<crate::EnginePageToken>,
    },
    HistorySettings(crate::EngineHistorySettings),
    PlaybackResolved(crate::EnginePlaybackSource),
}

/// Typed completion returned through the reliable completion ingress. The
/// generation captured at launch remains attached through validation.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineOperationCompletion {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub result: Result<EngineOperationResult, crate::model::error::EngineError>,
}

impl EngineOperation {
    pub fn completion(
        &self,
        result: Result<EngineOperationResult, crate::model::error::EngineError>,
    ) -> EngineOperationCompletion {
        EngineOperationCompletion {
            operation_id: self.operation_id,
            command_id: self.command_id,
            generation: self.generation,
            result,
        }
    }
}

/// Actor-owned generation snapshot used to reject late operation completions.
/// Snapshot revision is intentionally absent because it is never a stale-result
/// correctness token.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct DomainGenerations {
    pub account: AccountGeneration,
    pub search: SearchGeneration,
    pub playlist: PlaylistGeneration,
    pub history: HistoryGeneration,
    pub playback: PlaybackInstanceId,
}

impl DomainGenerations {
    pub fn is_current(self, candidate: OperationGeneration) -> bool {
        match candidate {
            OperationGeneration::Account(generation) => self.account == generation,
            OperationGeneration::Search(generation) => self.search == generation,
            OperationGeneration::Playlist(generation) => self.playlist == generation,
            OperationGeneration::History(generation) => self.history == generation,
            OperationGeneration::Playback(generation) => self.playback == generation,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct PublishedSnapshot {
    pub revision: SnapshotRevision,
    pub message_sequence: MessageSequence,
    pub snapshot: Arc<EngineSnapshot>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PlayerFacts {
    pub playback_instance_id: PlaybackInstanceId,
    pub position_millis: u64,
    pub buffered_position_millis: u64,
    pub play_when_ready: bool,
    pub is_playing: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PlayerEdgeEvent {
    Ended {
        playback_instance_id: PlaybackInstanceId,
    },
    SourceRejected {
        playback_instance_id: PlaybackInstanceId,
    },
    DecoderFailed {
        playback_instance_id: PlaybackInstanceId,
        error_code: i32,
    },
    PlayerFailed {
        playback_instance_id: PlaybackInstanceId,
        error_code: i32,
    },
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ActorLane {
    Command,
    OperationCompletion,
    PlayerFacts,
    PlayerEdge,
    Lifecycle,
    Outcome,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CancellationReason {
    ShutdownRequested,
    Superseded,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ActorFailure {
    Panicked,
    CommandChannelClosed,
    RuntimeUnavailable,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ActorOutcomeStatus {
    Completed,
    Cancelled(CancellationReason),
    Failed(ActorFailure),
}

#[derive(Clone, Debug, PartialEq)]
pub struct ActorOutcome {
    pub command_id: CommandId,
    pub message_sequence: MessageSequence,
    pub snapshot_revision: SnapshotRevision,
    pub status: ActorOutcomeStatus,
    pub snapshot: Arc<EngineSnapshot>,
    pub event: Option<EngineEvent>,
    pub effects: Vec<EngineEffect>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum ActorEvent {
    /// Terminal outcomes are ordered by actor processing/terminal readiness,
    /// not by command acceptance. This permits unrelated commands and later
    /// operations to complete while earlier remote work is pending. Effects
    /// inside one outcome retain reducer order.
    CommandOutcome(ActorOutcome),
    OperationLaunched {
        operation: EngineOperation,
        message_sequence: MessageSequence,
    },
    PlayerFactsApplied {
        facts: PlayerFacts,
        message_sequence: MessageSequence,
    },
    PlayerEdgeApplied {
        event: PlayerEdgeEvent,
        message_sequence: MessageSequence,
    },
    ControlApplied {
        control: ActorControl,
        message_sequence: MessageSequence,
        snapshot_revision: SnapshotRevision,
    },
    TickProcessed {
        now_epoch_millis: u64,
        message_sequence: MessageSequence,
        snapshot_revision: SnapshotRevision,
    },
    ShutdownStarted {
        message_sequence: MessageSequence,
    },
}

#[derive(Clone, Debug, PartialEq)]
pub enum ActorControl {
    AuthStateChanged(crate::AuthState),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SubmissionError {
    MailboxFull { lane: ActorLane, capacity: usize },
    ShuttingDown,
    ChannelClosed { lane: ActorLane },
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ActorStartError {
    InvalidCapacity(ActorLane),
    InvalidTickInterval,
    RuntimeNotImplemented,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ActorClockConfig {
    /// Epoch corresponding to actor start. Scheduled ticks derive their times
    /// from this value rather than accepting caller-supplied tick timestamps.
    pub start_epoch_millis: u64,
    pub tick_interval: Duration,
}

impl Default for ActorClockConfig {
    fn default() -> Self {
        Self {
            start_epoch_millis: 0,
            tick_interval: Duration::from_secs(1),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ActorConfig {
    pub command_capacity: usize,
    pub operation_completion_capacity: usize,
    pub player_edge_capacity: usize,
    pub lifecycle_capacity: usize,
    pub outcome_capacity: usize,
    pub clock: ActorClockConfig,
    pub split_remote_operations: bool,
}

impl Default for ActorConfig {
    fn default() -> Self {
        Self {
            command_capacity: 64,
            operation_completion_capacity: 64,
            player_edge_capacity: 32,
            lifecycle_capacity: 4,
            outcome_capacity: 128,
            clock: ActorClockConfig::default(),
            split_remote_operations: true,
        }
    }
}

/// Cloneable synchronous boundary intended for JNI and Binder callers. None of
/// its methods wait for actor work or expose mutable `Engine` access.
#[derive(Clone, Debug)]
pub struct EngineActorHandle {
    latest_snapshot: Arc<RwLock<Arc<PublishedSnapshot>>>,
    command_tx: mpsc::Sender<CommandMessage>,
    operation_completion_tx: mpsc::Sender<EngineOperationCompletion>,
    player_facts_tx: watch::Sender<Option<PlayerFacts>>,
    player_edge_tx: mpsc::Sender<PlayerEdgeEvent>,
    lifecycle_tx: mpsc::Sender<LifecycleMessage>,
    shutting_down: Arc<AtomicBool>,
    next_command_id: Arc<AtomicU64>,
    config: ActorConfig,
}

impl EngineActorHandle {
    pub fn try_submit(
        &self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> Result<CommandId, SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }

        let command_id = CommandId::new(self.next_command_id.fetch_add(1, Ordering::Relaxed));
        let message = CommandMessage {
            command_id,
            input: EngineInput::Command(command),
            now_epoch_millis,
            response: None,
        };
        self.command_tx
            .try_send(message)
            .map(|_| command_id)
            .map_err(|error| {
                map_try_send_error(error, ActorLane::Command, self.config.command_capacity)
            })
    }

    pub async fn submit_and_wait(
        &self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> Result<ActorOutcome, SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }

        let command_id = CommandId::new(self.next_command_id.fetch_add(1, Ordering::Relaxed));
        let (response_tx, response_rx) = oneshot::channel();
        let message = CommandMessage {
            command_id,
            input: EngineInput::Command(command),
            now_epoch_millis,
            response: Some(response_tx),
        };
        self.command_tx.try_send(message).map_err(|error| {
            map_try_send_error(error, ActorLane::Command, self.config.command_capacity)
        })?;
        response_rx
            .await
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::Outcome,
            })
    }

    pub async fn submit_platform_event_and_wait(
        &self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> Result<ActorOutcome, SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }

        let command_id = CommandId::new(self.next_command_id.fetch_add(1, Ordering::Relaxed));
        let (response_tx, response_rx) = oneshot::channel();
        let message = CommandMessage {
            command_id,
            input: EngineInput::PlatformEvent(event),
            now_epoch_millis,
            response: Some(response_tx),
        };
        self.command_tx.try_send(message).map_err(|error| {
            map_try_send_error(error, ActorLane::Command, self.config.command_capacity)
        })?;
        response_rx
            .await
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::Outcome,
            })
    }

    pub fn latest_snapshot(&self) -> Arc<PublishedSnapshot> {
        Arc::clone(
            &self
                .latest_snapshot
                .read()
                .expect("latest actor snapshot lock poisoned"),
        )
    }

    pub fn publish_player_facts(&self, facts: PlayerFacts) -> Result<(), SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }
        self.player_facts_tx
            .send(Some(facts))
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::PlayerFacts,
            })
    }

    pub fn try_publish_player_edge(&self, event: PlayerEdgeEvent) -> Result<(), SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }
        self.player_edge_tx.try_send(event).map_err(|error| {
            map_try_send_error(
                error,
                ActorLane::PlayerEdge,
                self.config.player_edge_capacity,
            )
        })
    }

    pub fn try_complete_operation(
        &self,
        completion: EngineOperationCompletion,
    ) -> Result<(), SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }
        self.operation_completion_tx
            .try_send(completion)
            .map_err(|error| {
                map_try_send_error(
                    error,
                    ActorLane::OperationCompletion,
                    self.config.operation_completion_capacity,
                )
            })
    }

    pub fn try_send_control(&self, control: ActorControl) -> Result<(), SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }
        self.lifecycle_tx
            .try_send(LifecycleMessage::Control(control))
            .map_err(|error| {
                map_try_send_error(error, ActorLane::Lifecycle, self.config.lifecycle_capacity)
            })
    }

    pub fn request_shutdown(&self) -> Result<(), SubmissionError> {
        if self.shutting_down.load(Ordering::Acquire) {
            return Err(SubmissionError::ShuttingDown);
        }
        self.lifecycle_tx
            .try_send(LifecycleMessage::Shutdown)
            .map_err(|error| {
                map_try_send_error(error, ActorLane::Lifecycle, self.config.lifecycle_capacity)
            })?;
        self.shutting_down.store(true, Ordering::Release);
        Ok(())
    }

    pub async fn tick_now(
        &self,
        now_epoch_millis: u64,
    ) -> Result<Vec<EngineOutcome>, SubmissionError> {
        let (response_tx, response_rx) = oneshot::channel();
        self.lifecycle_tx
            .try_send(LifecycleMessage::ManualTick {
                now_epoch_millis,
                response: response_tx,
            })
            .map_err(|error| {
                map_try_send_error(error, ActorLane::Lifecycle, self.config.lifecycle_capacity)
            })?;
        response_rx
            .await
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::Outcome,
            })
    }

    pub async fn mutate_engine<T, F>(&self, mutation: F) -> Result<T, SubmissionError>
    where
        T: Send + 'static,
        F: FnOnce(&mut Engine) -> T + Send + 'static,
    {
        let (response_tx, response_rx) = oneshot::channel();
        let mutation = Box::new(move |engine: &mut Engine| -> Box<dyn Any + Send> {
            Box::new(mutation(engine))
        });
        self.lifecycle_tx
            .try_send(LifecycleMessage::MutateEngine {
                mutation,
                response: response_tx,
            })
            .map_err(|error| {
                map_try_send_error(error, ActorLane::Lifecycle, self.config.lifecycle_capacity)
            })?;
        response_rx
            .await
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::Outcome,
            })?
            .downcast::<T>()
            .map(|value| *value)
            .map_err(|_| SubmissionError::ChannelClosed {
                lane: ActorLane::Outcome,
            })
    }
}

fn map_try_send_error<T>(
    error: mpsc::error::TrySendError<T>,
    lane: ActorLane,
    capacity: usize,
) -> SubmissionError {
    match error {
        mpsc::error::TrySendError::Full(_) => SubmissionError::MailboxFull { lane, capacity },
        mpsc::error::TrySendError::Closed(_) => SubmissionError::ChannelClosed { lane },
    }
}

#[derive(Debug)]
struct CommandMessage {
    command_id: CommandId,
    input: EngineInput,
    now_epoch_millis: u64,
    response: Option<oneshot::Sender<ActorOutcome>>,
}

#[derive(Debug)]
enum EngineInput {
    Command(EngineCommand),
    PlatformEvent(EnginePlatformEvent),
}

type EngineMutation = Box<dyn FnOnce(&mut Engine) -> Box<dyn Any + Send> + Send>;

enum LifecycleMessage {
    Control(ActorControl),
    ManualTick {
        now_epoch_millis: u64,
        response: oneshot::Sender<Vec<EngineOutcome>>,
    },
    MutateEngine {
        mutation: EngineMutation,
        response: oneshot::Sender<Box<dyn Any + Send>>,
    },
    Shutdown,
}

#[derive(Debug)]
struct ActorAuthStateProvider {
    state: RwLock<AuthState>,
}

impl ActorAuthStateProvider {
    fn new(state: AuthState) -> Self {
        Self {
            state: RwLock::new(state),
        }
    }

    fn get(&self) -> AuthState {
        self.state
            .read()
            .expect("actor auth state lock poisoned")
            .clone()
    }

    fn set(&self, state: AuthState) {
        *self.state.write().expect("actor auth state lock poisoned") = state;
    }
}

impl AuthStateProvider for ActorAuthStateProvider {
    fn current_auth_state(&self) -> AuthState {
        self.get()
    }
}

#[derive(Debug)]
pub struct EngineActorEventReceiver {
    events: mpsc::Receiver<ActorEvent>,
}

impl EngineActorEventReceiver {
    pub async fn recv(&mut self) -> Option<ActorEvent> {
        self.events.recv().await
    }

    pub fn try_recv(&mut self) -> Option<ActorEvent> {
        match self.events.try_recv() {
            Ok(event) => Some(event),
            Err(mpsc::error::TryRecvError::Empty | mpsc::error::TryRecvError::Disconnected) => None,
        }
    }
}

#[derive(Debug)]
pub struct ActorTask {
    join: JoinHandle<Result<ActorExit, ActorFailure>>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ActorExit {
    /// Every accepted command has exactly one entry before a clean wait returns.
    pub terminal_commands: Vec<CommandId>,
}

impl ActorTask {
    pub async fn wait(self) -> Result<ActorExit, ActorFailure> {
        match self.join.await {
            Ok(result) => result,
            Err(error) if error.is_panic() => Err(ActorFailure::Panicked),
            Err(_) => Err(ActorFailure::RuntimeUnavailable),
        }
    }
}

#[derive(Debug)]
pub struct EngineActorRuntime {
    handle: EngineActorHandle,
    events: EngineActorEventReceiver,
    task: ActorTask,
}

impl EngineActorRuntime {
    pub fn into_parts(self) -> (EngineActorHandle, EngineActorEventReceiver, ActorTask) {
        (self.handle, self.events, self.task)
    }
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
    pending_operations: HashMap<OperationId, EngineOperation>,
    current_catalog_operation_id: Option<String>,
    current_catalog_next_page_token: Option<crate::EnginePageToken>,
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
                    if let Some(message) = lifecycle {
                        if let Some(exit) = self.process_lifecycle(message).await? {
                            return Ok(exit);
                        }
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
                        self.emit_outcome(
                            command_id,
                            sequence,
                            snapshot_revision,
                            ActorOutcomeStatus::Cancelled(CancellationReason::ShutdownRequested),
                            self.current_snapshot(),
                            None,
                            Vec::new(),
                        )
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
        self.accept_command(command_id, response);

        let command = match input {
            EngineInput::Command(command) => command,
            EngineInput::PlatformEvent(event) => {
                return self
                    .process_platform_event(command_id, event, now_epoch_millis)
                    .await;
            }
        };

        if self.config.split_remote_operations
            && let Some(operation) = self.prepare_operation(command_id, &command, now_epoch_millis)
        {
            self.pending_operations
                .insert(operation.operation_id, operation.clone());
            self.spawn_operation_worker(&operation);
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
                self.emit_outcome(
                    command_id,
                    sequence,
                    snapshot_revision,
                    ActorOutcomeStatus::Completed,
                    outcome.snapshot,
                    Some(outcome.event),
                    outcome.effects,
                )
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
                self.emit_outcome(
                    command_id,
                    sequence,
                    snapshot_revision,
                    ActorOutcomeStatus::Completed,
                    outcome.snapshot,
                    Some(outcome.event),
                    outcome.effects,
                )
                .await;
                Ok(())
            }
            Err(_) => {
                self.fail_all(ActorFailure::Panicked).await;
                Err(ActorFailure::Panicked)
            }
        }
    }

    fn prepare_operation(
        &mut self,
        command_id: CommandId,
        command: &EngineCommand,
        _now_epoch_millis: u64,
    ) -> Option<EngineOperation> {
        match &command.command_type {
            EngineCommandType::GetAccount => {
                let identity = account_identity(&self.auth_provider.get())?;
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Account(self.generations.account),
                    EngineOperationRequest::AccountProjection { identity },
                ))
            }
            EngineCommandType::SearchCatalog { query, page } => {
                let generation = self.bump_search_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Search(generation),
                    EngineOperationRequest::SearchPage {
                        query: query.clone(),
                        page: page.clone(),
                        catalog_operation_id: None,
                    },
                ))
            }
            EngineCommandType::LoadNextCatalogPage { operation_id } => {
                let page = crate::EnginePageRequest {
                    page_size: 20,
                    page_token: self.current_catalog_next_page_token.clone(),
                };
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Search(self.generations.search),
                    EngineOperationRequest::SearchPage {
                        query: String::new(),
                        page,
                        catalog_operation_id: Some(operation_id.clone()),
                    },
                ))
            }
            EngineCommandType::ListPlaylists { page } => {
                let identity = playlist_identity(&self.auth_provider.get())?;
                let generation = self.bump_playlist_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Playlist(generation),
                    EngineOperationRequest::PlaylistPage {
                        identity,
                        page: page.clone(),
                    },
                ))
            }
            EngineCommandType::LoadHistorySettings => {
                let identity = history_identity(&self.auth_provider.get())?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistorySettings { identity },
                ))
            }
            EngineCommandType::PlayMediaById { media_id } => {
                let playback = self.bump_playback_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Playback(playback),
                    EngineOperationRequest::PlaybackResolution {
                        media_id: media_id.clone(),
                    },
                ))
            }
            _ => None,
        }
    }

    fn allocate_operation(
        &mut self,
        command_id: CommandId,
        generation: OperationGeneration,
        request: EngineOperationRequest,
    ) -> EngineOperation {
        let operation_id = OperationId::new(self.next_operation_id);
        self.next_operation_id = self.next_operation_id.saturating_add(1);
        EngineOperation {
            operation_id,
            command_id,
            generation,
            request,
        }
    }

    fn spawn_operation_worker(&self, operation: &EngineOperation) {
        let EngineOperationRequest::HistorySettings { identity } = operation.request.clone() else {
            return;
        };
        let Some(port) = self.engine.actor_history_port() else {
            return;
        };
        let operation = operation.clone();
        let completion_tx = self.operation_completion_tx.clone();
        tokio::spawn(async move {
            let result = port
                .get_settings(&identity)
                .await
                .map(EngineOperationResult::HistorySettings);
            let _ = completion_tx.send(operation.completion(result)).await;
        });
    }

    async fn process_operation_completion(&mut self, completion: EngineOperationCompletion) {
        let Some(operation) = self.pending_operations.remove(&completion.operation_id) else {
            return;
        };
        let sequence = self.next_sequence();

        if operation.generation != completion.generation
            || !self.generations.is_current(completion.generation)
        {
            let snapshot_revision = self.current_revision();
            self.emit_outcome(
                completion.command_id,
                sequence,
                snapshot_revision,
                ActorOutcomeStatus::Cancelled(CancellationReason::Superseded),
                self.current_snapshot(),
                None,
                Vec::new(),
            )
            .await;
            return;
        }

        let mut snapshot = self.current_snapshot();
        if let Ok(result) = completion.result {
            self.apply_operation_result(&operation.request, result, &mut snapshot);
        }
        let snapshot_revision = self.publish_snapshot(snapshot.clone(), sequence);
        self.emit_outcome(
            completion.command_id,
            sequence,
            snapshot_revision,
            ActorOutcomeStatus::Completed,
            snapshot,
            None,
            Vec::new(),
        )
        .await;
    }

    fn apply_operation_result(
        &mut self,
        request: &EngineOperationRequest,
        result: EngineOperationResult,
        snapshot: &mut EngineSnapshot,
    ) {
        match (request, result) {
            (
                EngineOperationRequest::AccountProjection { .. },
                EngineOperationResult::AccountProjection(account),
            ) => {
                snapshot.protected_account = Some(account);
            }
            (
                EngineOperationRequest::SearchPage { page, .. },
                EngineOperationResult::SearchPage {
                    catalog_operation_id,
                    items,
                    next_page_token,
                },
            ) => {
                if page.page_token.is_some() {
                    snapshot.search_results.extend(items);
                } else {
                    snapshot.search_results = items;
                }
                self.current_catalog_operation_id = Some(catalog_operation_id);
                self.current_catalog_next_page_token = next_page_token;
            }
            (
                EngineOperationRequest::PlaylistPage { .. },
                EngineOperationResult::PlaylistPage {
                    playlists,
                    next_page_token,
                },
            ) => {
                snapshot.playlists = playlists;
                snapshot.playlists_next_page_token = next_page_token;
            }
            (
                EngineOperationRequest::HistorySettings { .. },
                EngineOperationResult::HistorySettings(settings),
            ) => {
                snapshot.history_settings = Some(settings);
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
            }
            (
                EngineOperationRequest::PlaybackResolution { .. },
                EngineOperationResult::PlaybackResolved(source),
            ) => {
                snapshot.media_id = Some(source.track_id.clone());
                snapshot.source_uri = Some(source.url);
                snapshot.mime_type = Some(source.content_type);
                snapshot.duration_millis = Some(source.duration_millis);
                snapshot.playback_expires_at_epoch_millis = Some(source.expires_at_epoch_millis);
            }
            _ => {}
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
                self.emit_outcome(
                    command_id,
                    sequence,
                    snapshot_revision,
                    ActorOutcomeStatus::Failed(failure),
                    self.current_snapshot(),
                    None,
                    Vec::new(),
                )
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

    async fn emit_outcome(
        &mut self,
        command_id: CommandId,
        message_sequence: MessageSequence,
        snapshot_revision: SnapshotRevision,
        status: ActorOutcomeStatus,
        snapshot: EngineSnapshot,
        event: Option<EngineEvent>,
        effects: Vec<EngineEffect>,
    ) {
        self.terminal_commands.insert(command_id);
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
            current_catalog_operation_id: None,
            current_catalog_next_page_token: None,
        };
        let join = runtime.spawn(state.run());

        Ok(EngineActorRuntime {
            handle,
            events: EngineActorEventReceiver { events: event_rx },
            task: ActorTask { join },
        })
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

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_clone_send_sync<T: Clone + Send + Sync>() {}

    #[test]
    fn ffi_handle_is_cloneable_send_and_sync_without_engine_access() {
        assert_clone_send_sync::<EngineActorHandle>();
    }
}
