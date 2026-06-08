use crate::command::EngineCommand;
use crate::event::EngineEvent;
use crate::middleware::MiddlewarePipeline;
use crate::platform_event::EnginePlatformEvent;
use crate::repository::{InMemoryRepository, MediaRepository};
use crate::snapshot::EngineSnapshot;
use crate::state_machine::StateMachine;

/// Result of an engine operation, containing the new state and an event to be broadcasted.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineOutcome {
    /// The updated engine state.
    pub snapshot: EngineSnapshot,
    /// The event resulting from the action.
    pub event: EngineEvent,
}

/// The main state machine of the media engine.
///
/// It follows the Redux/ELM pattern where state transitions are deterministic
/// based on the current state and a given command.
pub struct Engine {
    snapshot: EngineSnapshot,
    middleware: MiddlewarePipeline,
    repository: Box<dyn MediaRepository>,
}

impl Default for Engine {
    fn default() -> Self {
        Self {
            snapshot: EngineSnapshot::default(),
            middleware: MiddlewarePipeline::default(),
            repository: Box::new(InMemoryRepository::new(vec![])),
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
        }
    }

    /// Sets the media repository for the engine.
    pub fn set_repository(&mut self, repository: Box<dyn MediaRepository>) {
        self.repository = repository;
    }

    /// Sets the middleware pipeline for the engine.
    pub fn set_middleware(&mut self, pipeline: MiddlewarePipeline) {
        self.middleware = pipeline;
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

        let next_playback_state =
            StateMachine::next_state_from_command(self.snapshot.playback_state, &command.command_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        // Update metadata based on command
        match command.command_type {
            crate::command::EngineCommandType::SkipNext => {
                if let Some(current_id) = &self.snapshot.media_id {
                    if let Some(next_media) = self.repository.get_next(current_id) {
                        next_snapshot = next_snapshot.with_media(next_media);
                    }
                }
            }
            crate::command::EngineCommandType::SkipPrevious => {
                if let Some(current_id) = &self.snapshot.media_id {
                    if let Some(prev_media) = self.repository.get_previous(current_id) {
                        next_snapshot = next_snapshot.with_media(prev_media);
                    }
                }
            }
            crate::command::EngineCommandType::Play => {
                if self.snapshot.media_id.is_none() {
                    // If playing from idle/nothing, try to load first item
                    if let Some(media) = self.repository.get_by_id("1") {
                        // Hardcoded '1' for now as a simple bootstrap
                        next_snapshot = next_snapshot.with_media(media);
                    }
                }
            }
            _ => {}
        }

        self.snapshot = next_snapshot;

        let outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(Some(command.command_type.as_wire().to_owned())),
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
        let next_playback_state = StateMachine::next_state_from_platform_event(
            self.snapshot.playback_state,
            &event.event_type,
        );

        self.snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
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
        let outcome = engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(EngineEventType::CommandApplied, outcome.event.event_type);
    }

    #[test]
    fn unknown_command_preserves_playback_state() {
        let mut engine = Engine::new(100);
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
        engine.set_repository(Box::new(InMemoryRepository::new(items)));

        // Initial play
        engine.dispatch(EngineCommand::play(), 200);
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));

        // Skip next
        engine.dispatch(EngineCommand::skip_next(), 300);
        assert_eq!(engine.snapshot().media_id, Some("2".to_string()));
        assert_eq!(engine.snapshot().title, Some("Song 2".to_string()));

        // Skip previous (wraps around)
        engine.dispatch(EngineCommand::skip_previous(), 400);
        assert_eq!(engine.snapshot().media_id, Some("1".to_string()));
    }

    #[test]
    fn repository_search_finds_items() {
        let items = vec![
            MediaItem {
                id: "1".to_string(),
                title: "Rust Song".to_string(),
                artist: "The Developers".to_string(),
            },
            MediaItem {
                id: "2".to_string(),
                title: "Kotlin Blues".to_string(),
                artist: "The Developers".to_string(),
            },
        ];
        let repo = InMemoryRepository::new(items);

        let results = repo.search("Rust");
        assert_eq!(1, results.len());
        assert_eq!("1", results[0].id);

        let results = repo.search("Developers");
        assert_eq!(2, results.len());
    }

    #[test]
    fn middleware_is_called() {
        use std::sync::atomic::{AtomicUsize, Ordering};
        use std::sync::Arc;
        use crate::middleware::Middleware;

        struct TestMiddleware(Arc<AtomicUsize>);
        impl Middleware for TestMiddleware {
            fn before_dispatch(&self, _engine: &Engine, _command: &EngineCommand) {
                self.0.fetch_add(1, Ordering::SeqCst);
            }
            fn after_dispatch(&self, _engine: &Engine, _outcome: &EngineOutcome) {
                self.0.fetch_add(10, Ordering::SeqCst);
            }
        }

        let counter = Arc::new(AtomicUsize::new(0));
        let mut engine = Engine::new(100);
        let mut pipeline = MiddlewarePipeline::new();
        pipeline.add(Box::new(TestMiddleware(counter.clone())));
        engine.set_middleware(pipeline);

        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        assert_eq!(11, counter.load(Ordering::SeqCst));
    }
}
