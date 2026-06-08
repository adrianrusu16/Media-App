use crate::command::EngineCommandType;
use crate::platform_event::EnginePlatformEventType;
use crate::playback::PlaybackState;

/// Logic for managing media playback state transitions.
pub struct StateMachine;

impl StateMachine {
    /// Determines the next [PlaybackState] based on the current state and a user command.
    pub fn next_state_from_command(
        current: PlaybackState,
        command: &EngineCommandType,
    ) -> PlaybackState {
        match (current, command) {
            // State: Idle
            (PlaybackState::Idle, EngineCommandType::Play) => PlaybackState::Buffering,
            (PlaybackState::Idle, EngineCommandType::Bootstrap) => PlaybackState::Idle,

            // State: Buffering
            (PlaybackState::Buffering, EngineCommandType::Pause) => PlaybackState::Paused,
            (PlaybackState::Buffering, _) => PlaybackState::Buffering,

            // State: Playing
            (PlaybackState::Playing, EngineCommandType::Pause) => PlaybackState::Paused,
            (PlaybackState::Playing, EngineCommandType::SkipNext)
            | (PlaybackState::Playing, EngineCommandType::SkipPrevious) => PlaybackState::Buffering,

            // State: Paused
            (PlaybackState::Paused, EngineCommandType::Play) => PlaybackState::Playing,
            (PlaybackState::Paused, EngineCommandType::SkipNext)
            | (PlaybackState::Paused, EngineCommandType::SkipPrevious) => PlaybackState::Buffering,

            // Default: preserve current state for unrecognized transitions
            (current, _) => current,
        }
    }

    /// Determines the next [PlaybackState] based on the current state and a platform event.
    pub fn next_state_from_platform_event(
        current: PlaybackState,
        event: &EnginePlatformEventType,
    ) -> PlaybackState {
        match (current, event) {
            // State: Buffering
            (PlaybackState::Buffering, EnginePlatformEventType::MediaLoaded) => {
                PlaybackState::Playing
            }
            (PlaybackState::Buffering, EnginePlatformEventType::MediaError) => PlaybackState::Error,

            // State: Playing
            (PlaybackState::Playing, EnginePlatformEventType::MediaError) => PlaybackState::Error,

            // Suspend/Resume logic (simplified)
            (_, EnginePlatformEventType::SuspendToRam) => PlaybackState::Paused,

            // Default: preserve current state
            (current, _) => current,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_idle_to_buffering_on_play() {
        assert_eq!(
            PlaybackState::Buffering,
            StateMachine::next_state_from_command(PlaybackState::Idle, &EngineCommandType::Play)
        );
    }

    #[test]
    fn test_buffering_to_playing_on_media_loaded() {
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Buffering,
                &EnginePlatformEventType::MediaLoaded
            )
        );
    }

    #[test]
    fn test_any_to_error_on_media_error() {
        assert_eq!(
            PlaybackState::Error,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Playing,
                &EnginePlatformEventType::MediaError
            )
        );
    }
}
