use std::ffi::c_char;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Arc;

use panda_engine_core::VoskVoiceEngine;
use tracing::info;
use tracing_subscriber::prelude::*;

use crate::engine_handle::{FfiObserver, build_engine, remember_outcome};
use crate::{FfiEngineSnapshot, PandaEngine};

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

#[unsafe(no_mangle)]
pub extern "C" fn panda_engine_create(now_epoch_millis: u64) -> *mut PandaEngine {
    Box::into_raw(Box::new(build_engine(now_epoch_millis)))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `on_state_changed` and `on_event_emitted` must be valid function pointers for the duration of observer usage.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
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

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_tick(
    engine: *mut PandaEngine,
    now_epoch_millis: u64,
) -> usize {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let outcomes = match catch_unwind(AssertUnwindSafe(|| {
            engine.runtime.block_on(engine.engine.tick(now_epoch_millis))
        })) {
            Ok(outcomes) => outcomes,
            Err(_) => return 0,
        };
        if let Some(last) = outcomes.last() {
            remember_outcome(engine, last);
        }
        outcomes.len()
    } else {
        0
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must either be null or a pointer previously returned by `panda_engine_create`.
/// - If non-null, `engine` must not be used again after this call.
pub unsafe extern "C" fn panda_engine_destroy(engine: *mut PandaEngine) {
    if !engine.is_null() {
        drop(unsafe { Box::from_raw(engine) });
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `model_path` must be a valid, non-null NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_enable_vosk(
    engine: *mut PandaEngine,
    model_path: *const c_char,
) -> bool {
    let engine = unsafe { engine.as_mut() };
    let model_path = unsafe { std::ffi::CStr::from_ptr(model_path).to_str() };

    if let (Some(engine), Ok(path)) = (engine, model_path) {
        match VoskVoiceEngine::new(path) {
            Ok(vosk) => {
                engine
                    .engine
                    .with_engine(|e| e.set_voice_engine(Box::new(vosk)));
                true
            }
            Err(e) => {
                tracing::error!("Failed to enable Vosk: {}", e);
                false
            }
        }
    } else {
        false
    }
}
