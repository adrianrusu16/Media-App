use std::sync::Arc;

use crate::engine::core::Engine;
use crate::model::command::EngineCommand;
use crate::model::effect::EngineEffect;
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

/// Internal asynchronous work envelope. `Request` is immutable owned data; an
/// operation worker receives this envelope rather than `Engine` or any engine
/// lock. Task 4 supplies the concrete request enum and effect supervisor.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineOperation<Request> {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub request: Request,
}

/// Typed completion envelope returned by an operation worker. The generation
/// captured at launch remains attached through completion validation.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineOperationCompletion<Output> {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub result: Result<Output, crate::model::error::EngineError>,
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
    pub fn is_current(self, _candidate: OperationGeneration) -> bool {
        unimplemented!("Task 4 must validate operation completions by typed domain generation")
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
    pub effects: Vec<EngineEffect>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum ActorEvent {
    CommandOutcome(ActorOutcome),
    PlayerFactsApplied {
        facts: PlayerFacts,
        message_sequence: MessageSequence,
    },
    PlayerEdgeApplied {
        event: PlayerEdgeEvent,
        message_sequence: MessageSequence,
    },
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
    RuntimeNotImplemented,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ActorConfig {
    pub command_capacity: usize,
    pub operation_completion_capacity: usize,
    pub player_edge_capacity: usize,
    pub lifecycle_capacity: usize,
    pub outcome_capacity: usize,
}

impl Default for ActorConfig {
    fn default() -> Self {
        Self {
            command_capacity: 64,
            operation_completion_capacity: 64,
            player_edge_capacity: 32,
            lifecycle_capacity: 4,
            outcome_capacity: 128,
        }
    }
}

/// Cloneable synchronous boundary intended for JNI and Binder callers. None of
/// its methods wait for actor work or expose mutable `Engine` access.
#[derive(Clone, Debug)]
pub struct EngineActorHandle {
    latest_snapshot: Arc<PublishedSnapshot>,
}

impl EngineActorHandle {
    pub fn try_submit(
        &self,
        _command: EngineCommand,
        _now_epoch_millis: u64,
    ) -> Result<CommandId, SubmissionError> {
        Err(SubmissionError::ChannelClosed {
            lane: ActorLane::Command,
        })
    }

    pub fn latest_snapshot(&self) -> Arc<PublishedSnapshot> {
        Arc::clone(&self.latest_snapshot)
    }

    pub fn publish_player_facts(&self, _facts: PlayerFacts) -> Result<(), SubmissionError> {
        Err(SubmissionError::ChannelClosed {
            lane: ActorLane::PlayerFacts,
        })
    }

    pub fn try_publish_player_edge(&self, _event: PlayerEdgeEvent) -> Result<(), SubmissionError> {
        Err(SubmissionError::ChannelClosed {
            lane: ActorLane::PlayerEdge,
        })
    }

    pub fn request_shutdown(&self) -> Result<(), SubmissionError> {
        Err(SubmissionError::ChannelClosed {
            lane: ActorLane::Lifecycle,
        })
    }
}

#[derive(Debug)]
pub struct EngineActorEventReceiver;

impl EngineActorEventReceiver {
    pub async fn recv(&mut self) -> Option<ActorEvent> {
        None
    }
}

#[derive(Debug)]
pub struct ActorTask;

impl ActorTask {
    pub async fn wait(self) -> Result<(), ActorFailure> {
        Err(ActorFailure::RuntimeUnavailable)
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

pub struct EngineActor;

impl EngineActor {
    pub fn spawn(
        _engine: Engine,
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
        Err(ActorStartError::RuntimeNotImplemented)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_clone_send_sync<T: Clone + Send + Sync>() {}

    #[test]
    fn ffi_handle_is_cloneable_send_and_sync_without_engine_access() {
        assert_clone_send_sync::<EngineActorHandle>();
    }

    #[test]
    fn identity_or_session_replacement_rejects_an_older_account_completion() {
        let current = DomainGenerations {
            account: AccountGeneration::new(2),
            ..DomainGenerations::default()
        };

        assert!(!current.is_current(OperationGeneration::Account(AccountGeneration::new(1))));
    }

    #[test]
    fn newer_search_rejects_an_older_search_page_completion() {
        let current = DomainGenerations {
            search: SearchGeneration::new(8),
            ..DomainGenerations::default()
        };

        assert!(!current.is_current(OperationGeneration::Search(SearchGeneration::new(7))));
    }

    #[test]
    fn newer_playlist_work_rejects_an_older_playlist_completion() {
        let current = DomainGenerations {
            playlist: PlaylistGeneration::new(12),
            ..DomainGenerations::default()
        };

        assert!(!current.is_current(OperationGeneration::Playlist(PlaylistGeneration::new(11))));
    }

    #[test]
    fn newer_playback_resolution_rejects_an_older_completion() {
        let current = DomainGenerations {
            playback: PlaybackInstanceId::new(22),
            ..DomainGenerations::default()
        };

        assert!(!current.is_current(OperationGeneration::Playback(PlaybackInstanceId::new(21))));
    }

    #[test]
    fn newer_history_work_rejects_an_older_history_completion() {
        let current = DomainGenerations {
            history: HistoryGeneration::new(5),
            ..DomainGenerations::default()
        };

        assert!(!current.is_current(OperationGeneration::History(HistoryGeneration::new(4))));
    }
}
