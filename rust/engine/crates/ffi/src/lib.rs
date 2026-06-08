use panda_engine_core::{
    Engine, EngineCommand, EngineCommandType, EngineEventType, EngineOutcome, EngineSnapshot,
    LoggerMiddleware, MiddlewarePipeline, PlaybackState, RestrictionState,
    TelemetryMiddleware,
};

pub const FFI_COMMAND_BOOTSTRAP: i32 = 0;
pub const FFI_COMMAND_PLAY: i32 = 1;
pub const FFI_COMMAND_PAUSE: i32 = 2;
pub const FFI_COMMAND_SKIP_PREVIOUS: i32 = 3;
pub const FFI_COMMAND_SKIP_NEXT: i32 = 4;
pub const FFI_COMMAND_UNKNOWN: i32 = -1;

pub const FFI_PLAYBACK_IDLE: i32 = 0;
pub const FFI_PLAYBACK_PLAYING: i32 = 1;
pub const FFI_PLAYBACK_PAUSED: i32 = 2;
pub const FFI_PLAYBACK_BUFFERING: i32 = 3;
pub const FFI_PLAYBACK_ERROR: i32 = 4;

pub const FFI_RESTRICTION_UNKNOWN: i32 = 0;

pub const FFI_EVENT_COMMAND_APPLIED: i32 = 0;
pub const FFI_EVENT_LISTENER_REGISTERED: i32 = 1;

/// C-compatible representation of the engine snapshot.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiEngineSnapshot {
    pub playback_state: i32,
    pub restriction_state: i32,
    pub updated_at_epoch_millis: u64,
}

/// C-compatible representation of the engine outcome.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiEngineOutcome {
    pub snapshot: FfiEngineSnapshot,
    pub event_type: i32,
    pub applied_command_type: i32,
}

/// Opaque handle to the Rust Engine.
pub struct PandaEngine {
    engine: Engine,
}

/// Creates a new PandaEngine instance.
///
/// # Safety
/// The caller is responsible for destroying the engine using [panda_engine_destroy].
#[unsafe(no_mangle)]
pub extern "C" fn panda_engine_create(now_epoch_millis: u64) -> *mut PandaEngine {
    let mut engine = Engine::new(now_epoch_millis);

    // Setup default state-of-the-art middleware (e.g., logging)
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(LoggerMiddleware));
    pipeline.add(Box::new(TelemetryMiddleware));
    engine.set_middleware(pipeline);

    Box::into_raw(Box::new(PandaEngine { engine }))
}

/// Destroys a PandaEngine instance and frees its memory.
///
/// # Safety
/// [engine] must be a pointer returned by [panda_engine_create] and must not
/// be used again after this call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_destroy(engine: *mut PandaEngine) {
    if !engine.is_null() {
        drop(unsafe { Box::from_raw(engine) });
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_snapshot(engine: *const PandaEngine) -> FfiEngineSnapshot {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => FfiEngineSnapshot::from(engine.engine.snapshot()),
        None => FfiEngineSnapshot::invalid(),
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_dispatch(
    engine: *mut PandaEngine,
    command_type: i32,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            let outcome = engine.engine.dispatch(
                EngineCommand::new(command_from_ffi(command_type), None),
                now_epoch_millis,
            );
            FfiEngineOutcome::from((&outcome, command_type))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

impl FfiEngineSnapshot {
    fn invalid() -> Self {
        Self {
            playback_state: FFI_COMMAND_UNKNOWN,
            restriction_state: FFI_COMMAND_UNKNOWN,
            updated_at_epoch_millis: 0,
        }
    }
}

impl FfiEngineOutcome {
    fn invalid() -> Self {
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

fn command_from_ffi(command_type: i32) -> EngineCommandType {
    match command_type {
        FFI_COMMAND_BOOTSTRAP => EngineCommandType::Bootstrap,
        FFI_COMMAND_PLAY => EngineCommandType::Play,
        FFI_COMMAND_PAUSE => EngineCommandType::Pause,
        FFI_COMMAND_SKIP_PREVIOUS => EngineCommandType::SkipPrevious,
        FFI_COMMAND_SKIP_NEXT => EngineCommandType::SkipNext,
        _ => EngineCommandType::Unknown(command_type.to_string()),
    }
}

fn playback_to_ffi(playback_state: PlaybackState) -> i32 {
    match playback_state {
        PlaybackState::Idle => FFI_PLAYBACK_IDLE,
        PlaybackState::Playing => FFI_PLAYBACK_PLAYING,
        PlaybackState::Paused => FFI_PLAYBACK_PAUSED,
        PlaybackState::Buffering => FFI_PLAYBACK_BUFFERING,
        PlaybackState::Error => FFI_PLAYBACK_ERROR,
    }
}

fn restriction_to_ffi(restriction_state: RestrictionState) -> i32 {
    match restriction_state {
        RestrictionState::Unknown => FFI_RESTRICTION_UNKNOWN,
    }
}

fn event_to_ffi(event_type: &EngineEventType) -> i32 {
    match event_type {
        EngineEventType::CommandApplied => FFI_EVENT_COMMAND_APPLIED,
        EngineEventType::PlatformEventApplied => FFI_EVENT_COMMAND_APPLIED,
        EngineEventType::ListenerRegistered => FFI_EVENT_LISTENER_REGISTERED,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dispatch_play_returns_buffering_snapshot() {
        let engine = panda_engine_create(100);
        let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, 200) };
        unsafe {
            panda_engine_destroy(engine);
        }

        assert_eq!(FFI_PLAYBACK_BUFFERING, outcome.snapshot.playback_state);
        assert_eq!(FFI_EVENT_COMMAND_APPLIED, outcome.event_type);
        assert_eq!(FFI_COMMAND_PLAY, outcome.applied_command_type);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
    }

    #[test]
    fn null_snapshot_returns_invalid_marker() {
        let snapshot = unsafe { panda_engine_snapshot(std::ptr::null()) };

        assert_eq!(FFI_COMMAND_UNKNOWN, snapshot.playback_state);
        assert_eq!(0, snapshot.updated_at_epoch_millis);
    }

    #[test]
    fn dispatch_skip_next_moves_to_buffering() {
        let engine = panda_engine_create(100);
        unsafe {
            panda_engine_dispatch(engine, FFI_COMMAND_PLAY, 200);
            // Simulate platform moving to Playing
            (*engine).engine.dispatch_platform_event(
                panda_engine_core::EnginePlatformEvent::new(
                    panda_engine_core::EnginePlatformEventType::MediaLoaded,
                    None,
                ),
                250,
            );
        }
        let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_SKIP_NEXT, 300) };
        unsafe {
            panda_engine_destroy(engine);
        }

        assert_eq!(FFI_PLAYBACK_BUFFERING, outcome.snapshot.playback_state);
        assert_eq!(FFI_COMMAND_SKIP_NEXT, outcome.applied_command_type);
        assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
    }
}
