pub mod data;
pub mod engine;
pub mod middleware;
pub mod model;
pub mod networking;
pub mod services;

pub mod test_utils {
    use crate::data::persistence::{EnginePersistentState, Persistence};
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

    impl<P: Persistence + ?Sized> Persistence for std::sync::Arc<P> {
        fn save(&self, state: &EnginePersistentState) -> Result<(), String> {
            (**self).save(state)
        }
        fn load(&self) -> Result<Option<EnginePersistentState>, String> {
            (**self).load()
        }
    }
}

pub use crate::data::persistence::{EnginePersistentState, NoopPersistence, Persistence};
pub use crate::data::queue::{QueueManager, RepeatMode};
pub use crate::data::repository::{InMemoryRepository, MediaItem, MediaItemType, MediaRepository};
pub use crate::data::session::MediaSession;
pub use crate::engine::concurrent::ConcurrentEngine;
pub use crate::engine::core::{Engine, EngineOutcome};
pub use crate::engine::observability::{EngineObserver, EventBus};
pub use crate::middleware::{
    AnalyticsMiddleware, FocusMiddleware, LoggerMiddleware, Middleware, MiddlewarePipeline,
    RecoveryMiddleware, TelemetryMiddleware, ThrottlingMiddleware, ValidationMiddleware,
};
pub use crate::model::command::{EngineCommand, EngineCommandType};
pub use crate::model::effect::EngineEffect;
pub use crate::model::error::{EngineError, EngineErrorType};
pub use crate::model::event::{EngineEvent, EngineEventType};
pub use crate::model::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
pub use crate::model::playback::{
    ControlState, DrivingState, PlaybackState, PlayerControls, RestrictionState,
};
pub use crate::model::preferences::{PreferenceSource, ThemePreference, ThemePreferenceState};
pub use crate::model::snapshot::EngineSnapshot;
pub use crate::networking::{
    AudioChunk, AudioSourceClient, BackendClient, CanopyAudioSourceClient, PlaybackSource,
    RemoteRepository, RetryingAudioSourceClient,
};
pub use crate::services::player::{MediaPlayer, MockPlayer};
pub use crate::services::service::{EngineService, ServiceManager};
pub use crate::services::voice::{
    MockVoiceEngine, VoiceEngine, VoiceInteractionResult, VoskVoiceEngine,
};
