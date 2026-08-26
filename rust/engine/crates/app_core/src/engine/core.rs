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
use tracing::{debug, info, instrument, warn};

use crate::engine::observability::EventBus;
use crate::services::service::ServiceManager;
use std::collections::{HashMap, VecDeque};
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

    fn library_identity(&self) -> crate::EngineLibraryIdentity {
        crate::EngineLibraryIdentity {
            account_id: self.account_id.clone(),
            session_id: self.session_id.clone(),
        }
    }

    fn playlist_identity(&self) -> crate::EnginePlaylistIdentity {
        crate::EnginePlaylistIdentity {
            account_id: self.account_id.clone(),
            session_id: self.session_id.clone(),
        }
    }

    fn history_identity(&self) -> crate::EngineHistoryIdentity {
        crate::EngineHistoryIdentity {
            account_id: self.account_id.clone(),
            session_id: self.session_id.clone(),
        }
    }

    fn account_identity(&self) -> crate::EngineAccountIdentity {
        crate::EngineAccountIdentity {
            account_id: self.account_id.clone(),
            session_id: self.session_id.clone(),
        }
    }

    fn discovery_identity(&self) -> crate::EngineDiscoveryIdentity {
        crate::EngineDiscoveryIdentity {
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
struct LibraryPageOperation {
    auth_identity: AuthIdentity,
    page_size: u32,
}

#[derive(Clone)]
struct PlaylistPageOperation {
    auth_identity: AuthIdentity,
    playlist_id: Option<String>,
    page_size: u32,
}

#[derive(Clone)]
struct HistoryOperation {
    owner: HistoryProjectionOwner,
    page_size: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum HistoryProjectionOwner {
    Anonymous,
    Authenticated(AuthIdentity),
}

impl HistoryProjectionOwner {
    fn current(auth_state: &crate::AuthState) -> Self {
        AuthIdentity::from_state(auth_state)
            .map(Self::Authenticated)
            .unwrap_or(Self::Anonymous)
    }

    fn matches_auth_state(&self, auth_state: &crate::AuthState) -> bool {
        match (self, AuthIdentity::from_state(auth_state)) {
            (Self::Anonymous, None) => true,
            (Self::Authenticated(expected), Some(current)) => expected == &current,
            _ => false,
        }
    }
}

struct AnonymousHistoryBuffer {
    entries: VecDeque<crate::EngineHistoryEntry>,
    max_entries: usize,
    enabled: bool,
    next_sequence: u64,
}

impl Default for AnonymousHistoryBuffer {
    fn default() -> Self {
        Self::new(ANONYMOUS_HISTORY_MAX_ENTRIES)
    }
}

impl AnonymousHistoryBuffer {
    fn new(max_entries: usize) -> Self {
        Self {
            entries: VecDeque::new(),
            max_entries,
            enabled: true,
            next_sequence: 0,
        }
    }

    fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    fn clear(&mut self) -> u64 {
        let deleted = self.entries.len() as u64;
        self.entries.clear();
        deleted
    }
}

const ANONYMOUS_HISTORY_MAX_ENTRIES: usize = 50;

#[derive(Clone, Debug, Default)]
struct HistoryListenTracker {
    playback_instance_id: Option<u64>,
    accumulated_playing_millis: u64,
    playing_since_epoch_millis: Option<u64>,
    recorded_instance_id: Option<u64>,
}

impl HistoryListenTracker {
    fn elapsed_millis(&self, now_epoch_millis: u64) -> u64 {
        let live = self
            .playing_since_epoch_millis
            .map(|started| now_epoch_millis.saturating_sub(started))
            .unwrap_or(0);
        self.accumulated_playing_millis.saturating_add(live)
    }
}

#[derive(Clone)]
struct DeviceSessionsOperation {
    auth_identity: AuthIdentity,
    page_size: u32,
}

/// Bounded recovery policy for the current logical playback context. The
/// instance IDs make each budget stale-safe while the intent survives local
/// player recreation and a subsequent source-capability refresh.
#[derive(Default)]
struct PlaybackRecoveryState {
    source_refresh_attempted_for: Option<u64>,
    decoder_attempted_for: Option<u64>,
    desired_play_when_ready: bool,
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
mod account;
mod controls;
mod dispatch_command;
mod dispatch_platform_event;
mod effects;
mod history;
mod library;
mod persistence_state;
mod playlist;

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
    repository: Arc<dyn MediaRepository>,
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
    library_port: Option<Arc<dyn crate::LibraryPort>>,
    playlist_port: Option<Arc<dyn crate::PlaylistPort>>,
    profile_port: Option<Arc<dyn crate::ProfilePort>>,
    account_port: Option<Arc<dyn crate::AccountPort>>,
    account_projection_identity: Option<AuthIdentity>,
    device_sessions_operation: Option<DeviceSessionsOperation>,
    profile_projection_identity: Option<AuthIdentity>,
    history_projection_owner: Option<HistoryProjectionOwner>,
    history_operation: Option<HistoryOperation>,
    anonymous_history: AnonymousHistoryBuffer,
    anonymous_history_reconciliation_in_flight: bool,
    history_listen: HistoryListenTracker,
    next_history_record_sequence: u64,
    library_projection_identity: Option<AuthIdentity>,
    saved_library_operation: Option<LibraryPageOperation>,
    liked_library_operation: Option<LibraryPageOperation>,
    playlist_projection_identity: Option<AuthIdentity>,
    playlists_operation: Option<PlaylistPageOperation>,
    playlist_tracks_operation: Option<PlaylistPageOperation>,
    catalog_operations: HashMap<String, CatalogOperation>,
    next_catalog_operation_sequence: u64,
    next_playback_instance_id: u64,
    current_playback_instance_id: Option<u64>,
    pending_seek_target_millis: Option<u64>,
    recovery: PlaybackRecoveryState,
    feed_projection_identity: Option<AuthIdentity>,
    discovery_operation: Option<DiscoveryOperation>,
    player: Option<Box<dyn MediaPlayer>>,
    voice_engine: Option<Box<dyn VoiceEngine>>,
    prefetched_operation: Option<PrefetchedOperation>,
}

/// Remote result fetched off the actor thread and injected back into the normal
/// dispatch path, so split and inline execution share one code path.
pub(crate) enum PrefetchedOperation {
    Account(Result<crate::Account, crate::model::error::EngineError>),
    Search(
        Result<
            crate::EnginePagedResult<crate::data::repository::MediaItem>,
            crate::model::error::EngineError,
        >,
    ),
    Playlists(
        Result<crate::EnginePagedResult<crate::EnginePlaylist>, crate::model::error::EngineError>,
    ),
    HistorySettings(Result<crate::EngineHistorySettings, crate::model::error::EngineError>),
    Playback(Result<crate::EnginePlaybackSource, crate::model::error::EngineError>),
}

impl Default for Engine {
    fn default() -> Self {
        let bus = Arc::new(EventBus::default());
        Self {
            snapshot: EngineSnapshot::default(),
            config: crate::model::config::EngineConfig::default(),
            middleware: Arc::new(MiddlewarePipeline::default()),
            repository: Arc::new(InMemoryRepository::new(vec![])),
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
            account_port: None,
            account_projection_identity: None,
            device_sessions_operation: None,
            profile_projection_identity: None,
            history_port: None,
            history_projection_owner: None,
            history_operation: None,
            anonymous_history: AnonymousHistoryBuffer::default(),
            anonymous_history_reconciliation_in_flight: false,
            history_listen: HistoryListenTracker::default(),
            next_history_record_sequence: 0,
            library_port: None,
            playlist_port: None,
            library_projection_identity: None,
            saved_library_operation: None,
            liked_library_operation: None,
            playlist_projection_identity: None,
            playlists_operation: None,
            playlist_tracks_operation: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
            next_playback_instance_id: 0,
            current_playback_instance_id: None,
            pending_seek_target_millis: None,
            recovery: PlaybackRecoveryState::default(),
            feed_projection_identity: None,
            discovery_operation: None,

            player: None,
            voice_engine: None,
            prefetched_operation: None,
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
    /// Updates reachability without rebuilding the engine or losing local
    /// playback/queue/session state. The Android connection supervisor owns
    /// when this is called; the engine remains the snapshot source of truth.
    pub fn set_backend_availability(&mut self, availability: crate::BackendAvailability) {
        self.snapshot.backend_availability = availability;
    }
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
            repository: Arc::new(InMemoryRepository::new(vec![])),
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
            account_port: None,
            account_projection_identity: None,
            device_sessions_operation: None,
            profile_projection_identity: None,
            history_port: None,
            history_projection_owner: None,
            history_operation: None,
            anonymous_history: AnonymousHistoryBuffer::default(),
            anonymous_history_reconciliation_in_flight: false,
            history_listen: HistoryListenTracker::default(),
            next_history_record_sequence: 0,
            library_port: None,
            playlist_port: None,
            library_projection_identity: None,
            saved_library_operation: None,
            liked_library_operation: None,
            playlist_projection_identity: None,
            playlists_operation: None,
            playlist_tracks_operation: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
            next_playback_instance_id: 0,
            current_playback_instance_id: None,
            pending_seek_target_millis: None,
            recovery: PlaybackRecoveryState::default(),
            feed_projection_identity: None,
            discovery_operation: None,

            player: None,
            voice_engine: None,
            prefetched_operation: None,
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
        let generation_before = self.snapshot.history_state.generation;
        let mut snapshot = self.snapshot.clone();
        self.maybe_auto_record_history(now_epoch_millis, &mut snapshot)
            .await;
        self.snapshot = snapshot;
        if self.snapshot.history_state.generation != generation_before {
            self.event_bus.notify_state_changed(&self.snapshot);
        }
        outcomes
    }

    /// Sets the media repository for the engine.
    pub fn set_repository(&mut self, repository: Box<dyn MediaRepository>) {
        self.repository = Arc::from(repository);
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

    pub(crate) fn actor_history_port(&self) -> Option<Arc<dyn crate::HistoryPort>> {
        self.history_port.clone()
    }

    pub(crate) fn actor_account_port(&self) -> Option<Arc<dyn crate::AccountPort>> {
        self.account_port.clone()
    }

    pub(crate) fn actor_playlist_port(&self) -> Option<Arc<dyn crate::PlaylistPort>> {
        self.playlist_port.clone()
    }

    pub(crate) fn actor_playback_port(&self) -> Option<Arc<dyn PlaybackPort>> {
        self.playback_port.clone()
    }

    pub(crate) fn actor_repository(&self) -> Arc<dyn MediaRepository> {
        self.repository.clone()
    }

    /// Continuation parameters the inline path would use for a catalog page,
    /// so a worker fetches the same page the engine would have fetched.
    pub(crate) fn actor_catalog_continuation(
        &self,
        operation_id: &str,
    ) -> Option<(String, u32, crate::EnginePageToken)> {
        match self.catalog_operations.get(operation_id) {
            Some(CatalogOperation::Search {
                query,
                page_size,
                next_page_token: Some(token),
                ..
            }) => Some((query.clone(), *page_size, token.clone())),
            _ => None,
        }
    }

    pub(crate) fn set_prefetched_operation(&mut self, prefetched: PrefetchedOperation) {
        self.prefetched_operation = Some(prefetched);
    }

    pub(crate) fn clear_prefetched_operation(&mut self) {
        self.prefetched_operation = None;
    }

    pub(super) fn take_prefetched_account(
        &mut self,
    ) -> Option<Result<crate::Account, crate::model::error::EngineError>> {
        match self.prefetched_operation.take() {
            Some(PrefetchedOperation::Account(result)) => Some(result),
            other => {
                self.prefetched_operation = other;
                None
            }
        }
    }

    pub(super) fn take_prefetched_search(
        &mut self,
    ) -> Option<
        Result<
            crate::EnginePagedResult<crate::data::repository::MediaItem>,
            crate::model::error::EngineError,
        >,
    > {
        match self.prefetched_operation.take() {
            Some(PrefetchedOperation::Search(result)) => Some(result),
            other => {
                self.prefetched_operation = other;
                None
            }
        }
    }

    pub(super) fn take_prefetched_playlists(
        &mut self,
    ) -> Option<
        Result<crate::EnginePagedResult<crate::EnginePlaylist>, crate::model::error::EngineError>,
    > {
        match self.prefetched_operation.take() {
            Some(PrefetchedOperation::Playlists(result)) => Some(result),
            other => {
                self.prefetched_operation = other;
                None
            }
        }
    }

    pub(super) fn take_prefetched_history_settings(
        &mut self,
    ) -> Option<Result<crate::EngineHistorySettings, crate::model::error::EngineError>> {
        match self.prefetched_operation.take() {
            Some(PrefetchedOperation::HistorySettings(result)) => Some(result),
            other => {
                self.prefetched_operation = other;
                None
            }
        }
    }

    pub(super) fn take_prefetched_playback(
        &mut self,
    ) -> Option<Result<crate::EnginePlaybackSource, crate::model::error::EngineError>> {
        match self.prefetched_operation.take() {
            Some(PrefetchedOperation::Playback(result)) => Some(result),
            other => {
                self.prefetched_operation = other;
                None
            }
        }
    }

    /// Sets the authenticated backend-neutral saved/liked library boundary.
    pub fn set_library_port(&mut self, port: Arc<dyn crate::LibraryPort>) {
        self.library_port = Some(port);
    }

    /// Sets the authenticated backend-neutral playlist boundary.
    pub fn set_playlist_port(&mut self, port: Arc<dyn crate::PlaylistPort>) {
        self.playlist_port = Some(port);
    }

    /// Sets the authenticated backend-neutral profile boundary.
    pub fn set_profile_port(&mut self, port: Arc<dyn crate::ProfilePort>) {
        self.profile_port = Some(port);
    }

    pub fn set_account_port(&mut self, port: Arc<dyn crate::AccountPort>) {
        self.account_port = Some(port);
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
        let feed_identity_matches = self
            .feed_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) == current_identity.as_ref());
        if !feed_identity_matches {
            snapshot.discovery_results.clear();
            snapshot.for_you_results.clear();
            snapshot.recommendations_results.clear();
            snapshot.discovery_next_page_token = None;
        }
        if self
            .account_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_account_projection(&mut snapshot);
        }
        if self
            .profile_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_profile_projection(&mut snapshot);
        }
        if self
            .history_projection_owner
            .as_ref()
            .is_some_and(|owner| !owner.matches_auth_state(&snapshot.auth_state))
        {
            Self::clear_history_projection(&mut snapshot);
        }
        if self
            .library_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_library_projection(&mut snapshot);
        }
        if self
            .playlist_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            Self::clear_playlist_projection(&mut snapshot);
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
        let feed_identity_matches = self
            .feed_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) == current_identity.as_ref());
        if !feed_identity_matches {
            self.feed_projection_identity = None;
            self.discovery_operation = None;
            self.snapshot.discovery_results.clear();
            self.snapshot.for_you_results.clear();
            self.snapshot.recommendations_results.clear();
            self.snapshot.discovery_next_page_token = None;
        }
        if self
            .account_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            self.account_projection_identity = None;
            self.device_sessions_operation = None;
            Self::clear_account_projection(&mut self.snapshot);
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
            .history_projection_owner
            .as_ref()
            .is_some_and(|owner| !owner.matches_auth_state(&self.snapshot.auth_state))
        {
            self.history_projection_owner = None;
            self.history_operation = None;
            Self::clear_history_projection(&mut self.snapshot);
        }
        if self
            .library_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            self.library_projection_identity = None;
            self.saved_library_operation = None;
            self.liked_library_operation = None;
            Self::clear_library_projection(&mut self.snapshot);
        }
        if self
            .playlist_projection_identity
            .as_ref()
            .is_some_and(|identity| Some(identity) != current_identity.as_ref())
        {
            self.playlist_projection_identity = None;
            self.playlists_operation = None;
            self.playlist_tracks_operation = None;
            Self::clear_playlist_projection(&mut self.snapshot);
        }
    }

    fn clear_profile_projection(snapshot: &mut EngineSnapshot) {
        snapshot.profile = None;
        snapshot.profile_preferences.clear();
        if snapshot.theme_preference.source == crate::PreferenceSource::RemoteProfile {
            snapshot.theme_preference = crate::ThemePreferenceState::default();
        }
    }

    fn clear_account_projection(snapshot: &mut EngineSnapshot) {
        snapshot.protected_account = None;
        snapshot.device_sessions.clear();
        snapshot.device_sessions_next_page_token = None;
    }

    fn clear_history_projection(snapshot: &mut EngineSnapshot) {
        snapshot.history_settings = None;
        snapshot.history_state.generation = snapshot.history_state.generation.saturating_add(1);
        snapshot.history_state.availability = crate::EngineHistoryAvailability::Unknown;
        snapshot.history_state.refresh_state = crate::EngineHistoryRefreshState::Idle;
        snapshot.history_entries.clear();
        snapshot.history_next_page_token = None;
        snapshot.history_deleted_count = 0;
    }

    fn clear_library_projection(snapshot: &mut EngineSnapshot) {
        snapshot.saved_tracks.clear();
        snapshot.saved_tracks_next_page_token = None;
        snapshot.liked_tracks.clear();
        snapshot.liked_tracks_next_page_token = None;
        snapshot.library_pending_track_ids.clear();
    }

    fn clear_playlist_projection(snapshot: &mut EngineSnapshot) {
        snapshot.playlists.clear();
        snapshot.playlists_next_page_token = None;
        snapshot.playlist_tracks.clear();
        snapshot.playlist_tracks_playlist_id = None;
        snapshot.playlist_tracks_next_page_token = None;
        snapshot.playlist_reconciliation = None;
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
    #[instrument(skip(self, command), fields(command_type = %command.command_type.as_wire()))]
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
