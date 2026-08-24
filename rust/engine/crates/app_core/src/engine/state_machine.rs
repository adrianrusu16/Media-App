use crate::model::command::EngineCommandType;
use crate::model::platform_event::EnginePlatformEventType;
use crate::model::playback::PlaybackState;

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

            // State: Recovering
            (PlaybackState::Recovering, EngineCommandType::Pause) => PlaybackState::Paused,
            (PlaybackState::Recovering, _) => PlaybackState::Recovering,

            // State: Playing
            (PlaybackState::Playing, EngineCommandType::Pause) => PlaybackState::Paused,

            // State: Paused
            (PlaybackState::Paused, EngineCommandType::Play) => PlaybackState::Playing,

            // State: Error
            (PlaybackState::Error, EngineCommandType::Play) => PlaybackState::Buffering,

            // State: Ended
            (PlaybackState::Ended, EngineCommandType::Play) => PlaybackState::Buffering,

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

            // State: Recovering
            (PlaybackState::Recovering, EnginePlatformEventType::MediaLoaded) => {
                PlaybackState::Playing
            }
            (PlaybackState::Recovering, EnginePlatformEventType::MediaError) => {
                PlaybackState::Error
            }

            // State: Playing
            (PlaybackState::Playing, EnginePlatformEventType::MediaError) => PlaybackState::Error,
            // State: Error
            (PlaybackState::Error, EnginePlatformEventType::MediaLoaded) => PlaybackState::Playing,

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

    #[test]
    fn test_buffering_to_paused_on_pause_command() {
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_command(
                PlaybackState::Buffering,
                &EngineCommandType::Pause
            )
        );
    }

    #[test]
    fn untyped_audio_focus_preserves_paused_state() {
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Paused,
                &EnginePlatformEventType::AudioFocusChanged
            )
        );
    }

    #[test]
    fn test_error_to_buffering_on_play_command() {
        assert_eq!(
            PlaybackState::Buffering,
            StateMachine::next_state_from_command(PlaybackState::Error, &EngineCommandType::Play)
        );
    }

    #[test]
    fn test_suspend_to_ram_pauses_any_state() {
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Playing,
                &EnginePlatformEventType::SuspendToRam
            )
        );
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Buffering,
                &EnginePlatformEventType::SuspendToRam
            )
        );
    }

    #[test]
    fn test_idle_bootstrap_stays_idle() {
        assert_eq!(
            PlaybackState::Idle,
            StateMachine::next_state_from_command(
                PlaybackState::Idle,
                &EngineCommandType::Bootstrap
            )
        );
    }

    #[test]
    fn test_playing_to_paused_on_pause() {
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_command(
                PlaybackState::Playing,
                &EngineCommandType::Pause
            )
        );
    }

    #[test]
    fn test_playing_skip_is_policy_driven_and_preserves_state_until_resolved() {
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_command(
                PlaybackState::Playing,
                &EngineCommandType::SkipNext
            )
        );
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_command(
                PlaybackState::Playing,
                &EngineCommandType::SkipPrevious
            )
        );
    }

    #[test]
    fn test_paused_to_playing_on_play_command() {
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_command(PlaybackState::Paused, &EngineCommandType::Play)
        );
    }

    #[test]
    fn test_paused_skip_is_policy_driven_and_preserves_state_until_resolved() {
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_command(
                PlaybackState::Paused,
                &EngineCommandType::SkipNext
            )
        );
        assert_eq!(
            PlaybackState::Paused,
            StateMachine::next_state_from_command(
                PlaybackState::Paused,
                &EngineCommandType::SkipPrevious
            )
        );
    }

    #[test]
    fn test_buffering_to_error_on_media_error() {
        assert_eq!(
            PlaybackState::Error,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Buffering,
                &EnginePlatformEventType::MediaError
            )
        );
    }

    #[test]
    fn test_error_to_playing_on_media_loaded() {
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Error,
                &EnginePlatformEventType::MediaLoaded
            )
        );
    }

    #[test]
    fn untyped_audio_focus_preserves_playing_state() {
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Playing,
                &EnginePlatformEventType::AudioFocusChanged
            )
        );
    }

    #[test]
    fn test_unrecognized_command_preserves_state() {
        // Pause has no transition out of Idle, so the state is preserved.
        assert_eq!(
            PlaybackState::Idle,
            StateMachine::next_state_from_command(PlaybackState::Idle, &EngineCommandType::Pause)
        );
        // A Seek command never changes the playback state.
        assert_eq!(
            PlaybackState::Playing,
            StateMachine::next_state_from_command(
                PlaybackState::Playing,
                &EngineCommandType::Seek {
                    position_millis: 1_000
                }
            )
        );
    }

    #[test]
    fn test_unrecognized_event_preserves_state() {
        // MediaLoaded has no transition from Idle, so the state is preserved.
        assert_eq!(
            PlaybackState::Idle,
            StateMachine::next_state_from_platform_event(
                PlaybackState::Idle,
                &EnginePlatformEventType::MediaLoaded
            )
        );
    }
}
