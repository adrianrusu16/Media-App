use panda_engine_core::{
    Engine, EngineEffect, EngineCommand, EngineCommandType, EngineEventType, EngineOutcome,
    EngineSnapshot, LoggerMiddleware, MediaItem, MiddlewarePipeline, PlaybackState, RepeatMode,
    RestrictionState, TelemetryMiddleware,
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

pub const FFI_PLATFORM_EVENT_APP_FOREGROUNDED: i32 = 0;
pub const FFI_PLATFORM_EVENT_APP_BACKGROUNDED: i32 = 1;
pub const FFI_PLATFORM_EVENT_SUSPEND_TO_RAM: i32 = 2;
pub const FFI_PLATFORM_EVENT_RESUME_FROM_RAM: i32 = 3;
pub const FFI_PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED: i32 = 4;
pub const FFI_PLATFORM_EVENT_AUDIO_FOCUS_CHANGED: i32 = 5;
pub const FFI_PLATFORM_EVENT_MEDIA_LOADED: i32 = 6;
pub const FFI_PLATFORM_EVENT_MEDIA_ERROR: i32 = 7;
pub const FFI_PLATFORM_EVENT_UNKNOWN: i32 = -1;

pub const FFI_RESTRICTION_UNKNOWN: i32 = 0;

pub const FFI_EVENT_COMMAND_APPLIED: i32 = 0;
pub const FFI_EVENT_LISTENER_REGISTERED: i32 = 1;

pub const FFI_EFFECT_PLAY: i32 = 0;
pub const FFI_EFFECT_PAUSE: i32 = 1;
pub const FFI_EFFECT_STOP: i32 = 2;
pub const FFI_EFFECT_SEEK: i32 = 3;
pub const FFI_EFFECT_REQUEST_AUDIO_FOCUS: i32 = 4;
pub const FFI_EFFECT_ABANDON_AUDIO_FOCUS: i32 = 5;
pub const FFI_EFFECT_UPDATE_METADATA: i32 = 6;

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
    last_effects: Vec<EngineEffect>,
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
    pipeline.add(Box::new(panda_engine_core::FocusMiddleware));
    engine.set_middleware(pipeline);

    Box::into_raw(Box::new(PandaEngine {
        engine,
        last_effects: Vec::new(),
    }))
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
            engine.last_effects = outcome.effects.clone();
            FfiEngineOutcome::from((&outcome, command_type))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_dispatch_platform_event(
    engine: *mut PandaEngine,
    event_type: i32,
    _payload: *const std::ffi::c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            let outcome = engine.engine.dispatch_platform_event(
                panda_engine_core::EnginePlatformEvent::new(platform_event_from_ffi(event_type), None),
                now_epoch_millis,
            );
            engine.last_effects = outcome.effects.clone();
            FfiEngineOutcome::from((&outcome, FFI_COMMAND_UNKNOWN))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_queue_set_items(
    engine: *mut PandaEngine,
    ids: *const *const std::ffi::c_char,
    titles: *const *const std::ffi::c_char,
    artists: *const *const std::ffi::c_char,
    count: usize,
) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let mut items = Vec::with_capacity(count);
        for i in 0..count {
            let id = unsafe { std::ffi::CStr::from_ptr(*ids.add(i)) }
                .to_string_lossy()
                .into_owned();
            let title = unsafe { std::ffi::CStr::from_ptr(*titles.add(i)) }
                .to_string_lossy()
                .into_owned();
            let artist = unsafe { std::ffi::CStr::from_ptr(*artists.add(i)) }
                .to_string_lossy()
                .into_owned();
            items.push(MediaItem { id, title, artist });
        }
        engine.engine.queue().set_items(items);
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_queue_set_repeat_mode(engine: *mut PandaEngine, mode: i32) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let repeat_mode = match mode {
            1 => RepeatMode::One,
            2 => RepeatMode::All,
            _ => RepeatMode::None,
        };
        engine.engine.queue().set_repeat_mode(repeat_mode);
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_queue_set_shuffle(engine: *mut PandaEngine, enabled: bool) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        engine.engine.queue().set_shuffle(enabled);
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_effects_count(engine: *const PandaEngine) -> usize {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => engine.last_effects.len(),
        None => 0,
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
/// [out_types] must be a pointer to an array of at least [panda_engine_get_actions_count] i32s.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_effects_types(
    engine: *const PandaEngine,
    out_types: *mut i32,
) {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        for (i, effect) in engine.last_effects.iter().enumerate() {
            unsafe {
                *out_types.add(i) = effect_to_ffi(effect);
            }
        }
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
/// Returns a pointer to the media ID if the action at [index] is UpdateMetadata.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_effect_media_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const std::ffi::c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        if let Some(EngineEffect::UpdateMetadata { media_id, .. }) = engine.last_effects.get(index) {
            // Leak for simplicity in this prototype, or use a better buffer management
            return std::ffi::CString::new(media_id.as_str()).unwrap().into_raw();
        }
    }
    std::ptr::null()
}

fn effect_to_ffi(effect: &EngineEffect) -> i32 {
    match effect {
        EngineEffect::Play => FFI_EFFECT_PLAY,
        EngineEffect::Pause => FFI_EFFECT_PAUSE,
        EngineEffect::Stop => FFI_EFFECT_STOP,
        EngineEffect::Seek(_) => FFI_EFFECT_SEEK,
        EngineEffect::RequestAudioFocus => FFI_EFFECT_REQUEST_AUDIO_FOCUS,
        EngineEffect::AbandonAudioFocus => FFI_EFFECT_ABANDON_AUDIO_FOCUS,
        EngineEffect::UpdateMetadata { .. } => FFI_EFFECT_UPDATE_METADATA,
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

fn platform_event_from_ffi(event_type: i32) -> panda_engine_core::EnginePlatformEventType {
    use panda_engine_core::EnginePlatformEventType;
    match event_type {
        FFI_PLATFORM_EVENT_APP_FOREGROUNDED => EnginePlatformEventType::AppForegrounded,
        FFI_PLATFORM_EVENT_APP_BACKGROUNDED => EnginePlatformEventType::AppBackgrounded,
        FFI_PLATFORM_EVENT_SUSPEND_TO_RAM => EnginePlatformEventType::SuspendToRam,
        FFI_PLATFORM_EVENT_RESUME_FROM_RAM => EnginePlatformEventType::ResumeFromRam,
        FFI_PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED => EnginePlatformEventType::UxRestrictionsChanged,
        FFI_PLATFORM_EVENT_AUDIO_FOCUS_CHANGED => EnginePlatformEventType::AudioFocusChanged,
        FFI_PLATFORM_EVENT_MEDIA_LOADED => EnginePlatformEventType::MediaLoaded,
        FFI_PLATFORM_EVENT_MEDIA_ERROR => EnginePlatformEventType::MediaError,
        _ => EnginePlatformEventType::Unknown(event_type.to_string()),
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
    fn dispatch_play_emits_effects_in_ffi() {
        let engine = panda_engine_create(100);
        unsafe {
            let mut items = Vec::new();
            items.push(MediaItem { id: "1".to_string(), title: "S1".to_string(), artist: "A1".to_string() });
            (*engine).engine.queue().set_items(items);
        }

        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, 200) };
        
        let count = unsafe { panda_engine_get_effects_count(engine) };
        assert!(count >= 2); // UpdateMetadata, RequestFocus, Play

        let mut types = vec![0i32; count];
        unsafe { panda_engine_get_effects_types(engine, types.as_mut_ptr()) };
        
        assert!(types.contains(&FFI_EFFECT_PLAY));
        assert!(types.contains(&FFI_EFFECT_REQUEST_AUDIO_FOCUS));

        unsafe {
            panda_engine_destroy(engine);
        }
    }
}
