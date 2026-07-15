use std::ffi::c_char;

use panda_engine_core::{EngineOutcome, EngineSnapshot};

use crate::mappings::{
    driving_state_to_ffi, event_to_ffi, playback_to_ffi, preference_source_to_ffi,
    restriction_to_ffi, theme_preference_to_ffi,
};
use crate::{
    FFI_COMMAND_UNKNOWN, FFI_ERROR_AUTHENTICATION, FFI_ERROR_MEDIA_SKIPPED, FFI_ERROR_NETWORK,
    FFI_ERROR_NONE, FFI_ERROR_NOT_FOUND, FFI_ERROR_PLAYER, FFI_ERROR_UNKNOWN,
};

/// C-compatible representation of the engine configuration.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiEngineConfig {
    pub vehicle_name: *const c_char,
    pub hifi_enabled: bool,
    pub max_volume: u8,
    pub auto_resume: bool,
    pub preferred_language: *const c_char,
}

/// C-compatible representation of a specific player control state.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiControlState {
    pub is_visible: bool,
    pub is_enabled: bool,
    pub is_active: bool,
}

/// C-compatible representation of the player controls.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiPlayerControls {
    pub play_pause: FfiControlState,
    pub skip_next: FfiControlState,
    pub skip_prev: FfiControlState,
    pub show_play_icon: bool,
}

/// C-compatible representation of the engine snapshot.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiEngineSnapshot {
    pub playback_state: i32,
    pub restriction_state: i32,
    pub updated_at_epoch_millis: u64,
    pub metadata_revision: u64,
    pub duration_millis: i64,
    pub playback_expires_at_epoch_millis: i64,
    pub theme_preference: i32,
    pub preference_source: i32,
    pub preference_revision: u64,
    pub preference_initialized: bool,
    pub has_active_session: bool,
    pub has_error: bool,
    pub error_type: i32,
    pub search_results_count: usize,
    pub playback_speed: f32,
    pub position_millis: u64,
    pub is_busy: bool,
    pub can_dispatch: bool,
    pub controls: FfiPlayerControls,
    pub has_voice_hypothesis: bool,
    pub browse_results_count: usize,
    pub driving_state: i32,
    pub has_backend_status: bool,
    pub backend_healthy: bool,
    pub backend_checked_at_epoch_millis: i64,
    pub backend_dependencies_count: usize,
}

/// C-compatible representation of the engine outcome.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiEngineOutcome {
    pub snapshot: FfiEngineSnapshot,
    pub event_type: i32,
    pub applied_command_type: i32,
}

impl FfiEngineSnapshot {
    pub(crate) fn invalid() -> Self {
        Self {
            playback_state: FFI_COMMAND_UNKNOWN,
            restriction_state: FFI_COMMAND_UNKNOWN,
            updated_at_epoch_millis: 0,
            metadata_revision: 0,
            duration_millis: -1,
            playback_expires_at_epoch_millis: -1,
            theme_preference: crate::FFI_THEME_SYSTEM_DEFAULT,
            preference_source: crate::FFI_PREFERENCE_SOURCE_UNINITIALIZED,
            preference_revision: 0,
            preference_initialized: false,
            has_active_session: false,
            has_error: false,
            error_type: FFI_ERROR_NONE,
            search_results_count: 0,
            playback_speed: 1.0,
            position_millis: 0,
            is_busy: false,
            can_dispatch: false,
            controls: FfiPlayerControls {
                play_pause: FfiControlState {
                    is_visible: false,
                    is_enabled: false,
                    is_active: false,
                },
                skip_next: FfiControlState {
                    is_visible: false,
                    is_enabled: false,
                    is_active: false,
                },
                skip_prev: FfiControlState {
                    is_visible: false,
                    is_enabled: false,
                    is_active: false,
                },
                show_play_icon: true,
            },
            has_voice_hypothesis: false,
            browse_results_count: 0,
            driving_state: FFI_COMMAND_UNKNOWN,
            has_backend_status: false,
            backend_healthy: false,
            backend_checked_at_epoch_millis: -1,
            backend_dependencies_count: 0,
        }
    }
}

impl FfiEngineOutcome {
    pub(crate) fn invalid() -> Self {
        Self {
            snapshot: FfiEngineSnapshot::invalid(),
            event_type: FFI_COMMAND_UNKNOWN,
            applied_command_type: FFI_COMMAND_UNKNOWN,
        }
    }
}

impl From<&EngineSnapshot> for FfiEngineSnapshot {
    fn from(snapshot: &EngineSnapshot) -> Self {
        Self {
            playback_state: playback_to_ffi(snapshot.playback_state),
            restriction_state: restriction_to_ffi(snapshot.restriction_state),
            updated_at_epoch_millis: snapshot.updated_at_epoch_millis,
            metadata_revision: snapshot.metadata_revision,
            duration_millis: snapshot
                .duration_millis
                .map(|duration_millis| duration_millis.min(i64::MAX as u64) as i64)
                .unwrap_or(-1),
            playback_expires_at_epoch_millis: snapshot
                .playback_expires_at_epoch_millis
                .and_then(|expiry| i64::try_from(expiry).ok())
                .unwrap_or(-1),
            theme_preference: theme_preference_to_ffi(snapshot.theme_preference.theme),
            preference_source: preference_source_to_ffi(snapshot.theme_preference.source),
            preference_revision: snapshot.theme_preference.revision,
            preference_initialized: snapshot.theme_preference.is_initialized(),
            has_active_session: snapshot.session.is_some(),
            has_error: snapshot.last_error.is_some(),
            error_type: snapshot
                .last_error
                .as_ref()
                .map(|e| match e.error_type {
                    panda_engine_core::EngineErrorType::NotFound => FFI_ERROR_NOT_FOUND,
                    panda_engine_core::EngineErrorType::NetworkError
                    | panda_engine_core::EngineErrorType::RateLimited
                    | panda_engine_core::EngineErrorType::ServiceUnavailable
                    | panda_engine_core::EngineErrorType::Transport => FFI_ERROR_NETWORK,
                    panda_engine_core::EngineErrorType::PlayerError => FFI_ERROR_PLAYER,
                    panda_engine_core::EngineErrorType::AuthenticationError
                    | panda_engine_core::EngineErrorType::LoginRequired
                    | panda_engine_core::EngineErrorType::AuthExpired
                    | panda_engine_core::EngineErrorType::Forbidden => FFI_ERROR_AUTHENTICATION,
                    panda_engine_core::EngineErrorType::MediaSkipped => FFI_ERROR_MEDIA_SKIPPED,
                    panda_engine_core::EngineErrorType::InvalidInput
                    | panda_engine_core::EngineErrorType::AlreadyExists
                    | panda_engine_core::EngineErrorType::FailedPrecondition
                    | panda_engine_core::EngineErrorType::Conflict
                    | panda_engine_core::EngineErrorType::BackendFault
                    | panda_engine_core::EngineErrorType::UnsafeTransport
                    | panda_engine_core::EngineErrorType::MappingDefect
                    | panda_engine_core::EngineErrorType::CommandRejected
                    | panda_engine_core::EngineErrorType::Unknown => FFI_ERROR_UNKNOWN,
                })
                .unwrap_or(FFI_ERROR_NONE),
            search_results_count: snapshot.search_results.len(),
            playback_speed: snapshot.playback_speed,
            position_millis: snapshot.position_millis,
            is_busy: snapshot.is_busy,
            can_dispatch: snapshot.can_dispatch(),
            controls: FfiPlayerControls {
                play_pause: FfiControlState {
                    is_visible: snapshot.controls.play_pause.is_visible,
                    is_enabled: snapshot.controls.play_pause.is_enabled,
                    is_active: snapshot.controls.play_pause.is_active,
                },
                skip_next: FfiControlState {
                    is_visible: snapshot.controls.skip_next.is_visible,
                    is_enabled: snapshot.controls.skip_next.is_enabled,
                    is_active: snapshot.controls.skip_next.is_active,
                },
                skip_prev: FfiControlState {
                    is_visible: snapshot.controls.skip_prev.is_visible,
                    is_enabled: snapshot.controls.skip_prev.is_enabled,
                    is_active: snapshot.controls.skip_prev.is_active,
                },
                show_play_icon: snapshot.controls.show_play_icon,
            },
            has_voice_hypothesis: snapshot.voice_hypothesis.is_some(),
            browse_results_count: snapshot.browse_results.len(),
            driving_state: driving_state_to_ffi(snapshot.driving_state),
            has_backend_status: snapshot.backend_status.is_some(),
            backend_healthy: snapshot
                .backend_status
                .as_ref()
                .is_some_and(|status| status.healthy),
            backend_checked_at_epoch_millis: snapshot
                .backend_status
                .as_ref()
                .and_then(|status| status.checked_at_epoch_millis)
                .map(|checked_at| checked_at.min(i64::MAX as u64) as i64)
                .unwrap_or(-1),
            backend_dependencies_count: snapshot
                .backend_status
                .as_ref()
                .map_or(0, |status| status.dependencies.len()),
        }
    }
}

impl From<(&EngineOutcome, i32)> for FfiEngineOutcome {
    fn from((outcome, command_type): (&EngineOutcome, i32)) -> Self {
        Self {
            snapshot: FfiEngineSnapshot::from(&outcome.snapshot),
            event_type: event_to_ffi(&outcome.event.event_type),
            applied_command_type: command_type,
        }
    }
}
