use panda_engine_core::{
    EngineCommandType, EngineEffect, EngineEventType, PlaybackState, RestrictionState,
};

use crate::constants::*;

pub(crate) fn effect_to_ffi(effect: &EngineEffect) -> i32 {
    match effect {
        EngineEffect::Play => FFI_EFFECT_PLAY,
        EngineEffect::Pause => FFI_EFFECT_PAUSE,
        EngineEffect::Stop => FFI_EFFECT_STOP,
        EngineEffect::Seek(_) => FFI_EFFECT_SEEK,
        EngineEffect::RequestAudioFocus => FFI_EFFECT_REQUEST_AUDIO_FOCUS,
        EngineEffect::AbandonAudioFocus => FFI_EFFECT_ABANDON_AUDIO_FOCUS,
        EngineEffect::UpdateMetadata { .. } => FFI_EFFECT_UPDATE_METADATA,
        EngineEffect::SessionStarted { .. } => FFI_EFFECT_SESSION_STARTED,
        EngineEffect::SessionEnded => FFI_EFFECT_SESSION_ENDED,
        EngineEffect::SetSpeed(_) => FFI_EFFECT_SET_SPEED,
        EngineEffect::NotifyUser { .. } => FFI_EFFECT_NOTIFY_USER,
        EngineEffect::StartAudioCapture => FFI_EFFECT_START_AUDIO_CAPTURE,
        EngineEffect::StopAudioCapture => FFI_EFFECT_STOP_AUDIO_CAPTURE,
        EngineEffect::DuckAudio => FFI_EFFECT_DUCK_AUDIO,
        EngineEffect::UnduckAudio => FFI_EFFECT_UNDUCK_AUDIO,
    }
}

pub(crate) fn command_from_ffi(command_type: i32) -> EngineCommandType {
    match command_type {
        FFI_COMMAND_BOOTSTRAP => EngineCommandType::Bootstrap,
        FFI_COMMAND_PLAY => EngineCommandType::Play,
        FFI_COMMAND_PAUSE => EngineCommandType::Pause,
        FFI_COMMAND_SKIP_PREVIOUS => EngineCommandType::SkipPrevious,
        FFI_COMMAND_SKIP_NEXT => EngineCommandType::SkipNext,
        FFI_COMMAND_START_SESSION => EngineCommandType::StartSession {
            user_id: "unknown".to_string(),
        },
        FFI_COMMAND_END_SESSION => EngineCommandType::EndSession,
        FFI_COMMAND_START_VOICE => EngineCommandType::StartVoiceInteraction,
        FFI_COMMAND_STOP_VOICE => EngineCommandType::StopVoiceInteraction,
        _ => EngineCommandType::Unknown(command_type.to_string()),
    }
}

pub(crate) fn platform_event_from_ffi(
    event_type: i32,
) -> panda_engine_core::EnginePlatformEventType {
    use panda_engine_core::EnginePlatformEventType;

    match event_type {
        FFI_PLATFORM_EVENT_APP_FOREGROUNDED => EnginePlatformEventType::AppForegrounded,
        FFI_PLATFORM_EVENT_APP_BACKGROUNDED => EnginePlatformEventType::AppBackgrounded,
        FFI_PLATFORM_EVENT_SUSPEND_TO_RAM => EnginePlatformEventType::SuspendToRam,
        FFI_PLATFORM_EVENT_RESUME_FROM_RAM => EnginePlatformEventType::ResumeFromRam,
        FFI_PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED => {
            EnginePlatformEventType::UxRestrictionsChanged
        }
        FFI_PLATFORM_EVENT_AUDIO_FOCUS_CHANGED => EnginePlatformEventType::AudioFocusChanged,
        FFI_PLATFORM_EVENT_MEDIA_LOADED => EnginePlatformEventType::MediaLoaded,
        FFI_PLATFORM_EVENT_MEDIA_ERROR => EnginePlatformEventType::MediaError,
        _ => EnginePlatformEventType::Unknown(event_type.to_string()),
    }
}

pub(crate) fn playback_to_ffi(playback_state: PlaybackState) -> i32 {
    match playback_state {
        PlaybackState::Idle => FFI_PLAYBACK_IDLE,
        PlaybackState::Playing => FFI_PLAYBACK_PLAYING,
        PlaybackState::Paused => FFI_PLAYBACK_PAUSED,
        PlaybackState::Buffering => FFI_PLAYBACK_BUFFERING,
        PlaybackState::Error => FFI_PLAYBACK_ERROR,
    }
}

pub(crate) fn restriction_to_ffi(restriction_state: RestrictionState) -> i32 {
    match restriction_state {
        RestrictionState::Unknown => FFI_RESTRICTION_UNKNOWN,
    }
}

pub(crate) fn event_to_ffi(event_type: &EngineEventType) -> i32 {
    match event_type {
        EngineEventType::CommandApplied => FFI_EVENT_COMMAND_APPLIED,
        EngineEventType::PlatformEventApplied => FFI_EVENT_COMMAND_APPLIED,
        EngineEventType::ListenerRegistered => FFI_EVENT_LISTENER_REGISTERED,
        EngineEventType::AnalyticsReported => FFI_EVENT_ANALYTICS_REPORTED,
    }
}
