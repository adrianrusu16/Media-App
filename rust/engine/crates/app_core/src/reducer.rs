use crate::command::{EngineCommand, EngineCommandType};
use crate::effect::EngineEffect;
use crate::error::EngineError;
use crate::event::EngineEvent;
use crate::middleware::MiddlewarePipeline;
use crate::persistence::{EnginePersistentState, NoopPersistence, Persistence};
use crate::platform_event::{EnginePlatformEvent, EnginePlatformEventType};
use crate::playback::PlaybackState;
use crate::queue::QueueManager;
use crate::repository::{InMemoryRepository, MediaRepository};
use crate::session::MediaSession;
use crate::snapshot::EngineSnapshot;
use crate::state_machine::StateMachine;

/// Result of an engine operation, containing the new state and an event to be broadcasted.
#[derive(Clone, Debug, Eq, PartialEq)]
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
    middleware: MiddlewarePipeline,
    repository: Box<dyn MediaRepository>,
    queue: QueueManager,
    persistence: Box<dyn Persistence>,
}

impl Default for Engine {
    fn default() -> Self {
        Self {
            snapshot: EngineSnapshot::default(),
            middleware: MiddlewarePipeline::default(),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue: QueueManager::default(),
            persistence: Box::new(NoopPersistence),
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
        Self {
            snapshot: EngineSnapshot::idle(now_epoch_millis),
            middleware: MiddlewarePipeline::new(),
            repository: Box::new(InMemoryRepository::new(vec![])),
            queue: QueueManager::default(),
            persistence: Box::new(NoopPersistence),
        }
    }

    /// Returns the queue manager for the engine.
    pub fn queue(&mut self) -> &mut QueueManager {
        &mut self.queue
    }

    /// Sets the media repository for the engine.
    pub fn set_repository(&mut self, repository: Box<dyn MediaRepository>) {
        self.repository = repository;
    }

    /// Sets the middleware pipeline for the engine.
    pub fn set_middleware(&mut self, pipeline: MiddlewarePipeline) {
        self.middleware = pipeline;
    }

    /// Sets the persistence for the engine.
    pub fn set_persistence(&mut self, persistence: Box<dyn Persistence>) {
        self.persistence = persistence;
    }

    /// Tries to restore the engine state from persistence.
    pub fn restore(&mut self) -> Result<bool, String> {
        if let Some(state) = self.persistence.load()? {
            self.snapshot = state.snapshot;
            self.queue = state.queue;
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

    /// Dispatches a command to the engine, returning the outcome.
    ///
    /// This is the primary way to interact with the engine.
    pub fn dispatch(&mut self, command: EngineCommand, now_epoch_millis: u64) -> EngineOutcome {
        self.middleware.before_dispatch(self, &command);

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_command(prev_playback_state, &command.command_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis)
            .with_error(None);

        let mut effects = Vec::new();

        // Update metadata based on command
        match &command.command_type {
            crate::command::EngineCommandType::StartSession { user_id } => {
                let session_id = format!("session-{}", now_epoch_millis);
                let session = MediaSession::new(session_id.clone(), user_id.clone(), now_epoch_millis);
                next_snapshot = next_snapshot.with_session(Some(session));
                effects.push(EngineEffect::SessionStarted { session_id });
            }
            crate::command::EngineCommandType::EndSession => {
                next_snapshot = next_snapshot.with_session(None);
                effects.push(EngineEffect::SessionEnded);
            }
            crate::command::EngineCommandType::SkipNext => {
                if let Some(next_media) = self.queue.next_item() {
                    next_snapshot = next_snapshot.with_media(next_media.clone());
                    effects.push(EngineEffect::UpdateMetadata {
                        media_id: next_media.id.clone(),
                        title: next_media.title.clone(),
                        artist: next_media.artist.clone(),
                    });
                }
            }
            crate::command::EngineCommandType::SkipPrevious => {
                if let Some(prev_media) = self.queue.previous_item() {
                    next_snapshot = next_snapshot.with_media(prev_media.clone());
                    effects.push(EngineEffect::UpdateMetadata {
                        media_id: prev_media.id.clone(),
                        title: prev_media.title.clone(),
                        artist: prev_media.artist.clone(),
                    });
                }
            }
            crate::command::EngineCommandType::Play => {
                // Only allow play if we have an active session
                if next_snapshot.session.is_some() {
                    if self.snapshot.media_id.is_none() {
                        // If playing from idle/nothing, try to load first item from queue
                        if let Some(media) = self.queue.current_item() {
                            next_snapshot = next_snapshot.with_media(media.clone());
                            effects.push(EngineEffect::UpdateMetadata {
                                media_id: media.id.clone(),
                                title: media.title.clone(),
                                artist: media.artist.clone(),
                            });
                        } else if let Some(media) = self.queue.next_item() {
                            next_snapshot = next_snapshot.with_media(media.clone());
                            effects.push(EngineEffect::UpdateMetadata {
                                media_id: media.id.clone(),
                                title: media.title.clone(),
                                artist: media.artist.clone(),
                            });
                        }
                    }
                } else {
                    // Revert to idle if no session
                    next_snapshot.playback_state = crate::playback::PlaybackState::Idle;
                }
            }
            crate::command::EngineCommandType::Search { query } => {
                let results = self.repository.search(query);
                next_snapshot = next_snapshot.with_search_results(results);
            }
            crate::command::EngineCommandType::Browse { parent_id } => {
                let results = self.repository.browse(parent_id);
                next_snapshot = next_snapshot.with_search_results(results);
            }
            _ => {}
        }

        // Logic-based action emission
        match (prev_playback_state, next_snapshot.playback_state) {
            (prev, next) if prev != next => {
                match next {
                    crate::playback::PlaybackState::Buffering => {
                        effects.push(EngineEffect::RequestAudioFocus);
                        effects.push(EngineEffect::Play);
                    }
                    crate::playback::PlaybackState::Playing => {
                        effects.push(EngineEffect::Play);
                    }
                    crate::playback::PlaybackState::Paused => {
                        effects.push(EngineEffect::Pause);
                    }
                    crate::playback::PlaybackState::Idle => {
                        effects.push(EngineEffect::Stop);
                        effects.push(EngineEffect::AbandonAudioFocus);
                    }
                    _ => {}
                }
            }
            _ => {}
        }

        self.snapshot = next_snapshot;

        let outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(Some(command.command_type.as_wire().to_owned())),
            effects,
        };

        self.middleware.after_dispatch(self, &outcome);

        outcome
    }

    /// Dispatches a platform-level event to the engine.
    pub fn dispatch_platform_event(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        // Handle Media Button Pressed by converting it to a command
        if event.event_type == EnginePlatformEventType::MediaButtonPressed {
            if let Some(payload) = &event.payload {
                let command_type = EngineCommandType::from_wire(payload.clone());
                return self.dispatch(EngineCommand::new(command_type, None), now_epoch_millis);
            }
        }

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state = StateMachine::next_state_from_platform_event(
            prev_playback_state,
            &event.event_type,
        );

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
                next_snapshot = next_snapshot.with_error(Some(EngineError::player_error("Unknown platform error")));
            }
        } else {
            next_snapshot = next_snapshot.with_error(None);
        }

        let mut effects = Vec::new();
        match (prev_playback_state, next_playback_state) {
            (prev, next) if prev != next => {
                match next {
                    crate::playback::PlaybackState::Paused => {
                        effects.push(EngineEffect::Pause);
                    }
                    crate::playback::PlaybackState::Playing => {
                        effects.push(EngineEffect::Play);
                    }
                    crate::playback::PlaybackState::Idle => {
                        effects.push(EngineEffect::Stop);
                    }
                    _ => {}
                }
            }
            _ => {}
        }

        self.snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
            effects,
        }
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
        // Force state to Playing for test (in real app, this would happen via system event)
        engine.snapshot = engine.snapshot.clone().with_playback_state(PlaybackState::Playing, 250);

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
        assert_eq!(EngineEventType::PlatformEventApplied, outcome.event.event_type);
        assert_eq!(Some("media_loaded".to_owned()), outcome.event.message);
    }

    #[test]
    fn platform_error_moves_to_error_state() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine.snapshot.clone().with_playback_state(PlaybackState::Playing, 150);

        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaError, None),
            200,
        );

        assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
    }

    #[test]
    fn audio_focus_loss_pauses_playing_engine() {
        let mut engine = Engine::new(100);
        engine.snapshot = engine.snapshot.clone().with_playback_state(PlaybackState::Playing, 150);

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
            },
            MediaItem {
                id: "2".to_string(),
                title: "Song 2".to_string(),
                artist: "Artist 2".to_string(),
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
        engine.queue().set_repeat_mode(crate::queue::RepeatMode::All);
        engine.dispatch(EngineCommand::skip_previous(), 400);
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));
    }

    #[test]
    fn engine_search_finds_items() {
        let mut engine = Engine::new(100);
        let items = vec![
            MediaItem { id: "1".to_string(), title: "Rust Song".to_string(), artist: "The Developers".to_string() },
            MediaItem { id: "2".to_string(), title: "Kotlin Blues".to_string(), artist: "The Developers".to_string() },
        ];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        let outcome = engine.dispatch(EngineCommand::search("Rust".to_string()), 150);
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[test]
    fn browse_returns_items() {
        let mut engine = Engine::new(100);
        let items = vec![
            MediaItem { id: "1".to_string(), title: "Song 1".to_string(), artist: "Artist A".to_string() },
        ];
        engine.set_repository(Box::new(InMemoryRepository::new(items)));
        
        let outcome = engine.dispatch(EngineCommand::browse("root".to_string()), 150);
        assert_eq!(outcome.snapshot.search_results.len(), 1);
        assert_eq!(outcome.snapshot.search_results[0].id, "1");
    }

    #[test]
    fn play_command_emits_effects() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::start_session("user1".to_string()), 120);
        let items = vec![
            MediaItem {
                id: "1".to_string(),
                title: "Song 1".to_string(),
                artist: "Artist 1".to_string(),
            },
        ];
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
        engine.snapshot = engine.snapshot.clone().with_playback_state(PlaybackState::Playing, 150);
        
        let outcome = engine.dispatch(EngineCommand::pause(), 200);

        assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
        assert!(outcome.effects.contains(&EngineEffect::Pause));
    }
}
