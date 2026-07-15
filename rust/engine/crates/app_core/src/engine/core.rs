use crate::data::persistence::{EnginePersistentState, NoopPersistence, Persistence};
use crate::data::queue::QueueManager;
use crate::data::repository::{InMemoryRepository, MediaRepository};
use crate::data::session::MediaSession;
use crate::engine::state_machine::StateMachine;
use crate::middleware::MiddlewarePipeline;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::effect::EngineEffect;
use crate::model::error::EngineError;
use crate::model::event::EngineEvent;
use crate::model::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
use crate::model::playback::PlaybackState;
use crate::model::snapshot::EngineSnapshot;
use crate::networking::{AudioSourceClient, SystemPort};
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
    },
    Browse {
        parent_id: Option<String>,
        genres: Vec<String>,
        page_size: u32,
        next_page_token: Option<crate::EnginePageToken>,
    },
}

// Core engine orchestration root:
// - Public `Engine` API surface lives here.
// - Heavy execution paths are delegated to focused submodules below.
mod controls;
mod dispatch_command;
mod dispatch_platform_event;
mod effects;
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
    system_port: Option<Arc<dyn SystemPort>>,
    catalog_operations: HashMap<String, CatalogOperation>,
    next_catalog_operation_sequence: u64,
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
            system_port: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
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
            system_port: None,
            catalog_operations: HashMap::new(),
            next_catalog_operation_sequence: 0,
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

    /// Sets the backend-neutral public system status port.
    pub fn set_system_port(&mut self, port: Arc<dyn SystemPort>) {
        self.system_port = Some(port);
    }

    /// Sets the media player for the engine.
    pub fn set_player(&mut self, player: Box<dyn MediaPlayer>) {
        self.player = Some(player);
    }

    /// Sets the voice engine for the engine.
    pub fn set_voice_engine(&mut self, voice_engine: Box<dyn VoiceEngine>) {
        self.voice_engine = Some(voice_engine);
    }

    /// Returns the current state of the engine.
    pub fn snapshot(&self) -> &EngineSnapshot {
        &self.snapshot
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
