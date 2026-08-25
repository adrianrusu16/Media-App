use std::sync::RwLock;

use tokio::sync::mpsc;
use tokio::task::JoinHandle;

use crate::AuthStateProvider;
use crate::model::auth::AuthState;

use super::handle::EngineActorHandle;
use super::ids::CommandId;
use super::protocol::{ActorEvent, ActorFailure};

#[derive(Debug)]
pub(super) struct ActorAuthStateProvider {
    state: RwLock<AuthState>,
}

impl ActorAuthStateProvider {
    pub(super) fn new(state: AuthState) -> Self {
        Self {
            state: RwLock::new(state),
        }
    }

    pub(super) fn get(&self) -> AuthState {
        self.state
            .read()
            .expect("actor auth state lock poisoned")
            .clone()
    }

    pub(super) fn set(&self, state: AuthState) {
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
    pub(super) fn new(events: mpsc::Receiver<ActorEvent>) -> Self {
        Self { events }
    }

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
    pub(super) join: JoinHandle<Result<ActorExit, ActorFailure>>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ActorExit {
    /// Every accepted command has exactly one entry before a clean wait returns.
    pub terminal_commands: Vec<CommandId>,
}

impl ActorTask {
    pub(super) fn new(join: JoinHandle<Result<ActorExit, ActorFailure>>) -> Self {
        Self { join }
    }

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
    pub(super) fn new(
        handle: EngineActorHandle,
        events: EngineActorEventReceiver,
        task: ActorTask,
    ) -> Self {
        Self {
            handle,
            events,
            task,
        }
    }

    pub fn into_parts(self) -> (EngineActorHandle, EngineActorEventReceiver, ActorTask) {
        (self.handle, self.events, self.task)
    }
}
