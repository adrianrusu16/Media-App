use std::sync::Arc;
use std::time::Duration;

use crate::model::effect::EngineEffect;
use crate::model::event::EngineEvent;
use crate::model::snapshot::EngineSnapshot;

use super::ids::{CommandId, MessageSequence, PlaybackInstanceId, SnapshotRevision};
use super::operation::EngineOperation;

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
