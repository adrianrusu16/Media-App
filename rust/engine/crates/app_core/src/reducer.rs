use crate::command::{EngineCommand, EngineCommandType};
use crate::event::EngineEvent;
use crate::platform_event::EnginePlatformEvent;
use crate::playback::PlaybackState;
use crate::snapshot::EngineSnapshot;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineOutcome {
    pub snapshot: EngineSnapshot,
    pub event: EngineEvent,
}

#[derive(Debug)]
pub struct Engine {
    snapshot: EngineSnapshot,
}

impl Engine {
    pub fn new(now_epoch_millis: u64) -> Self {
        Self {
            snapshot: EngineSnapshot::idle(now_epoch_millis),
        }
    }

    pub fn snapshot(&self) -> &EngineSnapshot {
        &self.snapshot
    }

    pub fn dispatch(&mut self, command: EngineCommand, now_epoch_millis: u64) -> EngineOutcome {
        let next_playback_state = match command.command_type {
            EngineCommandType::Play => PlaybackState::Playing,
            EngineCommandType::Pause => PlaybackState::Paused,
            EngineCommandType::Bootstrap
            | EngineCommandType::SkipPrevious
            | EngineCommandType::SkipNext
            | EngineCommandType::Unknown(_) => {
                self.snapshot.playback_state
            }
        };

        self.snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(Some(command.command_type.as_wire().to_owned())),
        }
    }

    pub fn dispatch_platform_event(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        self.snapshot = self
            .snapshot
            .clone()
            .with_playback_state(self.snapshot.playback_state, now_epoch_millis);

        EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::platform_event_applied(Some(
                event.event_type.as_wire().to_owned(),
            )),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::event::EngineEventType;
    use crate::platform_event::EnginePlatformEventType;

    #[test]
    fn starts_idle() {
        let engine = Engine::new(100);

        assert_eq!(PlaybackState::Idle, engine.snapshot().playback_state);
        assert_eq!(100, engine.snapshot().updated_at_epoch_millis);
    }

    #[test]
    fn play_command_moves_snapshot_to_playing() {
        let mut engine = Engine::new(100);
        let outcome = engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(EngineEventType::CommandApplied, outcome.event.event_type);
        assert_eq!(Some("play".to_owned()), outcome.event.message);
    }

    #[test]
    fn unknown_command_preserves_playback_state() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        let outcome = engine.dispatch(EngineCommand::from_wire("future_command", None), 300);

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("future_command".to_owned()), outcome.event.message);
    }

    #[test]
    fn skip_command_preserves_playback_state() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        let outcome = engine.dispatch(EngineCommand::new(EngineCommandType::SkipNext, None), 300);

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(Some("skip_next".to_owned()), outcome.event.message);
    }

    #[test]
    fn platform_event_preserves_playback_state() {
        let mut engine = Engine::new(100);
        engine.dispatch(EngineCommand::new(EngineCommandType::Play, None), 200);

        let outcome = engine.dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::SuspendToRam, None),
            300,
        );

        assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
        assert_eq!(EngineEventType::PlatformEventApplied, outcome.event.event_type);
        assert_eq!(Some("suspend_to_ram".to_owned()), outcome.event.message);
    }
}
