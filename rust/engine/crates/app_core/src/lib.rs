mod command;
mod effect;
mod error;
mod event;
mod middleware;
mod observability;
mod persistence;
mod platform_event;
mod playback;
mod queue;
mod reducer;
mod repository;
mod service;
mod session;
mod snapshot;
mod state_machine;

pub mod test_utils {
    use crate::persistence::{EnginePersistentState, Persistence};
    use std::sync::Mutex;

    pub struct MockPersistence {
        pub state: Mutex<Option<EnginePersistentState>>,
    }

    impl MockPersistence {
        pub fn new() -> Self {
            Self {
                state: Mutex::new(None),
            }
        }
    }

    impl Default for MockPersistence {
        fn default() -> Self {
            Self::new()
        }
    }

    impl Persistence for MockPersistence {
        fn save(&self, state: &EnginePersistentState) -> Result<(), String> {
            *self.state.lock().unwrap() = Some(state.clone());
            Ok(())
        }
        fn load(&self) -> Result<Option<EnginePersistentState>, String> {
            Ok(self.state.lock().unwrap().clone())
        }
    }
}

pub use command::{EngineCommand, EngineCommandType};
pub use effect::EngineEffect;
pub use error::{EngineError, EngineErrorType};
pub use event::{EngineEvent, EngineEventType};
pub use middleware::{
    FocusMiddleware, LoggerMiddleware, Middleware, MiddlewarePipeline, RecoveryMiddleware,
    TelemetryMiddleware, ValidationMiddleware,
};
pub use observability::{EngineObserver, EventBus};
pub use persistence::{EnginePersistentState, NoopPersistence, Persistence};
pub use platform_event::{EnginePlatformEvent, EnginePlatformEventType};
pub use playback::{PlaybackState, RestrictionState};
pub use queue::{QueueManager, RepeatMode};
pub use reducer::{Engine, EngineOutcome};
pub use repository::{InMemoryRepository, MediaItem, MediaRepository};
pub use service::{EngineService, ServiceManager};
pub use session::MediaSession;
pub use snapshot::EngineSnapshot;
