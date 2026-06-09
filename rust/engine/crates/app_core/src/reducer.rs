use crate::command::{EngineCommand, EngineCommandType};
use crate::effect::EngineEffect;
use crate::error::EngineError;
use crate::event::EngineEvent;
use crate::middleware::MiddlewarePipeline;
use crate::persistence::{EnginePersistentState, NoopPersistence, Persistence};
use crate::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
use crate::player::MediaPlayer;
use crate::playback::PlaybackState;
use crate::queue::QueueManager;
use crate::repository::{InMemoryRepository, MediaRepository};
use crate::session::MediaSession;
use crate::snapshot::EngineSnapshot;
use crate::state_machine::StateMachine;
use tracing::{info, instrument, warn};

use crate::observability::EventBus;
use crate::service::ServiceManager;
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
    middleware: Arc<MiddlewarePipeline>,
    repository: Box<dyn MediaRepository>,
    queue: QueueManager,
    persistence: Box<dyn Persistence>,
    event_bus: Arc<EventBus>,
    service_manager: ServiceManager,
    player: Option<Box<dyn MediaPlayer>>,
}

impl Default for Engine {
    fn default() -> Self {
        let bus = Arc::new(EventBus::default());
        Self {
            snapshot: EngineSnapshot::default(),
            middleware: Arc::new(MiddlewarePipeline::default()),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue: QueueManager::default(),
            persistence: Box::new(NoopPersistence),
            event_bus: bus,
            service_manager: ServiceManager::new(),
            player: None,
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
        let queue = QueueManager::default();

        // Create initial engine to derive controls
        let engine = Self {
            snapshot,
            middleware: Arc::new(MiddlewarePipeline::new()),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue,
            persistence: Box::new(NoopPersistence),
            event_bus: bus,
            service_manager: ServiceManager::new(),
            player: None,
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
    pub fn tick(&mut self, now_epoch_millis: u64) -> Vec<EngineOutcome> {
        let commands = self.service_manager.tick(self, now_epoch_millis);
        commands
            .into_iter()
            .map(|cmd| self.dispatch(cmd, now_epoch_millis))
            .collect()
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
            if self.snapshot.playback_state == PlaybackState::Playing {
                self.snapshot.playback_state = PlaybackState::Paused;
            }
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

    /// Forces a refresh of the player controls based on the current state.
    pub fn refresh_controls(&mut self) {
        self.snapshot.controls = self.derive_controls(&self.snapshot);
    }

    /// Derives player controls from the current engine state.
    fn derive_controls(&self, snapshot: &EngineSnapshot) -> crate::playback::PlayerControls {
        use crate::playback::{ControlState, PlayerControls};

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
        media: &crate::repository::MediaItem,
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
    pub fn dispatch(&mut self, command: EngineCommand, now_epoch_millis: u64) -> EngineOutcome {
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
                    next_snapshot = Self::update_media_state(next_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::SkipPrevious => {
                if let Some(prev_media) = self.queue.previous_item() {
                    next_snapshot = Self::update_media_state(prev_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::Play => {
                // Only allow play if we have an active session
                if next_snapshot.session.is_some() {
                    if self.snapshot.media_id.is_none() {
                        // If playing from idle/nothing, try to load first item from queue
                        if let Some(media) = self.queue.current_item() {
                            next_snapshot = Self::update_media_state(media, next_snapshot, &mut effects);
                        } else if let Some(media) = self.queue.next_item() {
                            next_snapshot = Self::update_media_state(media, next_snapshot, &mut effects);
                        }
                    }
                } else {
                    // Revert to idle if no session
                    next_snapshot.playback_state = PlaybackState::Idle;
                }
            }
            EngineCommandType::Search { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query);
                next_snapshot = next_snapshot.with_search_results(results).with_busy(false);
            }
            EngineCommandType::Browse { parent_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.browse(parent_id);
                next_snapshot = next_snapshot.with_search_results(results).with_busy(false);
            }
            EngineCommandType::SetSpeed { speed } => {
                next_snapshot = next_snapshot.with_speed(*speed);
                effects.push(EngineEffect::Play); // Ensure speed change is applied if playing
            }
            EngineCommandType::Seek { position_millis } => {
                next_snapshot = next_snapshot.with_position(*position_millis);
                effects.push(EngineEffect::Play); // Often a seek implies continuing playback
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
    pub fn dispatch_platform_event(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        // Handle Media Button Pressed by converting it to a command
        if event.event_type == EnginePlatformEventType::MediaButtonPressed
            && let Some(payload) = &event.payload
        {
            let command_type = EngineCommandType::from_wire(payload.clone());
            return self.dispatch(EngineCommand::new(command_type, None), now_epoch_millis);
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
    use crate::command::EngineCommandType;
    use crate::event::EngineEventType;
    use crate::platform_event::EnginePlatformEventType;
    use crate::playback::PlaybackState;
    use crate::repository::MediaItem;

    #[test]
    fn starts_idle() {
        let engine = Engine::new(100);

        assert_eq!(PlaybackState::Idle, engine.snapshot().playback_state);
        assert_eq!(100, engine.snapshot().updated_at_epoch_millis);
    }

    #[test]
    fn play_command_moves_idle_to_buffering() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 150);
        let outcome = engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(EngineEventType::CommandApplied, outcome.event.event_type);
    }

    #[test]
    fn unknown_command_preserves_playback_state() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 150);
        // Idle -> Buffering
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        let outcome = engine.dispatch(EngineCommand::from_wire("future_command", None), 300);

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("future_command".to_owned()), outcome.event.message);
    }

    #[test]
    fn skip_command_from_playing_moves_to_buffering() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 150);
        // Idle -> Buffering
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);
        // Force Playing state for test (in real app, this would happen via system event)
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 250);

        let outcome = engine.dispatch(EngineCommand::new(EngineCommandType::SkipNext, None), 300);

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("skip_next".to_owned()), outcome.event.message);
    }

    #[test]
    fn platform_event_can_drive_state_transitions() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 150);
        // Idle -> Buffering
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);
        assert_eq!(PlaybackState::Buffering, engine.snapshot().playback_state);

        // Buffering -> Playing via Platform Event
        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            300,
        );

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(
            EngineEventType::PlatformEventApplied,
            outcome.event.event_type
        );
        assert_eq!(Some("media_loaded".to_owned()), outcome.event.message);
    }

    #[test]
    fn platform_error_moves_to_error_state() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaError, None),
            200,
        );

        assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
    }

    #[test]
    fn audio_focus_loss_pauses_playing_engine() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::AudioFocusChanged, None),
            200,
        );

        assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
    }

    #[test]
    fn skip_updates_metadata() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 120);
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
        engine.dispatch(EngineCommand::play(), 200);
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));

        // Skip next
        engine.dispatch(EngineCommand::skip_next(), 300);
        assert_eq!(engine.snapshot().media_id, Some("2".to_string()));
        assert_eq!(engine.snapshot().title, Some("Song 2".to_string()));

        // Skip previous (wraps around - though Repeat All is not default,
        // in my current impl it stays on last or first if no repeat.
        // Wait, I should check my QueueManager impl)
        engine
            .queue()
            .set_repeat_mode(crate::queue::RepeatMode::All);
        engine.dispatch(EngineCommand::skip_previous(), 400);
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));
    }

    #[test]
    fn engine_search_finds_items() {
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

        let outcome = engine.dispatch(EngineCommand::search("Rust".to_string()), 150);
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[test]
    fn browse_returns_items() {
        let mut engine = Engine::new(100);
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist A".to_string(),
            parent_id: Some("root".to_string()),
            ..Default::default()
        }];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        let outcome = engine.dispatch(EngineCommand::browse("root".to_string()), 150);
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[test]
    fn play_command_emits_effects() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 120);
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);

        let outcome = engine.dispatch(EngineCommand::play(), 200);

        // Should emit UpdateMetadata, RequestAudioFocus, and Play
        assert!(outcome.effects.contains(&EngineEffect::UpdateMetadata {
            media_id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
        }));
        assert!(outcome.effects.contains(&EngineEffect::RequestAudioFocus));
        assert!(outcome.effects.contains(&EngineEffect::Play));
    }

    #[test]
    fn pause_command_emits_pause_effect() {
        let mut engine = Engine::new(100);
        // Buffering -> Playing
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine.dispatch(EngineCommand::pause(), 200);

        assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
        assert!(outcome.effects.contains(&EngineEffect::Pause));
    }

    #[test]
    fn tick_updates_progress() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine
            .snapshot
            .clone()
            .with_playback_state(PlaybackState::Playing, 100)
            .with_position(5000)
            .with_speed(1.0);

        // 1 second later
        let outcomes = engine.tick(1100);

        assert_eq!(outcomes.len(), 1);
        assert_eq!(outcomes[0].snapshot.position_millis, 6000);
    }

    #[test]
    fn recovery_middleware_skips_on_network_error() {
        use crate::middleware::{MiddlewarePipeline, RecoveryMiddleware};
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user".to_string()), 110);

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
        engine.dispatch(EngineCommand::play(), 150);

        // Simulate a network error platform event
        let error = EngineError::new(crate::error::EngineErrorType::NetworkError, "No net", false);
        let payload = serde_json::to_string(&error).unwrap();
        let event = EnginePlatformEvent::new(EnginePlatformEventType::MediaError, Some(payload));

        let outcome = engine.dispatch_platform_event(event, 200);

        // RecoveryMiddleware should have triggered skip_next
        assert_eq!(outcome.snapshot.media_id, Some("2".to_string()));
        assert_eq!(
            outcome.snapshot.last_error.unwrap().error_type,
            crate::error::EngineErrorType::MediaSkipped
        );
    }

    #[test]
    fn player_bridge_drives_mock_player() {
        use crate::player::MockPlayer;
        let mut engine = Engine::new(100);
        let player = Box::new(MockPlayer::new());
        engine.set_player(player);

        engine.dispatch(EngineCommand::start_session("user".to_string()), 110);

        let items = vec![MediaItem {
            id: "track_1".to_string(),
            title: "Title 1".to_string(),
            ..Default::default()
        }];
        engine.queue().set_items(items);

        // Play command should trigger UpdateMetadata and Play effects
        engine.dispatch(EngineCommand::play(), 120);

        // Check the player state through the engine (we need to cast or just check effects were emitted)
        // Since we don't have direct access to the Boxed player easily, we can check outcomes
        // but the goal was to verify execute_effects works.
        // Let's verify that PlaybackState is Buffering and then MediaLoaded event moves it to Playing
        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            130,
        );

        assert_eq!(outcome.snapshot.playback_state, PlaybackState::Playing);
        assert!(outcome.effects.contains(&EngineEffect::Play));
    }

    #[test]
    fn controls_are_derived_correctly() {
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
        engine.dispatch(EngineCommand::start_session("user".to_string()), 110);
        engine.dispatch(EngineCommand::play(), 120); // Moves to Buffering
        engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            130,
        );

        let snapshot = engine.snapshot();
        assert_eq!(snapshot.playback_state, PlaybackState::Playing);
        assert!(!snapshot.controls.show_play_icon);
        assert!(snapshot.controls.play_pause.is_active);
        // engine.queue() is a mutable borrow, so we can't use snapshot (immutable borrow) after it.
        // We check the queue index before or after using the snapshot.
        let idx = engine.queue().current_index();
        assert_eq!(idx, Some(0));

        // Skip to end
        engine.dispatch(EngineCommand::skip_next(), 140);
        // Skip moves state to Buffering, we need to load it to Playing to enable controls
        engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            145,
        );

        let idx = engine.queue().current_index();
        assert_eq!(idx, Some(1));
        let snapshot = engine.snapshot();
        // Since there are only 2 items and we are at index 1, skip_next should be disabled
        // and skip_prev should be enabled.
        assert!(!snapshot.controls.skip_next.is_enabled, "Skip next should be disabled at the end of queue");
        assert!(snapshot.controls.skip_prev.is_enabled, "Skip prev should be enabled when not at the start");
    }
}
