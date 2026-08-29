use std::ffi::c_char;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Arc;

use futures_util::FutureExt;
use panda_engine_core::VoskVoiceEngine;
use tracing::info;
use tracing_subscriber::filter::LevelFilter as TracingLevelFilter;
use tracing_subscriber::prelude::*;

use crate::engine_handle::{FfiObserver, build_engine, remember_outcome};
use crate::{FfiEngineSnapshot, PandaEngine};

fn run_future_safely<T>(
    runtime: &tokio::runtime::Runtime,
    future: impl std::future::Future<Output = T>,
) -> Option<T> {
    let future_result = catch_unwind(AssertUnwindSafe(|| {
        runtime.block_on(AssertUnwindSafe(future).catch_unwind())
    }));

    match future_result {
        Ok(Ok(value)) => Some(value),
        Ok(Err(_)) | Err(_) => None,
    }
}

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

    let tracing_level = tracing_level(max_level);
    #[cfg(target_os = "android")]
    let _ = tracing_subscriber::registry()
        .with(
            tracing_subscriber::fmt::layer()
                .with_ansi(false)
                .without_time()
                .with_target(false)
                .with_writer(android_tracing::AndroidLogMakeWriter)
                .with_filter(tracing_level),
        )
        .try_init();
    #[cfg(not(target_os = "android"))]
    let _ = tracing_subscriber::registry()
        .with(
            tracing_subscriber::fmt::layer()
                .with_ansi(false)
                .with_writer(std::io::stdout)
                .with_filter(tracing_level),
        )
        .try_init();

    info!("PandaEngine logging initialized with level {:?}", level);
}

fn tracing_level(max_level: i32) -> TracingLevelFilter {
    match max_level {
        0 => TracingLevelFilter::OFF,
        1 => TracingLevelFilter::ERROR,
        2 => TracingLevelFilter::WARN,
        3 => TracingLevelFilter::INFO,
        4 => TracingLevelFilter::DEBUG,
        5 => TracingLevelFilter::TRACE,
        _ => TracingLevelFilter::INFO,
    }
}

#[cfg(target_os = "android")]
mod android_tracing {
    use std::ffi::CString;
    use std::io::{self, Write};

    use android_log_sys::{__android_log_write, LogPriority};
    use tracing::{Level, Metadata};
    use tracing_subscriber::fmt::MakeWriter;

    pub(super) struct AndroidLogMakeWriter;

    pub(super) struct AndroidLogWriter {
        priority: i32,
        buffer: Vec<u8>,
    }

    impl AndroidLogWriter {
        fn new(priority: LogPriority) -> Self {
            Self {
                priority: priority as i32,
                buffer: Vec::new(),
            }
        }

        fn emit(&mut self) {
            if self.buffer.is_empty() {
                return;
            }
            let text = String::from_utf8_lossy(&self.buffer)
                .trim_end_matches(|character| character == '\r' || character == '\n')
                .replace('\0', "�");
            if let (Ok(tag), Ok(message)) = (CString::new("PandaEngine"), CString::new(text)) {
                unsafe {
                    __android_log_write(self.priority, tag.as_ptr(), message.as_ptr());
                }
            }
            self.buffer.clear();
        }
    }

    impl Write for AndroidLogWriter {
        fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
            self.buffer.extend_from_slice(bytes);
            Ok(bytes.len())
        }

        fn flush(&mut self) -> io::Result<()> {
            self.emit();
            Ok(())
        }
    }

    impl Drop for AndroidLogWriter {
        fn drop(&mut self) {
            self.emit();
        }
    }

    impl<'writer> MakeWriter<'writer> for AndroidLogMakeWriter {
        type Writer = AndroidLogWriter;

        fn make_writer(&'writer self) -> Self::Writer {
            AndroidLogWriter::new(LogPriority::INFO)
        }

        fn make_writer_for(&'writer self, metadata: &Metadata<'_>) -> Self::Writer {
            let priority = match *metadata.level() {
                Level::ERROR => LogPriority::ERROR,
                Level::WARN => LogPriority::WARN,
                Level::INFO => LogPriority::INFO,
                Level::DEBUG => LogPriority::DEBUG,
                Level::TRACE => LogPriority::VERBOSE,
            };
            AndroidLogWriter::new(priority)
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn panda_engine_create(now_epoch_millis: u64) -> *mut PandaEngine {
    let _trace = crate::perfetto_trace::section("PW.Native.create");
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
            .with_engine(move |e| e.event_bus().subscribe(Box::new(observer)));
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
    let _trace = crate::perfetto_trace::section("PW.Native.tick");
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        let outcomes =
            match run_future_safely(&engine.runtime, engine.engine.tick(now_epoch_millis)) {
                Some(Ok(outcomes)) => outcomes,
                None => return 0,
                Some(Err(_)) => return 0,
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
                    .with_engine(move |e| e.set_voice_engine(Box::new(vosk)));
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

#[cfg(test)]
mod logging_tests {
    use super::*;

    #[test]
    fn native_logging_levels_map_to_tracing_filters() {
        assert_eq!(TracingLevelFilter::OFF, tracing_level(0));
        assert_eq!(TracingLevelFilter::ERROR, tracing_level(1));
        assert_eq!(TracingLevelFilter::WARN, tracing_level(2));
        assert_eq!(TracingLevelFilter::INFO, tracing_level(3));
        assert_eq!(TracingLevelFilter::DEBUG, tracing_level(4));
        assert_eq!(TracingLevelFilter::TRACE, tracing_level(5));
        assert_eq!(TracingLevelFilter::INFO, tracing_level(99));
    }
}
