use crate::data::persistence::{EnginePersistentState, NoopPersistence, Persistence};
use crate::data::queue::QueueManager;
use crate::data::repository::{InMemoryRepository, MediaRepository};
use crate::data::session::MediaSession;
use crate::engine::state_machine::StateMachine;
use crate::middleware::MiddlewarePipeline;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::discovery::DiscoveryPort;
use crate::model::effect::EngineEffect;
use crate::model::error::EngineError;
use crate::model::event::EngineEvent;
use crate::model::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
use crate::model::playback::PlaybackState;
use crate::model::snapshot::EngineSnapshot;
use crate::networking::{AudioSourceClient, AuthStateProvider, PlaybackPort, SystemPort};
use crate::services::player::MediaPlayer;
use crate::services::voice::{VoiceEngine, VoiceInteractionResult};
use tracing::{info, instrument, warn};

use crate::engine::observability::EventBus;
use crate::services::service::ServiceManager;
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Clone)]
enum CatalogOperation {
    Search {
        query: String,
        page_size: u32,
        next_page_token: Option<crate::EnginePageToken>,
        items: Vec<crate::MediaItem>,
    },
    Browse {
        parent_id: Option<String>,
        genres: Vec<String>,
        page_size: u32,
        next_page_token: Option<crate::EnginePageToken>,
        items: Vec<crate::MediaItem>,
    },
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct AuthIdentity {
    account_id: String,
    session_id: String,
}

impl AuthIdentity {
    fn from_state(state: &crate::AuthState) -> Option<Self> {
        match state {
            crate::AuthState::Authenticated { account, session }
                if !account.id.trim().is_empty()
                    && !session.id.trim().is_empty()
                    && session.current =>
            {
                Some(Self {
                    account_id: account.id.clone(),
                    session_id: session.id.clone(),
                })
            }
            crate::AuthState::Authenticated { .. } => None,
            crate::AuthState::Anonymous | crate::AuthState::LoginRequired => None,
        }
    }

    fn history_identity(&self) -> crate::EngineHistoryIdentity {
        crate::EngineHistoryIdentity {
            account_id: self.account_id.clone(),
            session_id: self.session_id.clone(),
        }
    }
}

#[derive(Clone)]
struct DiscoveryOperation {
    auth_identity: AuthIdentity,
    excluded_track_ids: Vec<String>,
    page_size: u32,
    next_page_token: Option<crate::EnginePageToken>,
    items: Vec<crate::MediaItem>,
}

#[derive(Clone)]
struct HistoryOperation {
    auth_identity: AuthIdentity,
    page_size: u32,
}

fn project_discovery_track(track: crate::EngineTrack) -> crate::MediaItem {
    crate::MediaItem {
        id: track.id,
        title: track.title,
        artist: track.artist.name,
        album: track.album.map(|album| album.title),
        duration_millis: Some(track.duration_millis),
        thumbnail_url: track.artwork_id,
        ..Default::default()
    }
}

// Core engine orchestration root:
// - Public `Engine` API surface lives here.
// - Heavy execution paths are delegated to focused submodules below.
mod controls;
mod dispatch_command;
mod dispatch_platform_event;
mod effects;
mod history;
mod persistence_state;

/// Result of an engine operation, containing the new state and an event to be broadcasted.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineOutcome {
    /// The updated engine state.
    pub snapshot: EngineSnapshot,
    /// The event resulting from the action.
    pub event: EngineEvent,
    /// List of effects that the host must perform.
    pub effects: Vec<EngineEffect>,
}

/// The main state machine of the media engine.
///
/// It follows the Redux/ELM pattern where state transitions are deterministic
/// based on the current state and a given command.
pub struct Engine {
    snapshot: EngineSnapshot,
    config: crate::model::config::EngineConfig,
    middleware: Arc<MiddlewarePipeline>,
    repository: Box<dyn MediaRepository>,
    queue: QueueManager,
    persistence: Box<dyn Persistence>,
    event_bus: Arc<EventBus>,
    service_manager: ServiceManager,
    audio_source_client: Option<Arc<dyn AudioSourceClient>>,
    playback_port: Option<Arc<dyn PlaybackPort>>,
    system_port: Option<Arc<dyn SystemPort>>,
    auth_state_provider: Option<Arc<dyn AuthStateProvider>>,
    discovery_port: Option<Arc<dyn DiscoveryPort>>,
    history_port: Option<Arc<dyn crate::HistoryPort>>,
    profile_port: Option<Arc<dyn crate::ProfilePort>>,
    profile_projection_identity: Option<AuthIdentity>,
    history_projection_identity: Option<AuthIdentity>,
    history_operation: Option<HistoryOperation>,
    catalog_operations: HashMap<String, CatalogOperation>,
    next_catalog_operation_sequence: u64,
    discovery_operation: Option<DiscoveryOperation>,
    player: Option<Box<dyn MediaPlayer>>,
    voice_engine: Option<Box<dyn VoiceEngine>>,
}

impl Default for Engine {
    fn default() -> Self {
        let bus = Arc::new(EventBus::default());
        Self {
            snapshot: EngineSnapshot::default(),
            config: crate::model::config::EngineConfig::default(),
            middleware: Arc::new(MiddlewarePipeline::default()),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue: QueueManager::default(),
            persistence: Box::new(NoopPersistence),
            event_bus: bus,
            service_manager: ServiceManager::new(),
            audio_source_client: None,
            playback_port: None,
            system_port: None,
            auth_state_provider: None,
            discovery_port: None,
            profile_port: None,
            profile_projection_identity: None,
            history_port: None,
            history_projection_identity: None,
            history_operation: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
            discovery_operation: None,

            player: None,
            voice_engine: None,
        }
    }
}

impl std::fmt::Debug for Engine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Engine")
            .field("snapshot", &self.snapshot)
            .finish()
    }
}

impl Engine {
    /// Initializes a new engine instance with the given timestamp.
    pub fn new(now_epoch_millis: u64) -> Self {
        let bus = Arc::new(EventBus::default());
        let snapshot = EngineSnapshot::idle(now_epoch_millis);
        let config = crate::model::config::EngineConfig::default();
        let queue = QueueManager::default();

        // Create initial engine to derive controls
        let engine = Self {
            snapshot,
            config,
            middleware: Arc::new(MiddlewarePipeline::new()),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue,
            persistence: Box::new(NoopPersistence),
            event_bus: bus,
            service_manager: ServiceManager::new(),
            audio_source_client: None,
            playback_port: None,
            system_port: None,
            auth_state_provider: None,
            discovery_port: None,
            profile_port: None,
            profile_projection_identity: None,
            history_port: None,
            history_projection_identity: None,
            history_operation: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
            discovery_operation: None,

            player: None,
            voice_engine: None,
        };

        let mut final_snapshot = engine.snapshot.clone();
        final_snapshot.controls = engine.derive_controls(&final_snapshot);

        Self {
            snapshot: final_snapshot,
            ..engine
        }
    }

    /// Returns the event bus for the engine.
    pub fn event_bus(&self) -> Arc<EventBus> {
        self.event_bus.clone()
    }

    /// Returns the queue manager for the engine.
    pub fn queue(&mut self) -> &mut QueueManager {
        &mut self.queue
    }

    /// Returns the service manager for the engine.
    pub fn services(&mut self) -> &mut ServiceManager {
        &mut self.service_manager
    }

    /// Ticks all background services and processes any commands they emit.
    pub async fn tick(&mut self, now_epoch_millis: u64) -> Vec<EngineOutcome> {
        let commands = self.service_manager.tick(self, now_epoch_millis);
        let mut outcomes = Vec::new();
        for cmd in commands {
            outcomes.push(self.dispatch(cmd, now_epoch_millis).await);
        }
        outcomes
    }

    /// Sets the media repository for the engine.
    pub fn set_repository(&mut self, repository: Box<dyn MediaRepository>) {
        self.repository = repository;
    }

    /// Sets the middleware pipeline for the engine.
    pub fn set_middleware(&mut self, pipeline: MiddlewarePipeline) {
        self.middleware = Arc::new(pipeline);
    }

    /// Sets the persistence for the engine.
    pub fn set_persistence(&mut self, persistence: Box<dyn Persistence>) {
        self.persistence = persistence;
    }

    /// Sets the playback source resolver for the engine.
    pub fn set_audio_source_client(&mut self, client: Arc<dyn AudioSourceClient>) {
        self.audio_source_client = Some(client);
    }

    /// Sets the canonical backend-neutral playback resolver.
    pub fn set_playback_port(&mut self, port: Arc<dyn PlaybackPort>) {
        self.playback_port = Some(port);
    }

    /// Sets the authenticated discovery-feed boundary.
    pub fn set_discovery_port(&mut self, port: Arc<dyn DiscoveryPort>) {
        self.discovery_port = Some(port);
    }

    /// Sets the authenticated backend-neutral playback-history boundary.
    pub fn set_history_port(&mut self, port: Arc<dyn crate::HistoryPort>) {
        self.history_port = Some(port);
    }

    /// Sets the authenticated backend-neutral profile boundary.
    pub fn set_profile_port(&mut self, port: Arc<dyn crate::ProfilePort>) {
        self.profile_port = Some(port);
    }

    /// Sets the backend-neutral public system status port.
    pub fn set_system_port(&mut self, port: Arc<dyn SystemPort>) {
        self.system_port = Some(port);
    }

    /// Sets the service-neutral source used for live auth-state projections.
    pub fn set_auth_state_provider(&mut self, provider: Arc<dyn AuthStateProvider>) {
        self.auth_state_provider = Some(provider);
    }

    fn snapshot_projection(&self) -> EngineSnapshot {
        let mut snapshot = self.snapshot.clone();
        snapshot.auth_state = self
            .auth_state_provider
            .as_ref()
            .map(|provider| provider.current_auth_state())
            .unwrap_or(crate::AuthState::Anonymous);
        let current_identity = AuthIdentity::from_state(&snapshot.auth_state);
        let operation_matches = self
            .discovery_operation
            .as_ref()
            .is_some_and(|operation| Some(&operation.auth_identity) == current_identity.as_ref());
        if !operation_matches {
            snapshot.discovery_results.clear();
        }
        if self
            .profile_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_profile_projection(&mut snapshot);
        }
        if self
            .history_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_history_projection(&mut snapshot);
        }
        snapshot
    }

    pub(crate) fn sync_auth_state_projection(&mut self) {
        self.snapshot.auth_state = self
            .auth_state_provider
            .as_ref()
            .map(|provider| provider.current_auth_state())
            .unwrap_or(crate::AuthState::Anonymous);
        let current_identity = AuthIdentity::from_state(&self.snapshot.auth_state);
        let operation_matches = self
            .discovery_operation
            .as_ref()
            .is_some_and(|operation| Some(&operation.auth_identity) == current_identity.as_ref());
        if !operation_matches {
            self.discovery_operation = None;
            self.snapshot.discovery_results.clear();
        }
        if self
            .profile_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            self.profile_projection_identity = None;
            Self::clear_profile_projection(&mut self.snapshot);
        }
        if self
            .history_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            self.history_projection_identity = None;
            self.history_operation = None;
            Self::clear_history_projection(&mut self.snapshot);
        }
    }

    fn clear_profile_projection(snapshot: &mut EngineSnapshot) {
        snapshot.profile = None;
        snapshot.profile_preferences.clear();
        if snapshot.theme_preference.source == crate::PreferenceSource::RemoteProfile {
            snapshot.theme_preference = crate::ThemePreferenceState::default();
        }
    }

    fn clear_history_projection(snapshot: &mut EngineSnapshot) {
        snapshot.history_settings = None;
        snapshot.history_entries.clear();
        snapshot.history_next_page_token = None;
        snapshot.history_deleted_count = 0;
    }

    fn publish_intermediate_snapshot(&mut self, snapshot: EngineSnapshot) {
        self.snapshot = snapshot;
        self.event_bus.notify_state_changed(&self.snapshot);
    }

    /// Sets the media player for the engine.
    pub fn set_player(&mut self, player: Box<dyn MediaPlayer>) {
        self.player = Some(player);
    }

    /// Sets the voice engine for the engine.
    pub fn set_voice_engine(&mut self, voice_engine: Box<dyn VoiceEngine>) {
        self.voice_engine = Some(voice_engine);
    }

    /// Returns an owned, credential-free projection of the current engine state.
    pub fn snapshot(&self) -> EngineSnapshot {
        self.snapshot_projection()
    }

    /// Returns the current configuration of the engine.
    pub fn config(&self) -> &crate::model::config::EngineConfig {
        &self.config
    }

    fn allocate_catalog_operation_id(&mut self, now_epoch_millis: u64) -> String {
        let sequence = self.next_catalog_operation_sequence;
        self.next_catalog_operation_sequence = self.next_catalog_operation_sequence.wrapping_add(1);
        format!("catalog-{now_epoch_millis}-{sequence}")
    }

    /// Dispatches a command to the engine, returning the outcome.
    ///
    /// This is the primary way to interact with the engine.
    #[instrument(skip(self), fields(command_type = ?command.command_type))]
    pub async fn dispatch(
        &mut self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        self.dispatch_command(command, now_epoch_millis).await
    }

    /// Dispatches a platform-level event to the engine.
    pub async fn dispatch_platform_event(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        self.dispatch_platform_event_impl(event, now_epoch_millis)
            .await
    }
}

#[cfg(test)]
mod core_tests;
