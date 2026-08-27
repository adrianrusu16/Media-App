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

pub use crate::data::encrypted_session_store::{
    EncryptedFileSessionStore, SealedSession, SessionCryptor,
};
pub use crate::data::persistence::{EnginePersistentState, NoopPersistence, Persistence};
pub use crate::data::queue::{QueueManager, RepeatMode};
pub use crate::data::repository::{InMemoryRepository, MediaItem, MediaItemType, MediaRepository};
pub use crate::data::session::MediaSession;
pub use crate::data::session_store::{
    InMemorySessionStore, SessionStore, SessionStoreError, SessionStoreSecurity,
    validate_production_session_store,
};
pub use crate::engine::concurrent::ConcurrentEngine;
pub use crate::engine::core::{Engine, EngineOutcome};
pub use crate::engine::observability::{EngineObserver, EventBus};
pub use crate::middleware::{
    AnalyticsMiddleware, FocusMiddleware, LoggerMiddleware, Middleware, MiddlewarePipeline,
    RecoveryMiddleware, TelemetryMiddleware, ThrottlingMiddleware, ValidationMiddleware,
};
pub use crate::model::auth::{
    Account, AccountOperation, AccountPort, AuthRequestAcceptance, AuthSession,
    AuthSessionEnvelope, AuthState, EngineAccountIdentity, account_retry_class,
};
pub use crate::model::backend::{
    BackendAvailability, BackendUnavailableReason, EngineBackendStatus, EngineDependencyStatus,
    EngineStatusValue, RetryClass,
};
pub use crate::model::catalog::{
    EngineAlbum, EngineArtist, EngineArtwork, EngineTrack, canopy_artwork_http_uri,
    project_artwork_identity,
};
pub use crate::model::command::{EngineCommand, EngineCommandType};
pub use crate::model::discovery::{DiscoveryFeed, DiscoveryPort, EngineDiscoveryIdentity};
pub use crate::model::effect::EngineEffect;
pub use crate::model::error::{EngineError, EngineErrorType};
pub use crate::model::event::{EngineEvent, EngineEventType};
pub use crate::model::history::{
    EngineHistoryAvailability, EngineHistoryEntry, EngineHistoryIdentity, EngineHistoryPage,
    EngineHistoryPageKey, EngineHistoryRefreshState, EngineHistorySettings,
    EngineHistorySettingsUpdate, EngineHistoryState, EnginePlaybackRecord, HistoryPort,
    normalize_completion_ratio,
};
pub use crate::model::library::{
    EngineLibraryIdentity, EngineLibraryRelationshipKind, EngineLibraryTrack, LibraryPort,
};
pub use crate::model::page::{EnginePageRequest, EnginePageToken, EnginePagedResult};
pub use crate::model::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
pub use crate::model::playback::{
    ControlState, DrivingState, EnginePlaybackSource, PlaybackState, PlayerControls,
    RestrictionState,
};
pub use crate::model::playlist::{
    EngineCreatePlaylist, EnginePlaylist, EnginePlaylistIdentity, EnginePlaylistTrack,
    EngineUpdatePlaylist, PlaylistPort, PlaylistReconciliation,
};
pub use crate::model::preferences::{PreferenceSource, ThemePreference, ThemePreferenceState};
pub use crate::model::snapshot::EngineSnapshot;
pub use crate::networking::{
    AudioChunk, AudioSourceClient, AudioSourceOperation, AudioSourceRetryPolicy, AuthPort,
    AuthStateProvider, CanopyAuthClient, CanopyCatalogClient, CanopyDiscoveryClient,
    CanopyHistoryClient, CanopyLibraryClient, CanopyPlaybackClient, CanopyPlaylistClient,
    CatalogPort, PlaybackPort, PlaybackSource, RemoteRepository, RetryingAudioSourceClient,
    SessionCoordinator, SystemPort,
};
pub use crate::services::player::{MediaPlayer, MockPlayer};
pub use crate::services::playlist_reconciler::PlaylistReconciler;
pub use crate::services::service::{EngineService, ServiceManager};
pub use crate::services::voice::{
    MockVoiceEngine, VoiceEngine, VoiceInteractionResult, VoskVoiceEngine,
};

pub use crate::model::profile::{EngineProfile, EngineProfileUpdate, ProfilePort};

pub use crate::networking::CanopyProfileClient;
