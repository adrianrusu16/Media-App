use std::ffi::c_char;

use panda_engine_core::{MediaItem, RepeatMode};

use crate::PandaEngine;

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `ids`, `titles`, and `artists` must each point to arrays of at least `count` valid C-string pointers.
/// - Every non-null string pointer in those arrays must point to a valid NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
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

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
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

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_queue_set_shuffle(engine: *mut PandaEngine, enabled: bool) {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        engine
            .engine
            .with_engine(|e| e.queue().set_shuffle(enabled));
    }
}
