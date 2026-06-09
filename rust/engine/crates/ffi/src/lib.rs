use std::ffi::{CString, c_char};
use std::ptr;
use std::sync::{Arc, Mutex};
use tracing::info;
use tracing_subscriber::prelude::*;

use panda_engine_core::{
    ConcurrentEngine, Engine, EngineCommand, EngineCommandType, EngineEffect, EngineEvent,
    EngineEventType, EngineObserver, EngineOutcome, EngineSnapshot, LoggerMiddleware, MediaItem,
    MiddlewarePipeline, PlaybackState, RepeatMode, RestrictionState, TelemetryMiddleware,
};

pub const FFI_COMMAND_BOOTSTRAP: i32 = 0;
pub const FFI_COMMAND_PLAY: i32 = 1;
pub const FFI_COMMAND_PAUSE: i32 = 2;
pub const FFI_COMMAND_SKIP_PREVIOUS: i32 = 3;
pub const FFI_COMMAND_SKIP_NEXT: i32 = 4;
pub const FFI_COMMAND_START_SESSION: i32 = 5;
pub const FFI_COMMAND_END_SESSION: i32 = 6;
pub const FFI_COMMAND_SEARCH: i32 = 7;
pub const FFI_COMMAND_BROWSE: i32 = 8;
pub const FFI_COMMAND_SET_SPEED: i32 = 9;
pub const FFI_COMMAND_SEEK: i32 = 10;
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
pub const FFI_EVENT_ANALYTICS_REPORTED: i32 = 2;

pub const FFI_EFFECT_PLAY: i32 = 0;
pub const FFI_EFFECT_PAUSE: i32 = 1;
pub const FFI_EFFECT_STOP: i32 = 2;
pub const FFI_EFFECT_SEEK: i32 = 3;
pub const FFI_EFFECT_REQUEST_AUDIO_FOCUS: i32 = 4;
pub const FFI_EFFECT_ABANDON_AUDIO_FOCUS: i32 = 5;
pub const FFI_EFFECT_UPDATE_METADATA: i32 = 6;
pub const FFI_EFFECT_SESSION_STARTED: i32 = 7;
pub const FFI_EFFECT_SESSION_ENDED: i32 = 8;
pub const FFI_EFFECT_SET_SPEED: i32 = 9;

pub const FFI_ERROR_NONE: i32 = 0;
pub const FFI_ERROR_NOT_FOUND: i32 = 1;
pub const FFI_ERROR_NETWORK: i32 = 2;
pub const FFI_ERROR_PLAYER: i32 = 3;
pub const FFI_ERROR_AUTHENTICATION: i32 = 4;
pub const FFI_ERROR_MEDIA_SKIPPED: i32 = 5;
pub const FFI_ERROR_UNKNOWN: i32 = -1;

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
    pub has_active_session: bool,
    pub has_error: bool,
    pub error_type: i32,
    pub search_results_count: usize,
    pub playback_speed: f32,
    pub position_millis: u64,
    pub is_busy: bool,
    pub can_dispatch: bool,
    pub controls: FfiPlayerControls,
}

/// C-compatible representation of the engine outcome.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiEngineOutcome {
    pub snapshot: FfiEngineSnapshot,
    pub event_type: i32,
    pub applied_command_type: i32,
}

/// Opaque handle to the Rust Engine.
pub struct PandaEngine {
    engine: ConcurrentEngine,
    last_effects: Arc<Mutex<Vec<EngineEffect>>>,
    last_event: Arc<Mutex<Option<EngineEvent>>>,
    observer: Option<Arc<FfiObserver>>,
}

struct FfiObserver {
    on_state_changed: unsafe extern "C" fn(FfiEngineSnapshot),
    on_event_emitted: unsafe extern "C" fn(i32),
    last_event: Arc<Mutex<Option<EngineEvent>>>,
}

unsafe impl Send for FfiObserver {}
unsafe impl Sync for FfiObserver {}

impl EngineObserver for FfiObserver {
    fn on_state_changed(&self, snapshot: &EngineSnapshot) {
        info!("FFI: Notifying observer of state change");
        let ffi_snapshot = FfiEngineSnapshot::from(snapshot);
        unsafe { (self.on_state_changed)(ffi_snapshot) };
    }

    fn on_event_emitted(&self, event: &EngineEvent) {
        info!("FFI: Notifying observer of event {:?}", event.event_type);
        {
            let mut last = self.last_event.lock().unwrap();
            *last = Some(event.clone());
        }
        let event_type = event_to_ffi(&event.event_type);
        unsafe { (self.on_event_emitted)(event_type) };
    }
}

/// Initializes the logging system for the PandaEngine.
///
/// This should be called once at application startup.
/// On Android, this redirects Rust logs to Logcat.
///
/// # Safety
/// This function is safe to call multiple times, but only the first call will initialize the logger.
#[unsafe(no_mangle)]
pub extern "C" fn panda_engine_init_logging(max_level: i32) {
    use android_logger::Config;
    use log::LevelFilter;

    let level = match max_level {
        0 => LevelFilter::Off,
        1 => LevelFilter::Error,
        2 => LevelFilter::Warn,
        3 => LevelFilter::Info,
        4 => LevelFilter::Debug,
        5 => LevelFilter::Trace,
        _ => LevelFilter::Info,
    };

    android_logger::init_once(
        Config::default()
            .with_max_level(level)
            .with_tag("PandaEngine"),
    );

    let _ = tracing_subscriber::registry()
        .with(tracing_subscriber::fmt::layer().with_writer(std::io::stdout))
        .try_init();

    info!("PandaEngine logging initialized with level {:?}", level);
}

/// Creates a new PandaEngine instance.
///
/// # Safety
/// The caller is responsible for destroying the engine using [panda_engine_destroy].
#[unsafe(no_mangle)]
pub extern "C" fn panda_engine_create(now_epoch_millis: u64) -> *mut PandaEngine {
    let mut engine = Engine::new(now_epoch_millis);

    // Set up default state-of-the-art middleware (e.g., logging)
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(LoggerMiddleware));
    let bus = engine.event_bus();
    pipeline.add(Box::new(TelemetryMiddleware::new(bus.clone())));
    pipeline.add(Box::new(panda_engine_core::AnalyticsMiddleware::new(
        bus.clone(),
    )));
    pipeline.add(Box::new(panda_engine_core::ThrottlingMiddleware::new(300))); // 300ms throttle
    pipeline.add(Box::new(panda_engine_core::FocusMiddleware));
    engine.set_middleware(pipeline);

    Box::into_raw(Box::new(PandaEngine {
        engine: ConcurrentEngine::new(engine),
        last_effects: Arc::new(Mutex::new(Vec::new())),
        last_event: Arc::new(Mutex::new(None)),
        observer: None,
    }))
}

/// Registers callbacks for engine observability.
///
/// # Safety
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_set_observer(
    engine: *mut PandaEngine,
    on_state_changed: unsafe extern "C" fn(FfiEngineSnapshot),
    on_event_emitted: unsafe extern "C" fn(i32),
) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let observer = Arc::new(FfiObserver {
            on_state_changed,
            on_event_emitted,
            last_event: engine.last_event.clone(),
        });
        engine.observer = Some(observer.clone());
        engine
            .engine
            .with_engine(|e| e.event_bus().subscribe(Box::new(observer)));
    }
}

/// Advances the engine's internal state by one tick.
///
/// # Safety
/// The `engine` pointer must be a valid, non-null pointer to a `PandaEngine`
/// previously created with `panda_engine_create`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_tick(
    engine: *mut PandaEngine,
    now_epoch_millis: u64,
) -> usize {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let outcomes = engine.engine.tick(now_epoch_millis);
        if let Some(last) = outcomes.last() {
            {
                let mut effects = engine.last_effects.lock().unwrap();
                *effects = last.effects.clone();
            }
            {
                let mut event = engine.last_event.lock().unwrap();
                *event = Some(last.event.clone());
            }
        }
        outcomes.len()
    } else {
        0
    }
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
        Some(engine) => {
            let snapshot = engine.engine.snapshot();
            FfiEngineSnapshot::from(&snapshot)
        }
        None => FfiEngineSnapshot::invalid(),
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
/// The caller is responsible for freeing the strings in the returned config using [panda_engine_free_string].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_config(engine: *const PandaEngine) -> FfiEngineConfig {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => {
            let config = engine.engine.config();
            FfiEngineConfig {
                vehicle_name: CString::new(config.vehicle_name.clone())
                    .unwrap()
                    .into_raw(),
                hifi_enabled: config.hifi_enabled,
                max_volume: config.max_volume,
                auto_resume: config.auto_resume,
                preferred_language: CString::new(config.preferred_language.clone())
                    .unwrap()
                    .into_raw(),
            }
        }
        None => FfiEngineConfig {
            vehicle_name: ptr::null(),
            hifi_enabled: false,
            max_volume: 0,
            auto_resume: false,
            preferred_language: ptr::null(),
        },
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_dispatch(
    engine: *mut PandaEngine,
    command_type: i32,
    payload: *const c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    let payload_str = if payload.is_null() {
        None
    } else {
        Some(
            unsafe { std::ffi::CStr::from_ptr(payload) }
                .to_string_lossy()
                .into_owned(),
        )
    };

    match engine {
        Some(engine) => {
            let command = match command_type {
                FFI_COMMAND_SEARCH => EngineCommand::new(
                    EngineCommandType::Search {
                        query: payload_str.unwrap_or_default(),
                    },
                    None,
                ),
                FFI_COMMAND_BROWSE => EngineCommand::new(
                    EngineCommandType::Browse {
                        parent_id: payload_str.unwrap_or_else(|| "root".to_string()),
                    },
                    None,
                ),
                FFI_COMMAND_SET_SPEED => {
                    let speed = payload_str
                        .and_then(|s| s.parse::<f32>().ok())
                        .unwrap_or(1.0);
                    EngineCommand::new(EngineCommandType::SetSpeed { speed }, None)
                }
                FFI_COMMAND_SEEK => {
                    let pos = payload_str.and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
                    EngineCommand::new(
                        EngineCommandType::Seek {
                            position_millis: pos,
                        },
                        None,
                    )
                }
                _ => EngineCommand::new(command_from_ffi(command_type), payload_str),
            };

            let outcome = engine.engine.dispatch(command, now_epoch_millis);
            {
                let mut effects = engine.last_effects.lock().unwrap();
                *effects = outcome.effects.clone();
            }
            {
                let mut event = engine.last_event.lock().unwrap();
                *event = Some(outcome.event.clone());
            }
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
    _payload: *const c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            let outcome = engine.engine.dispatch_platform_event(
                panda_engine_core::EnginePlatformEvent::new(
                    platform_event_from_ffi(event_type),
                    None,
                ),
                now_epoch_millis,
            );
            {
                let mut effects = engine.last_effects.lock().unwrap();
                *effects = outcome.effects.clone();
            }
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
    ids: *const *const c_char,
    titles: *const *const c_char,
    artists: *const *const c_char,
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
            items.push(MediaItem {
                id,
                title,
                artist,
                ..Default::default()
            });
        }
        engine.engine.with_engine(|e| e.queue().set_items(items));
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
        engine
            .engine
            .with_engine(|e| e.queue().set_repeat_mode(repeat_mode));
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_queue_set_shuffle(engine: *mut PandaEngine, enabled: bool) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        engine
            .engine
            .with_engine(|e| e.queue().set_shuffle(enabled));
    }
}

/// # Safety
///
/// [engine] must be a valid pointer returned by [panda_engine_create].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_effects_count(engine: *const PandaEngine) -> usize {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => {
            let effects = engine.last_effects.lock().unwrap();
            effects.len()
        }
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
        let effects = engine.last_effects.lock().unwrap();
        for (i, effect) in effects.iter().enumerate() {
            unsafe {
                *out_types.add(i) = effect_to_ffi(effect);
            }
        }
    }
}

/// Returns the media ID for the effect at the specified index.
///
/// # Safety
/// The `engine` pointer must be a valid, non-null pointer to a `PandaEngine`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_effect_media_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(EngineEffect::UpdateMetadata { media_id, .. }) = effects.get(index) {
            // Leak for simplicity in this prototype or use a better buffer management
            return CString::new(media_id.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
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
        EngineEffect::SessionStarted { .. } => FFI_EFFECT_SESSION_STARTED,
        EngineEffect::SessionEnded => FFI_EFFECT_SESSION_ENDED,
        EngineEffect::SetSpeed(_) => FFI_EFFECT_SET_SPEED,
    }
}

impl FfiEngineSnapshot {
    fn invalid() -> Self {
        Self {
            playback_state: FFI_COMMAND_UNKNOWN,
            restriction_state: FFI_COMMAND_UNKNOWN,
            updated_at_epoch_millis: 0,
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
            has_active_session: snapshot.session.is_some(),
            has_error: snapshot.last_error.is_some(),
            error_type: snapshot
                .last_error
                .as_ref()
                .map(|e| match e.error_type {
                    panda_engine_core::EngineErrorType::NotFound => FFI_ERROR_NOT_FOUND,
                    panda_engine_core::EngineErrorType::NetworkError => FFI_ERROR_NETWORK,
                    panda_engine_core::EngineErrorType::PlayerError => FFI_ERROR_PLAYER,
                    panda_engine_core::EngineErrorType::AuthenticationError => {
                        FFI_ERROR_AUTHENTICATION
                    }
                    panda_engine_core::EngineErrorType::MediaSkipped => FFI_ERROR_MEDIA_SKIPPED,
                    panda_engine_core::EngineErrorType::Unknown => FFI_ERROR_UNKNOWN,
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

/// Returns the ID of the search result at the specified index.
///
/// # Safety
/// The `engine` pointer must be a valid, non-null pointer to a `PandaEngine`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_search_result_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.search_results.get(index) {
            let c_str = CString::new(item.id.clone()).unwrap();
            return c_str.into_raw();
        }
    }
    ptr::null()
}

/// Returns the title of the search result at the specified index.
///
/// # Safety
/// The `engine` pointer must be a valid, non-null pointer to a `PandaEngine`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_search_result_title(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.search_results.get(index) {
            let c_str = CString::new(item.title.clone()).unwrap();
            return c_str.into_raw();
        }
    }
    ptr::null()
}

/// Returns the last error message from the engine.
///
/// # Safety
/// The `engine` pointer must be a valid, non-null pointer to a `PandaEngine`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_last_error_message(
    engine: *const PandaEngine,
) -> *const c_char {
    if engine.is_null() {
        return ptr::null();
    }
    let engine = unsafe { &*engine };
    let snapshot = engine.engine.snapshot();
    if let Some(error) = &snapshot.last_error {
        let c_str = CString::new(error.message.clone()).unwrap();
        c_str.into_raw()
    } else {
        ptr::null()
    }
}

/// Frees a string allocated by the Rust engine.
///
/// # Safety
/// The `s` pointer must be a valid, non-null pointer to a string
/// previously allocated by one of the engine's FFI functions.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_free_string(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    unsafe {
        let _ = CString::from_raw(s);
    }
}

fn command_from_ffi(command_type: i32) -> EngineCommandType {
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
        FFI_PLATFORM_EVENT_UX_RESTRICTIONS_CHANGED => {
            EnginePlatformEventType::UxRestrictionsChanged
        }
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
        EngineEventType::AnalyticsReported => FFI_EVENT_ANALYTICS_REPORTED,
    }
}

/// Retrieves the last event's message/payload.
///
/// # Safety
/// [engine] must be a valid pointer. Returns a pointer to a C-string that must be freed by the caller.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_get_last_event_message(
    engine: *const PandaEngine,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let event = engine.last_event.lock().unwrap();
        if let Some(event) = &*event
            && let Some(msg) = &event.message
        {
            return CString::new(msg.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dispatch_play_returns_buffering_snapshot() {
        let engine = panda_engine_create(100);
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 150) };
        let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 200) };
        unsafe {
            panda_engine_destroy(engine);
        }

        assert_eq!(FFI_PLAYBACK_BUFFERING, outcome.snapshot.playback_state);
        assert!(outcome.snapshot.has_active_session);
        assert_eq!(FFI_EVENT_COMMAND_APPLIED, outcome.event_type);
        assert_eq!(FFI_COMMAND_PLAY, outcome.applied_command_type);
        assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
    }

    #[test]
    fn null_snapshot_returns_invalid_marker() {
        let snapshot = unsafe { panda_engine_snapshot(ptr::null()) };

        assert_eq!(FFI_COMMAND_UNKNOWN, snapshot.playback_state);
        assert_eq!(0, snapshot.updated_at_epoch_millis);
    }

    #[test]
    fn dispatch_play_emits_effects_in_ffi() {
        let engine = panda_engine_create(100);
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 150) };
        unsafe {
            let items = vec![MediaItem {
                id: "1".to_string(),
                title: "S1".to_string(),
                artist: "A1".to_string(),
                ..Default::default()
            }];
            (*engine).engine.with_engine(|e| e.queue().set_items(items));
        }

        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 200) };

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

    #[test]
    fn search_updates_snapshot_results() {
        let engine = panda_engine_create(100);
        unsafe {
            let items = vec![MediaItem {
                id: "1".to_string(),
                title: "Rust Song".to_string(),
                artist: "A".to_string(),
                ..Default::default()
            }];
            (*engine).engine.with_engine(|e| {
                e.set_repository(Box::new(panda_engine_core::InMemoryRepository::new(items)))
            });
        }

        let query = CString::new("Rust").unwrap();
        let outcome =
            unsafe { panda_engine_dispatch(engine, FFI_COMMAND_SEARCH, query.as_ptr(), 200) };

        assert_eq!(1, outcome.snapshot.search_results_count);

        let id_ptr = unsafe { panda_engine_get_search_result_id(engine, 0) };
        let id = unsafe { CString::from_raw(id_ptr as *mut c_char) }
            .to_string_lossy()
            .into_owned();
        assert_eq!("1", id);

        unsafe {
            panda_engine_destroy(engine);
        }
    }

    #[test]
    fn analytics_middleware_reports_events() {
        let engine = panda_engine_create(100);
        unsafe {
            panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 150);
        }

        // We check after a command that should trigger an analytics report in before_dispatch
        // or after_dispatch. Since TelemetryMiddleware also reports the final event,
        // we might need to be careful.

        unsafe {
            panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 200);
        }

        let msg_ptr = unsafe { panda_engine_get_last_event_message(engine) };
        assert!(!msg_ptr.is_null());

        let msg = unsafe { CString::from_raw(msg_ptr as *mut c_char) }
            .to_string_lossy()
            .into_owned();

        // The last event might be "command_applied" from TelemetryMiddleware,
        // OR it might be "state_transition" from AnalyticsMiddleware if it ran last.
        // Given the order in panda_engine_create:
        // 1. TelemetryMiddleware
        // 2. AnalyticsMiddleware
        // after_dispatch runs in order. So AnalyticsMiddleware runs AFTER TelemetryMiddleware?
        // Wait, MiddlewarePipeline::after_dispatch:
        // for mw in &self.middlewares { mw.after_dispatch(engine, outcome); }
        // So yes, AnalyticsMiddleware runs second, its report should be last.

        assert!(
            msg.contains("state_transition")
                || msg.contains("play_requested")
                || msg.contains("play")
        );

        unsafe {
            panda_engine_destroy(engine);
        }
    }
}
