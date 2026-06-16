use std::ffi::{CString, c_char};
use std::ptr;

use panda_engine_core::{EngineEffect, EngineSnapshot};

use crate::constants::FFI_MEDIA_ITEM_TRACK;
use crate::mappings::effect_to_ffi;
use crate::mappings::media_item_type_to_ffi;
use crate::{FfiEngineConfig, FfiEngineSnapshot, PandaEngine};

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_snapshot(engine: *const PandaEngine) -> FfiEngineSnapshot {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => FfiEngineSnapshot::from(&engine.engine.snapshot()),
        None => FfiEngineSnapshot::invalid(),
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - Returned string pointers inside `FfiEngineConfig` are heap-allocated and must be released with
///   `panda_engine_free_string` when no longer needed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
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

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_media_id(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.media_id.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_title(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.title.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_artist(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.artist.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_album(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.album.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_thumbnail_url(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.thumbnail_url.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_source_uri(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.source_uri.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_mime_type(
    engine: *const PandaEngine,
) -> *const c_char {
    current_snapshot_string(engine, |snapshot| snapshot.mime_type.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_current_user_id(
    engine: *const PandaEngine,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(session) = &snapshot.session {
            return CString::new(session.user_id.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effects_count(engine: *const PandaEngine) -> usize {
    let engine = unsafe { engine.as_ref() };
    match engine {
        Some(engine) => engine.last_effects.lock().unwrap().len(),
        None => 0,
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `out_types` must point to writable memory for at least `panda_engine_get_effects_count(engine)` `i32` values.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effects_types(
    engine: *const PandaEngine,
    out_types: *mut i32,
) {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        for (i, effect) in effects.iter().enumerate() {
            unsafe { *out_types.add(i) = effect_to_ffi(effect) };
        }
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effect_type(
    engine: *const PandaEngine,
    index: usize,
) -> i32 {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(effect) = effects.get(index) {
            return effect_to_ffi(effect);
        }
    }
    crate::FFI_COMMAND_UNKNOWN
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effect_media_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(effect) = effects.get(index) {
            let media_id = match effect {
                EngineEffect::PreparePlaybackSource { media_id } => Some(media_id),
                EngineEffect::UpdateMetadata { media_id, .. } => Some(media_id),
                _ => None,
            };
            if let Some(media_id) = media_id {
                return CString::new(media_id.as_str()).unwrap().into_raw();
            }
        }
    }
    ptr::null()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effect_position_millis(
    engine: *const PandaEngine,
    index: usize,
) -> i64 {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(EngineEffect::Seek(position_millis)) = effects.get(index) {
            return (*position_millis).try_into().unwrap_or(i64::MAX);
        }
    }
    -1
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effect_speed(
    engine: *const PandaEngine,
    index: usize,
) -> f32 {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(EngineEffect::SetSpeed(speed)) = effects.get(index) {
            return *speed;
        }
    }
    f32::NAN
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_effect_notify_message(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let effects = engine.last_effects.lock().unwrap();
        if let Some(EngineEffect::NotifyUser { message }) = effects.get(index) {
            return CString::new(message.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_voice_hypothesis(
    engine: *const PandaEngine,
) -> *mut c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(hypothesis) = &snapshot.voice_hypothesis {
            return CString::new(hypothesis.as_str()).unwrap().into_raw();
        }
    }
    ptr::null_mut()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.search_results.get(index) {
            return CString::new(item.id.clone()).unwrap().into_raw();
        }
    }
    ptr::null()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_title(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| Some(&item.title))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_artist(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| Some(&item.artist))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_album(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| item.album.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_thumbnail_url(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| item.thumbnail_url.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_source_uri(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| item.source_uri.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_mime_type(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    search_result_string(engine, index, |item| item.mime_type.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_search_result_item_type(
    engine: *const PandaEngine,
    index: usize,
) -> i32 {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.search_results.get(index) {
            return media_item_type_to_ffi(&item.item_type);
        }
    }
    FFI_MEDIA_ITEM_TRACK
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_id(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.browse_results.get(index) {
            return CString::new(item.id.clone()).unwrap().into_raw();
        }
    }
    ptr::null()
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_title(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| Some(&item.title))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_artist(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| Some(&item.artist))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_album(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| item.album.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_thumbnail_url(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| item.thumbnail_url.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_source_uri(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| item.source_uri.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_mime_type(
    engine: *const PandaEngine,
    index: usize,
) -> *const c_char {
    browse_result_string(engine, index, |item| item.mime_type.as_ref())
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_browse_result_item_type(
    engine: *const PandaEngine,
    index: usize,
) -> i32 {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.browse_results.get(index) {
            return media_item_type_to_ffi(&item.item_type);
        }
    }
    FFI_MEDIA_ITEM_TRACK
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be null or a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer (when non-null) must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
pub unsafe extern "C" fn panda_engine_get_last_error_message(
    engine: *const PandaEngine,
) -> *const c_char {
    if engine.is_null() {
        return ptr::null();
    }
    let engine = unsafe { &*engine };
    let snapshot = engine.engine.snapshot();
    if let Some(error) = &snapshot.last_error {
        CString::new(error.message.clone()).unwrap().into_raw()
    } else {
        ptr::null()
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `s` must be null or a pointer previously returned by one of this crate's FFI string-returning functions.
/// - `s` must not be freed more than once and must not be used after this call.
pub unsafe extern "C" fn panda_engine_free_string(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    unsafe {
        let _ = CString::from_raw(s);
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The returned string pointer (when non-null) must be released with `panda_engine_free_string`.
/// - The caller must ensure no concurrent mutable access while this function reads engine state.
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

fn current_snapshot_string(
    engine: *const PandaEngine,
    value: fn(&EngineSnapshot) -> Option<&String>,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(value) = value(&snapshot) {
            return CString::new(value.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}

fn search_result_string(
    engine: *const PandaEngine,
    index: usize,
    value: fn(&panda_engine_core::MediaItem) -> Option<&String>,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.search_results.get(index)
            && let Some(value) = value(item)
        {
            return CString::new(value.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}

fn browse_result_string(
    engine: *const PandaEngine,
    index: usize,
    value: fn(&panda_engine_core::MediaItem) -> Option<&String>,
) -> *const c_char {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        let snapshot = engine.engine.snapshot();
        if let Some(item) = snapshot.browse_results.get(index)
            && let Some(value) = value(item)
        {
            return CString::new(value.as_str()).unwrap().into_raw();
        }
    }
    ptr::null()
}
