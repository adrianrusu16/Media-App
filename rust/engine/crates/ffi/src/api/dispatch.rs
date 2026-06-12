use std::ffi::c_char;

use panda_engine_core::{EngineCommand, EngineCommandType};

use crate::engine_handle::remember_outcome;
use crate::mappings::{command_from_ffi, platform_event_from_ffi};
use crate::{
    FFI_COMMAND_BROWSE, FFI_COMMAND_PROCESS_VOICE, FFI_COMMAND_SEARCH, FFI_COMMAND_SEEK,
    FFI_COMMAND_SET_SPEED, FFI_COMMAND_UNKNOWN, FfiEngineOutcome, PandaEngine,
};

fn dispatch_voice_chunk(
    engine: &mut PandaEngine,
    chunk: Vec<i16>,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let command = EngineCommand::process_voice_audio(chunk);
    let outcome = engine
        .runtime
        .block_on(engine.engine.dispatch(command, now_epoch_millis));
    remember_outcome(engine, &outcome);
    FfiEngineOutcome::from((&outcome, FFI_COMMAND_PROCESS_VOICE))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `payload` may be null; if non-null, it must point to a valid NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
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
                        query: payload_str.clone().unwrap_or_default(),
                    },
                    None,
                ),
                FFI_COMMAND_BROWSE => EngineCommand::new(
                    EngineCommandType::Browse {
                        parent_id: payload_str.clone().unwrap_or_else(|| "root".to_string()),
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
                FFI_COMMAND_PROCESS_VOICE => {
                    let chunk = payload_str
                        .unwrap_or_default()
                        .split(',')
                        .filter_map(|s| s.trim().parse::<i16>().ok())
                        .collect();
                    return dispatch_voice_chunk(engine, chunk, now_epoch_millis);
                }
                _ => EngineCommand::new(command_from_ffi(command_type), payload_str),
            };

            let outcome = engine
                .runtime
                .block_on(engine.engine.dispatch(command, now_epoch_millis));
            remember_outcome(engine, &outcome);
            FfiEngineOutcome::from((&outcome, command_type))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `audio` must point to a readable buffer of `len` `i16` samples unless `len == 0`.
/// - If `len > 0`, `audio` must be non-null and valid for reads for the duration of this call.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_process_audio_raw(
    engine: *mut PandaEngine,
    audio: *const i16,
    len: usize,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            if len > 0 && audio.is_null() {
                return FfiEngineOutcome::invalid();
            }
            let chunk = if len == 0 {
                Vec::new()
            } else {
                unsafe { std::slice::from_raw_parts(audio, len) }.to_vec()
            };
            dispatch_voice_chunk(engine, chunk, now_epoch_millis)
        }
        None => FfiEngineOutcome::invalid(),
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `_payload` may be null; if non-null, it must point to a valid NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_dispatch_platform_event(
    engine: *mut PandaEngine,
    event_type: i32,
    _payload: *const c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            let outcome = engine
                .runtime
                .block_on(engine.engine.dispatch_platform_event(
                    panda_engine_core::EnginePlatformEvent::new(
                        platform_event_from_ffi(event_type),
                        None,
                    ),
                    now_epoch_millis,
                ));
            remember_outcome(engine, &outcome);
            FfiEngineOutcome::from((&outcome, FFI_COMMAND_UNKNOWN))
        }
        None => FfiEngineOutcome::invalid(),
    }
}
