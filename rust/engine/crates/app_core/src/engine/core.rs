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
use crate::services::player::MediaPlayer;
use crate::services::voice::{VoiceEngine, VoiceInteractionResult};
use tracing::{info, instrument, warn};

use crate::engine::observability::EventBus;
use crate::services::service::ServiceManager;
use std::sync::Arc;

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

    /// Sets the media player for the engine.
    pub fn set_player(&mut self, player: Box<dyn MediaPlayer>) {
        self.player = Some(player);
    }

    /// Sets the voice engine for the engine.
    pub fn set_voice_engine(&mut self, voice_engine: Box<dyn VoiceEngine>) {
        self.voice_engine = Some(voice_engine);
    }

    /// Executes any side effects by driving the player and other components.
    fn execute_effects(&mut self, effects: &[EngineEffect]) {
        if let Some(player) = &mut self.player {
            for effect in effects {
                match effect {
                    EngineEffect::Play => player.play(),
                    EngineEffect::Pause => player.pause(),
                    EngineEffect::Stop => player.stop(),
                    EngineEffect::UpdateMetadata { media_id, .. } => player.prepare(media_id),
                    EngineEffect::Seek(position_millis) => player.seek(*position_millis),
                    EngineEffect::SetSpeed(speed) => player.set_speed(*speed),
                    _ => {}
                }
            }
        }
    }

    /// Tries to restore the engine state from persistence.
    pub fn restore(&mut self) -> Result<bool, String> {
        if let Some(state) = self.persistence.load()? {
            self.snapshot = state.snapshot;
            self.queue = state.queue;

            // If we were playing, we should probably be paused after restore for safety in AAOS
            // Unless auto_resume is enabled and we want to actually start playing.
            if self.snapshot.playback_state == PlaybackState::Playing {
                if self.config.auto_resume {
                    // Stay in Playing state (or move to Buffering if we need to reload)
                    // We'll emit a Play effect later if we want to actually drive the player.
                    // For now, let's move to Buffering to trigger a clean reload.
                    self.snapshot.playback_state = PlaybackState::Buffering;

                    // Emit effects to sync the player with the restored state
                    if let Some(media_id) = &self.snapshot.media_id {
                        let effects = vec![
                            EngineEffect::UpdateMetadata {
                                media_id: media_id.clone(),
                                title: self.snapshot.title.clone().unwrap_or_default(),
                                artist: self.snapshot.artist.clone().unwrap_or_default(),
                            },
                            EngineEffect::Seek(self.snapshot.position_millis),
                            EngineEffect::Play,
                        ];
                        self.execute_effects(&effects);
                    }
                } else {
                    self.snapshot.playback_state = PlaybackState::Paused;
                    // Sync metadata even if paused
                    if let Some(media_id) = &self.snapshot.media_id {
                        let effects = vec![
                            EngineEffect::UpdateMetadata {
                                media_id: media_id.clone(),
                                title: self.snapshot.title.clone().unwrap_or_default(),
                                artist: self.snapshot.artist.clone().unwrap_or_default(),
                            },
                            EngineEffect::Seek(self.snapshot.position_millis),
                        ];
                        self.execute_effects(&effects);
                    }
                }
            } else if let Some(media_id) = &self.snapshot.media_id {
                // Not playing, but still sync metadata and position if we have media
                let effects = vec![
                    EngineEffect::UpdateMetadata {
                        media_id: media_id.clone(),
                        title: self.snapshot.title.clone().unwrap_or_default(),
                        artist: self.snapshot.artist.clone().unwrap_or_default(),
                    },
                    EngineEffect::Seek(self.snapshot.position_millis),
                ];
                self.execute_effects(&effects);
            }

            self.refresh_controls();
            Ok(true)
        } else {
            Ok(false)
        }
    }

    /// Saves the current engine state to persistence.
    pub fn save(&self) -> Result<(), String> {
        let state = EnginePersistentState {
            snapshot: self.snapshot.clone(),
            queue: self.queue.clone(),
        };
        self.persistence.save(&state)
    }

    /// Returns the current state of the engine.
    pub fn snapshot(&self) -> &EngineSnapshot {
        &self.snapshot
    }

    /// Returns the current configuration of the engine.
    pub fn config(&self) -> &crate::model::config::EngineConfig {
        &self.config
    }

    /// Forces a refresh of the player controls based on the current state.
    pub fn refresh_controls(&mut self) {
        self.snapshot.controls = self.derive_controls(&self.snapshot);
    }

    /// Derives player controls from the current engine state.
    fn derive_controls(&self, snapshot: &EngineSnapshot) -> crate::model::playback::PlayerControls {
        use crate::model::playback::{ControlState, PlayerControls};

        let can_dispatch = snapshot.can_dispatch();
        let is_playing = snapshot.playback_state == PlaybackState::Playing;
        let is_buffering = snapshot.playback_state == PlaybackState::Buffering;

        PlayerControls {
            play_pause: ControlState {
                is_visible: true,
                is_enabled: can_dispatch || is_buffering, // Can pause while buffering
                is_active: is_playing,
            },
            skip_next: ControlState {
                is_visible: true,
                is_enabled: can_dispatch && self.queue.has_next(),
                is_active: false,
            },
            skip_prev: ControlState {
                is_visible: true,
                is_enabled: can_dispatch && self.queue.has_previous(),
                is_active: false,
            },
            show_play_icon: !is_playing,
        }
    }

    /// Helper to update the snapshot with new media and emit metadata effects.
    fn update_media_state(
        media: &crate::data::repository::MediaItem,
        snapshot: EngineSnapshot,
        effects: &mut Vec<EngineEffect>,
    ) -> EngineSnapshot {
        let next_snapshot = snapshot.with_media(media.clone());
        effects.push(EngineEffect::UpdateMetadata {
            media_id: media.id.clone(),
            title: media.title.clone(),
            artist: media.artist.clone(),
        });
        next_snapshot
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
        info!(
            "Dispatching command: {:?} at {}",
            command.command_type, now_epoch_millis
        );
        let middleware = Arc::clone(&self.middleware);
        middleware.before_dispatch(self, &command);

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_command(prev_playback_state, &command.command_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis)
            .with_error(None)
            .with_busy(false);

        let mut effects = Vec::new();

        // Update metadata based on command
        match &command.command_type {
            EngineCommandType::StartSession { user_id } => {
                let session_id = format!("session-{}", now_epoch_millis);
                let session =
                    MediaSession::new(session_id.clone(), user_id.clone(), now_epoch_millis);
                next_snapshot = next_snapshot.with_session(Some(session));
                effects.push(EngineEffect::SessionStarted { session_id });
            }
            EngineCommandType::EndSession => {
                next_snapshot = next_snapshot.with_session(None);
                effects.push(EngineEffect::SessionEnded);
            }
            EngineCommandType::SkipNext => {
                if let Some(next_media) = self.queue.next_item() {
                    next_snapshot =
                        Self::update_media_state(next_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::SkipPrevious => {
                if let Some(prev_media) = self.queue.previous_item() {
                    next_snapshot =
                        Self::update_media_state(prev_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::Play => {
                // Only allow play if we have an active session
                if next_snapshot.session.is_some() {
                    if self.snapshot.media_id.is_none() {
                        // If playing from idle/nothing, try to load first item from queue
                        if let Some(media) = self.queue.current_item() {
                            next_snapshot =
                                Self::update_media_state(media, next_snapshot, &mut effects);
                        } else if let Some(media) = self.queue.next_item() {
                            next_snapshot =
                                Self::update_media_state(media, next_snapshot, &mut effects);
                        }
                    }
                } else {
                    // Revert to idle if no session
                    next_snapshot.playback_state = PlaybackState::Idle;
                }
            }
            EngineCommandType::Search { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query).await;
                next_snapshot = next_snapshot
                    .with_search_results(results.unwrap_or_default())
                    .with_busy(false);
            }
            EngineCommandType::Browse { parent_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.browse(parent_id).await;
                next_snapshot = next_snapshot
                    .with_search_results(results.unwrap_or_default())
                    .with_busy(false);
            }
            EngineCommandType::SetSpeed { speed } => {
                next_snapshot = next_snapshot.with_speed(*speed);
                effects.push(EngineEffect::Play); // Ensure speed change is applied if playing
            }
            EngineCommandType::Seek { position_millis } => {
                next_snapshot = next_snapshot.with_position(*position_millis);
                effects.push(EngineEffect::Play); // Often a seek implies continuing playback
            }
            EngineCommandType::UpdateConfig { config } => {
                self.config = config.clone();
                info!("Engine configuration updated: {:?}", config);
            }
            EngineCommandType::StartVoiceInteraction => {
                if let Some(ve) = &mut self.voice_engine {
                    ve.reset();
                    // Provide contextual metadata to help resolution
                    let context = if let (Some(title), Some(artist)) =
                        (&self.snapshot.title, &self.snapshot.artist)
                    {
                        Some((title.clone(), artist.clone()))
                    } else {
                        None
                    };
                    ve.set_context(context);
                }
                effects.push(EngineEffect::DuckAudio);
                effects.push(EngineEffect::StartAudioCapture);
                info!("Voice interaction started (audio ducked)");
            }
            EngineCommandType::StopVoiceInteraction => {
                let mut resolved_cmd = None;
                if let Some(ve) = &mut self.voice_engine {
                    match ve.finish() {
                        Ok(VoiceInteractionResult::Command(cmd)) => {
                            info!("Voice command determined: {:?}", cmd);
                            resolved_cmd = Some(cmd);
                        }
                        Ok(VoiceInteractionResult::Error(err)) => {
                            warn!("Voice interaction error: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                        Ok(VoiceInteractionResult::NoMatch) => {
                            info!("No voice command match found");
                            effects.push(EngineEffect::NotifyUser {
                                message: "I didn't catch that. Could you repeat?".to_string(),
                            });
                        }
                        Err(err) => {
                            warn!("Voice engine failure: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                    }
                } else {
                    // Fallback: if no internal voice engine, maybe the platform handled it.
                    info!("Voice interaction stopped (no internal engine)");
                }

                if let Some(cmd) = resolved_cmd {
                    // Recursively dispatch the resolved command (boxed to allow async recursion)
                    let outcome = Box::pin(self.dispatch(cmd, now_epoch_millis)).await;
                    // Merge effects and update snapshot from the outcome
                    effects.extend(outcome.effects);
                    next_snapshot = outcome.snapshot;
                }

                effects.push(EngineEffect::StopAudioCapture);
                effects.push(EngineEffect::UnduckAudio);
                next_snapshot = next_snapshot.with_busy(false).with_voice_hypothesis(None);
            }
            EngineCommandType::ProcessVoiceAudio { chunk } => {
                if let Some(ve) = &mut self.voice_engine {
                    let _ = ve.process_audio_chunk(chunk).map_err(|e| {
                        warn!("Failed to process voice audio chunk: {}", e);
                    });
                    // Update hypothesis in snapshot for real-time UI feedback
                    let hypothesis = ve.get_partial_hypothesis();
                    next_snapshot = next_snapshot.with_voice_hypothesis(if hypothesis.is_empty() {
                        None
                    } else {
                        Some(hypothesis)
                    });
                }
            }
            EngineCommandType::VoicePlay { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query).await.unwrap_or_default();
                if let Some(first) = results.first() {
                    let media = first.clone();
                    next_snapshot = Self::update_media_state(&media, next_snapshot, &mut effects);
                    next_snapshot.playback_state = PlaybackState::Buffering;
                } else {
                    info!("Voice search found no results for: {}", query);
                    effects.push(EngineEffect::NotifyUser {
                        message: format!("No results found for {}", query),
                    });
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::PlayMediaById { media_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                if let Some(media) = self.repository.get_by_id(media_id) {
                    next_snapshot = Self::update_media_state(&media, next_snapshot, &mut effects);
                    next_snapshot.playback_state = PlaybackState::Buffering;
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::SetSleepTimer { duration_millis } => {
                use crate::services::service::SleepTimerService;
                if let Some(timer_service) =
                    self.service_manager.find_service::<SleepTimerService>()
                {
                    match duration_millis {
                        Some(duration) => {
                            let fire_at = now_epoch_millis + duration;
                            timer_service.set_timer(fire_at);
                            info!("Sleep timer set to fire in {}ms", duration);
                        }
                        None => {
                            timer_service.set_timer(0);
                            info!("Sleep timer cancelled");
                        }
                    }
                }
            }
            _ => {}
        }

        // Logic-based action emission
        match (prev_playback_state, next_snapshot.playback_state) {
            (prev, next) if prev != next => match next {
                PlaybackState::Buffering => {
                    effects.push(EngineEffect::RequestAudioFocus);
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Playing => {
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Paused => {
                    effects.push(EngineEffect::Pause);
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                    effects.push(EngineEffect::AbandonAudioFocus);
                }
                _ => {}
            },
            _ => {}
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);

        if self.snapshot.playback_state != prev_playback_state {
            info!(
                "Playback state transition: {:?} -> {:?}",
                prev_playback_state, self.snapshot.playback_state
            );
        }

        let mut outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(Some(command.command_type.as_wire().to_owned())),
            effects,
        };

        let middleware = Arc::clone(&self.middleware);
        middleware.after_dispatch(self, &mut outcome);

        // Execute side effects on the player
        self.execute_effects(&outcome.effects);

        outcome
    }

    /// Dispatches a platform-level event to the engine.
    pub async fn dispatch_platform_event(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        // Handle Media Button Pressed by converting it to a command
        if event.event_type == EnginePlatformEventType::MediaButtonPressed
            && let Some(payload) = &event.payload
        {
            let command_type = EngineCommandType::from_wire(payload.clone());
            return self
                .dispatch(EngineCommand::new(command_type, None), now_epoch_millis)
                .await;
        }

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_platform_event(prev_playback_state, &event.event_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        if next_playback_state == PlaybackState::Error {
            if let Some(payload) = &event.payload {
                let error = serde_json::from_str::<EngineError>(payload)
                    .unwrap_or_else(|_| EngineError::player_error(payload.clone()));
                next_snapshot = next_snapshot.with_error(Some(error));
            } else {
                next_snapshot = next_snapshot
                    .with_error(Some(EngineError::player_error("Unknown platform error")));
            }
        } else {
            next_snapshot = next_snapshot.with_error(None);
        }

        let mut effects = Vec::new();
        match (prev_playback_state, next_playback_state) {
            (prev, next) if prev != next => match next {
                PlaybackState::Paused => {
                    effects.push(EngineEffect::Pause);
                }
                PlaybackState::Playing => {
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                }
                _ => {}
            },
            _ => {}
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);

        let middleware = Arc::clone(&self.middleware);
        let outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
            effects,
        };

        // Note: Platform events also pass through after_dispatch for consistency
        let mut outcome = outcome;
        middleware.after_dispatch(self, &mut outcome);

        // Execute side effects on the player
        self.execute_effects(&outcome.effects);

        outcome
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::data::repository::MediaItem;
    use crate::model::command::EngineCommandType;
    use crate::model::event::EngineEventType;
    use crate::model::platform_event::EnginePlatformEventType;
    use crate::model::playback::PlaybackState;

    #[test]
    fn starts_idle() {
        let engine = Engine::new(100);

        assert_eq!(PlaybackState::Idle, engine.snapshot().playback_state);
        assert_eq!(100, engine.snapshot().updated_at_epoch_millis);
    }

    #[tokio::test]
    async fn play_command_moves_idle_to_buffering() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 150)
            .await;
        let outcome = engine
            .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
            .await;

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(EngineEventType::CommandApplied, outcome.event.event_type);
    }

    #[tokio::test]
    async fn unknown_command_preserves_playback_state() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 150)
            .await;
        // Idle -> Buffering
        engine
            .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
            .await;

        let outcome = engine
            .dispatch(EngineCommand::from_wire("future_command", None), 300)
            .await;

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("future_command".to_owned()), outcome.event.message);
    }

    #[tokio::test]
    async fn skip_command_from_playing_moves_to_buffering() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 150)
            .await;
        // Idle -> Buffering
        engine
            .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
            .await;
        // Force Playing state for test (in real app, this would happen via system event)
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 250);

        let outcome = engine
            .dispatch(EngineCommand::new(EngineCommandType::SkipNext, None), 300)
            .await;

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("skip_next".to_owned()), outcome.event.message);
    }

    #[tokio::test]
    async fn platform_event_can_drive_state_transitions() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 150)
            .await;
        // Idle -> Buffering
        engine
            .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
            .await;
        assert_eq!(PlaybackState::Buffering, engine.snapshot().playback_state);

        // Buffering -> Playing via Platform Event
        let outcome = engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
                300,
            )
            .await;

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(
            EngineEventType::PlatformEventApplied,
            outcome.event.event_type
        );
        assert_eq!(Some("media_loaded".to_owned()), outcome.event.message);
    }

    #[tokio::test]
    async fn platform_error_moves_to_error_state() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaError, None),
                200,
            )
            .await;

        assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
    }

    #[tokio::test]
    async fn audio_focus_loss_pauses_playing_engine() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::AudioFocusChanged, None),
                200,
            )
            .await;

        assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
    }

    #[tokio::test]
    async fn skip_updates_metadata() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 120)
            .await;
        let items = vec![
            MediaItem {
                id: "1".to_string(),
                title: "Song 1".to_string(),
                artist: "Artist 1".to_string(),
                ..Default::default()
            },
            MediaItem {
                id: "2".to_string(),
                title: "Song 2".to_string(),
                artist: "Artist 2".to_string(),
                ..Default::default()
            },
        ];
        engine.queue().set_items(items);

        // Initial play
        engine.dispatch(EngineCommand::play(), 200).await;
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));

        // Skip next
        engine.dispatch(EngineCommand::skip_next(), 300).await;
        assert_eq!(engine.snapshot().media_id, Some("2".to_string()));
        assert_eq!(engine.snapshot().title, Some("Song 2".to_string()));

        // Skip previous (wraps around - though Repeat All is not default,
        // in my current impl it stays on last or first if no repeat.
        // Wait, I should check my QueueManager impl)
        engine
            .queue()
            .set_repeat_mode(crate::data::queue::RepeatMode::All);
        engine.dispatch(EngineCommand::skip_previous(), 400).await;
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));
    }

    #[tokio::test]
    async fn engine_search_finds_items() {
        let mut engine = Engine::new(100);
        let items = vec![
            MediaItem {
                id: "1".to_string(),
                title: "Rust Song".to_string(),
                artist: "The Developers".to_string(),
                ..Default::default()
            },
            MediaItem {
                id: "2".to_string(),
                title: "Kotlin Blues".to_string(),
                artist: "The Developers".to_string(),
                ..Default::default()
            },
        ];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        let outcome = engine
            .dispatch(EngineCommand::search("Rust".to_string()), 150)
            .await;
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[tokio::test]
    async fn browse_returns_items() {
        let mut engine = Engine::new(100);
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist A".to_string(),
            parent_id: Some("root".to_string()),
            ..Default::default()
        }];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        let outcome = engine
            .dispatch(EngineCommand::browse("root".to_string()), 150)
            .await;
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[tokio::test]
    async fn play_command_emits_effects() {
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 120)
            .await;
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);

        let outcome = engine.dispatch(EngineCommand::play(), 200).await;

        // Should emit UpdateMetadata, RequestAudioFocus, and Play
        assert!(outcome.effects.contains(&EngineEffect::UpdateMetadata {
            media_id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
        }));
        assert!(outcome.effects.contains(&EngineEffect::RequestAudioFocus));
        assert!(outcome.effects.contains(&EngineEffect::Play));
    }

    #[tokio::test]
    async fn pause_command_emits_pause_effect() {
        let mut engine = Engine::new(100);
        // Buffering -> Playing
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine.dispatch(EngineCommand::pause(), 200).await;

        assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
        assert!(outcome.effects.contains(&EngineEffect::Pause));
    }

    #[tokio::test]
    async fn tick_updates_progress() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 100)
            .with_position(5000)
            .with_speed(1.0);

        // 1 second later
        let outcomes = engine.tick(1100).await;

        assert_eq!(outcomes.len(), 1);
        assert_eq!(outcomes[0].snapshot.position_millis, 6000);
    }

    #[tokio::test]
    async fn recovery_middleware_skips_on_network_error() {
        use crate::middleware::{MiddlewarePipeline, RecoveryMiddleware};
        let mut engine = Engine::new(100);
        engine
            .dispatch(EngineCommand::start_session("user".to_string()), 110)
            .await;

        let items = vec![
            MediaItem {
                id: "1".to_string(),
                title: "Bad Track".to_string(),
                ..Default::default()
            },
            MediaItem {
                id: "2".to_string(),
                title: "Good Track".to_string(),
                ..Default::default()
            },
        ];
        engine.queue().set_items(items);

        let mut pipeline = MiddlewarePipeline::new();
        pipeline.add(Box::new(RecoveryMiddleware));
        engine.set_middleware(pipeline);

        // Initial play
        engine.dispatch(EngineCommand::play(), 150).await;

        // Simulate a network error platform event
        let error = EngineError::new(
            crate::model::error::EngineErrorType::NetworkError,
            "No net",
            false,
        );
        let payload = serde_json::to_string(&error).unwrap();
        let event = EnginePlatformEvent::new(EnginePlatformEventType::MediaError, Some(payload));

        let outcome = engine.dispatch_platform_event(event, 200).await;

        // RecoveryMiddleware should have triggered skip_next
        assert_eq!(outcome.snapshot.media_id, Some("2".to_string()));
        assert_eq!(
            outcome.snapshot.last_error.unwrap().error_type,
            crate::model::error::EngineErrorType::MediaSkipped
        );
        assert_eq!(
            outcome.event.message.as_deref(),
            Some("recovered_from_network_error: skipped_to_next_track")
        );
    }

    #[tokio::test]
    async fn player_bridge_drives_mock_player() {
        use crate::services::player::MockPlayer;
        let mut engine = Engine::new(100);
        let player = Box::new(MockPlayer::new());
        engine.set_player(player);

        engine
            .dispatch(EngineCommand::start_session("user".to_string()), 110)
            .await;

        let items = vec![MediaItem {
            id: "track_1".to_string(),
            title: "Title 1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);

        // Play command should trigger UpdateMetadata and Play effects
        engine.dispatch(EngineCommand::play(), 120).await;

        // Check the player state through the engine (we need to cast or just check effects were emitted)
        // Since we don't have direct access to the Boxed player easily, we can check outcomes
        // but the goal was to verify execute_effects works.
        // Let's verify that PlaybackState is Buffering and then the MediaLoaded event moves it to Playing
        let outcome = engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
                130,
            )
            .await;

        assert_eq!(outcome.snapshot.playback_state, PlaybackState::Playing);
        assert!(outcome.effects.contains(&EngineEffect::Play));
    }

    #[tokio::test]
    async fn controls_are_derived_correctly() {
        let mut engine = Engine::new(100);
        let items = vec![
            MediaItem {
                id: "1".to_string(),
                ..Default::default()
            },
            MediaItem {
                id: "2".to_string(),
                ..Default::default()
            },
        ];
        engine.queue().set_items(items);
        engine.refresh_controls();

        // Idle state
        let snapshot = engine.snapshot();
        assert!(snapshot.controls.show_play_icon);
        assert!(snapshot.controls.play_pause.is_enabled);
        assert!(!snapshot.controls.skip_prev.is_enabled);
        assert!(snapshot.controls.skip_next.is_enabled);

        // Playing state
        engine
            .dispatch(EngineCommand::start_session("user".to_string()), 110)
            .await;
        engine.dispatch(EngineCommand::play(), 120).await; // Moves to Buffering
        engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
                130,
            )
            .await;

        let snapshot = engine.snapshot();
        assert_eq!(snapshot.playback_state, PlaybackState::Playing);
        assert!(!snapshot.controls.show_play_icon);
        assert!(snapshot.controls.play_pause.is_active);
        // engine.queue() is a mutable borrow, so we can't use snapshot (immutable borrow) after it.
        // We check the queue index before or after using the snapshot.
        let idx = engine.queue().current_index();
        assert_eq!(idx, Some(0));

        // Skip to end
        engine.dispatch(EngineCommand::skip_next(), 140).await;
        // Skip moves state to Buffering, we need to load it to Playing to enable controls
        engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
                145,
            )
            .await;

        let idx = engine.queue().current_index();
        assert_eq!(idx, Some(1));
        let snapshot = engine.snapshot();
        // Since there are only 2 items, and we are at index 1, skip_next should be disabled
        // and skip_prev should be enabled.
        assert!(
            !snapshot.controls.skip_next.is_enabled,
            "Skip next should be disabled at the end of queue"
        );
        assert!(
            snapshot.controls.skip_prev.is_enabled,
            "Skip prev should be enabled when not at the start"
        );
    }

    #[tokio::test]
    async fn update_config_updates_snapshot() {
        let mut engine = Engine::new(100);
        let new_config = crate::model::config::EngineConfig::new()
            .with_vehicle_name("Model S".to_string())
            .with_hifi(true);

        engine
            .dispatch(EngineCommand::update_config(new_config.clone()), 150)
            .await;

        let config = engine.config();
        assert_eq!(config.vehicle_name, "Model S");
        assert!(config.hifi_enabled);
    }

    #[tokio::test]
    async fn voice_play_starts_playback() {
        let mut engine = Engine::new(100);
        // Set up repository with some items
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
            ..Default::default()
        }];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        // "Song 1" matches ID "1" in InMemoryRepository
        engine
            .dispatch(EngineCommand::voice_play("Song 1".to_string()), 150)
            .await;

        let snapshot = engine.snapshot();
        assert_eq!(snapshot.playback_state, PlaybackState::Buffering);
        assert_eq!(snapshot.media_id, Some("1".to_string()));
    }

    #[tokio::test]
    async fn voice_play_no_results_emits_notify() {
        let mut engine = Engine::new(100);
        engine.set_repository(Box::new(InMemoryRepository::new(vec![])));

        let outcome = engine
            .dispatch(EngineCommand::voice_play("NonExistent".to_string()), 150)
            .await;

        assert!(outcome.effects.iter().any(|e| match e {
            EngineEffect::NotifyUser { message } => message.contains("No results found"),
            _ => false,
        }));
        // State should remain Idle (default)
        assert_eq!(engine.snapshot().playback_state, PlaybackState::Idle);
    }

    #[tokio::test]
    async fn voice_interaction_lifecycle() {
        use crate::data::repository::MediaItem;
        use crate::services::voice::MockVoiceEngine;

        let mut engine = Engine::new(100);
        engine.set_voice_engine(Box::new(MockVoiceEngine::new()));
        engine.set_repository(Box::new(InMemoryRepository::new(vec![MediaItem {
            id: "jazz_1".to_string(),
            title: "Jazz Song".to_string(),
            artist: "Jazz Artist".to_string(),
            ..Default::default()
        }])));

        // 1. Start interaction
        let outcome = engine
            .dispatch(EngineCommand::start_voice_interaction(), 110)
            .await;
        // Busy state is cleared at the end of dispatch, so we check if it was set
        // Actually, StartVoiceInteraction does NOT set busy=true in its current implementation,
        // it only pushes effects. Let's verify what it does.
        assert!(outcome.effects.contains(&EngineEffect::DuckAudio));
        assert!(outcome.effects.contains(&EngineEffect::StartAudioCapture));

        // 2. Process audio
        engine
            .dispatch(EngineCommand::process_voice_audio(vec![0; 100]), 120)
            .await;

        // 3. Stop interaction (triggers finish -> VoicePlay("jazz"))
        let outcome = engine
            .dispatch(EngineCommand::stop_voice_interaction(), 130)
            .await;
        assert!(!outcome.snapshot.is_busy);
        assert!(outcome.effects.contains(&EngineEffect::StopAudioCapture));
        assert!(outcome.effects.contains(&EngineEffect::UnduckAudio));

        // Verify it resolved to a play command
        assert_eq!(outcome.snapshot.playback_state, PlaybackState::Buffering);
        assert_eq!(outcome.snapshot.media_id, Some("jazz_1".to_string()));
    }

    #[tokio::test]
    async fn sleep_timer_pauses_playback() {
        let mut engine = Engine::new(100);
        let items = vec![MediaItem {
            id: "1".to_string(),
            ..Default::default()
        }];
        engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
        engine.queue().set_items(items); // Manually set queue
        engine
            .dispatch(EngineCommand::start_session("user1".to_string()), 50)
            .await;

        engine.dispatch(EngineCommand::play(), 100).await;
        // Buffering -> Loaded -> Playing
        engine
            .dispatch_platform_event(
                EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
                110,
            )
            .await;
        assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);

        // Set sleep timer for 500ms
        engine
            .dispatch(EngineCommand::set_sleep_timer(Some(500)), 150)
            .await;

        // Tick before it fires
        engine.tick(300).await;
        assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);

        // Tick after it fires (150 + 500 = 650)
        engine.tick(700).await;
        assert_eq!(engine.snapshot().playback_state, PlaybackState::Paused);
    }

    #[tokio::test]
    async fn engine_save_and_restore_cycle() {
        use crate::data::repository::MediaItem;
        use crate::test_utils::MockPersistence;
        use std::sync::Arc;

        let persistence = Arc::new(MockPersistence::new());

        let mut engine = Engine::new(100);
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "T1".to_string(),
            artist: "A1".to_string(),
            ..Default::default()
        }];
        engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
        engine.queue().set_items(items);
        engine.set_persistence(Box::new(persistence.clone()));

        engine
            .dispatch(EngineCommand::start_session("u1".to_string()), 150)
            .await;
        engine.dispatch(EngineCommand::play(), 200).await;
        engine
            .dispatch_platform_event(
                crate::model::platform_event::EnginePlatformEvent::new(
                    crate::model::platform_event::EnginePlatformEventType::MediaLoaded,
                    None,
                ),
                250,
            )
            .await;

        assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);
        engine.save().expect("Save failed");

        // New engine, restore
        let mut engine2 = Engine::new(1000);
        // Explicitly set config to match engine1
        let mut config2 = crate::model::config::EngineConfig::new();
        config2.auto_resume = false;
        engine2
            .dispatch(EngineCommand::update_config(config2), 1010)
            .await;

        engine2.set_persistence(Box::new(persistence));
        let restored = engine2.restore().expect("Restore failed");

        assert!(restored);
        // By default, restore moves Playing -> Paused (unless auto_resume is true)
        assert_eq!(engine2.snapshot().playback_state, PlaybackState::Paused);
        assert_eq!(engine2.snapshot().media_id, Some("1".to_string()));
    }

    #[tokio::test]
    async fn engine_auto_resume_on_restore() {
        use crate::data::repository::MediaItem;
        use crate::test_utils::MockPersistence;
        use std::sync::Arc;

        let persistence = Arc::new(MockPersistence::new());

        let mut engine = Engine::new(100);
        engine.set_persistence(Box::new(persistence.clone()));
        // Enable auto_resume
        let mut config = crate::model::config::EngineConfig::new();
        config.auto_resume = true;
        engine
            .dispatch(EngineCommand::update_config(config), 110)
            .await;

        let items = vec![MediaItem {
            id: "1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);
        engine
            .dispatch(EngineCommand::start_session("u1".to_string()), 150)
            .await;
        engine.dispatch(EngineCommand::play(), 200).await;
        engine
            .dispatch_platform_event(
                crate::model::platform_event::EnginePlatformEvent::new(
                    crate::model::platform_event::EnginePlatformEventType::MediaLoaded,
                    None,
                ),
                250,
            )
            .await;

        engine.save().expect("Save failed");

        // Restore in new engine with auto_resume config
        let mut engine2 = Engine::new(1000);
        let mut config2 = crate::model::config::EngineConfig::new();
        config2.auto_resume = true;
        engine2
            .dispatch(EngineCommand::update_config(config2), 1010)
            .await;
        engine2.set_persistence(Box::new(persistence));

        let restored = engine2.restore().expect("Restore failed");
        assert!(restored);
        // Should be Buffering (initiating reload) if auto_resume is true
        assert_eq!(engine2.snapshot().playback_state, PlaybackState::Buffering);
    }

    #[tokio::test]
    async fn voice_interaction_error_handling() {
        use crate::services::voice::MockVoiceEngine;

        let mut engine = Engine::new(100);
        let mut ve = MockVoiceEngine::new();
        ve.set_fail(true);
        engine.set_voice_engine(Box::new(ve));

        engine
            .dispatch(EngineCommand::start_voice_interaction(), 110)
            .await;
        let _outcome = engine
            .dispatch(EngineCommand::process_voice_audio(vec![0; 100]), 120)
            .await;

        // Process audio itself might not fail yet, but stop_voice_interaction will call finish()
        let outcome = engine
            .dispatch(EngineCommand::stop_voice_interaction(), 130)
            .await;

        assert!(outcome.effects.iter().any(|e| match e {
            EngineEffect::NotifyUser { message } => message.contains("Failed to recognize speech"),
            _ => false,
        }));
        assert_eq!(engine.snapshot().playback_state, PlaybackState::Idle);
    }

    #[tokio::test]
    async fn persistence_integration_test() {
        use crate::data::repository::MediaItem;
        use crate::test_utils::MockPersistence;
        use std::sync::Arc;

        let persistence = Arc::new(MockPersistence::new());
        let mut engine = Engine::new(100);
        engine.set_persistence(Box::new(persistence.clone()));

        // 1. Setup state
        let items = vec![MediaItem {
            id: "1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);
        engine
            .dispatch(EngineCommand::start_session("u1".to_string()), 150)
            .await;

        // 2. Save
        engine.save().expect("First save");

        // 3. Verify persistence contains data
        let state = persistence.load().unwrap().expect("Should have data");
        assert_eq!(state.snapshot.media_id, None); // Haven't played yet

        // 4. Play and save again
        engine.dispatch(EngineCommand::play(), 200).await;
        engine.save().expect("Second save");

        let state2 = persistence.load().unwrap().expect("Should have data");
        assert_eq!(state2.snapshot.media_id, Some("1".to_string()));
    }
}
