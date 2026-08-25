use std::any::Any;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, RwLock};

use tokio::sync::{mpsc, oneshot, watch};

use crate::engine::core::{Engine, EngineOutcome};
use crate::model::command::EngineCommand;
use crate::model::platform_event::EnginePlatformEvent;

use super::ids::CommandId;
use super::operation::EngineOperationCompletion;
use super::protocol::{
    ActorConfig, ActorControl, ActorLane, ActorOutcome, PlayerEdgeEvent, PlayerFacts,
    PublishedSnapshot, SubmissionError,
};

/// Cloneable synchronous boundary intended for JNI and Binder callers. None of
/// its methods wait for actor work or expose mutable `Engine` access.
#[derive(Clone, Debug)]
pub struct EngineActorHandle {
    pub(super) latest_snapshot: Arc<RwLock<Arc<PublishedSnapshot>>>,
    pub(super) command_tx: mpsc::Sender<CommandMessage>,
    pub(super) operation_completion_tx: mpsc::Sender<EngineOperationCompletion>,
    pub(super) player_facts_tx: watch::Sender<Option<PlayerFacts>>,
    pub(super) player_edge_tx: mpsc::Sender<PlayerEdgeEvent>,
    pub(super) lifecycle_tx: mpsc::Sender<LifecycleMessage>,
    pub(super) shutting_down: Arc<AtomicBool>,
    pub(super) next_command_id: Arc<AtomicU64>,
    pub(super) config: ActorConfig,
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
pub(super) struct CommandMessage {
    pub(super) command_id: CommandId,
    pub(super) input: EngineInput,
    pub(super) now_epoch_millis: u64,
    pub(super) response: Option<oneshot::Sender<ActorOutcome>>,
}

#[derive(Debug)]
pub(super) enum EngineInput {
    Command(EngineCommand),
    PlatformEvent(EnginePlatformEvent),
}

pub(super) type EngineMutation = Box<dyn FnOnce(&mut Engine) -> Box<dyn Any + Send> + Send>;

pub(super) enum LifecycleMessage {
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
