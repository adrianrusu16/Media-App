use super::super::*;
use panda_engine_core::MediaItem;
use std::ffi::{CString, c_char};
use std::ptr;

#[test]
fn dispatch_play_returns_buffering_snapshot() {
    let engine = panda_engine_create(1000);
    unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 500) };
    let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 900) };
    unsafe {
        panda_engine_destroy(engine);
    }

    assert_eq!(FFI_PLAYBACK_BUFFERING, outcome.snapshot.playback_state);
    assert!(outcome.snapshot.has_active_session);
    assert_eq!(FFI_EVENT_COMMAND_APPLIED, outcome.event_type);
    assert_eq!(FFI_COMMAND_PLAY, outcome.applied_command_type);
    assert_eq!(900, outcome.snapshot.updated_at_epoch_millis);
}

#[test]
fn start_session_uses_payload_as_user_id() {
    let engine = panda_engine_create(1000);
    let user_id = CString::new("android-user-42").unwrap();

    let outcome =
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, user_id.as_ptr(), 500) };

    let session_user_id = unsafe {
        (*engine).engine.with_engine(|e| {
            e.snapshot()
                .session
                .as_ref()
                .map(|session| session.user_id.clone())
        })
    };

    assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);
    assert_eq!(session_user_id.as_deref(), Some("android-user-42"));

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn start_session_defaults_to_unknown_when_payload_is_missing() {
    let engine = panda_engine_create(1000);

    let outcome =
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 500) };

    let session_user_id = unsafe {
        (*engine).engine.with_engine(|e| {
            e.snapshot()
                .session
                .as_ref()
                .map(|session| session.user_id.clone())
        })
    };

    assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);
    assert_eq!(session_user_id.as_deref(), Some("unknown"));

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn null_snapshot_returns_invalid_marker() {
    let snapshot = unsafe { panda_engine_snapshot(ptr::null()) };

    assert_eq!(FFI_COMMAND_UNKNOWN, snapshot.playback_state);
    assert_eq!(0, snapshot.updated_at_epoch_millis);
}

#[test]
fn dispatch_play_emits_effects_in_ffi() {
    let engine = panda_engine_create(1000);
    unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 500) };
    unsafe {
        let items = vec![MediaItem {
            id: "1".to_string(),
            title: "S1".to_string(),
            artist: "A1".to_string(),
            ..Default::default()
        }];
        (*engine).engine.with_engine(|e| e.queue().set_items(items));
    }

    unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 900) };

    let count = unsafe { panda_engine_get_effects_count(engine) };
    assert!(count >= 2);

    let mut types = vec![0i32; count];
    unsafe { panda_engine_get_effects_types(engine, types.as_mut_ptr()) };

    assert!(types.contains(&FFI_EFFECT_PLAY));
    assert!(types.contains(&FFI_EFFECT_REQUEST_AUDIO_FOCUS));

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn current_media_queries_follow_snapshot_metadata() {
    let engine = panda_engine_create(1000);
    let user_id = CString::new("driver-7").unwrap();
    unsafe { panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, user_id.as_ptr(), 500) };
    unsafe {
        let items = vec![MediaItem {
            id: "track-7".to_string(),
            title: "Bamboo Road".to_string(),
            artist: "PandaWave".to_string(),
            ..Default::default()
        }];
        (*engine).engine.with_engine(|e| e.queue().set_items(items));
    }

    unsafe { panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 900) };

    let media_id = unsafe { take_string(panda_engine_get_current_media_id(engine)) };
    let title = unsafe { take_string(panda_engine_get_current_title(engine)) };
    let artist = unsafe { take_string(panda_engine_get_current_artist(engine)) };
    let current_user_id = unsafe { take_string(panda_engine_get_current_user_id(engine)) };

    assert_eq!(Some("track-7".to_string()), media_id);
    assert_eq!(Some("Bamboo Road".to_string()), title);
    assert_eq!(Some("PandaWave".to_string()), artist);
    assert_eq!(Some("driver-7".to_string()), current_user_id);

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn search_updates_snapshot_results() {
    let engine = panda_engine_create(1000);
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
    let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_SEARCH, query.as_ptr(), 500) };

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

unsafe fn take_string(value: *const c_char) -> Option<String> {
    if value.is_null() {
        return None;
    }

    Some(
        unsafe { CString::from_raw(value as *mut c_char) }
            .to_string_lossy()
            .into_owned(),
    )
}

#[test]
fn browse_keeps_search_results_separate() {
    let engine = panda_engine_create(1000);
    unsafe {
        let items = vec![
            MediaItem {
                id: "search-1".to_string(),
                title: "Rust Song".to_string(),
                artist: "A".to_string(),
                ..Default::default()
            },
            MediaItem {
                id: "browse-1".to_string(),
                title: "Playlist Item".to_string(),
                artist: "B".to_string(),
                parent_id: Some("root".to_string()),
                ..Default::default()
            },
        ];
        (*engine).engine.with_engine(|e| {
            e.set_repository(Box::new(panda_engine_core::InMemoryRepository::new(items)))
        });
    }

    let query = CString::new("Rust").unwrap();
    let search_outcome =
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_SEARCH, query.as_ptr(), 500) };
    assert_eq!(1, search_outcome.snapshot.search_results_count);
    assert_eq!(0, search_outcome.snapshot.browse_results_count);

    let parent = CString::new("root").unwrap();
    let browse_outcome =
        unsafe { panda_engine_dispatch(engine, FFI_COMMAND_BROWSE, parent.as_ptr(), 900) };

    assert_eq!(1, browse_outcome.snapshot.search_results_count);
    assert_eq!(1, browse_outcome.snapshot.browse_results_count);

    let browse_id_ptr = unsafe { panda_engine_get_browse_result_id(engine, 0) };
    let browse_id = unsafe { CString::from_raw(browse_id_ptr as *mut c_char) }
        .to_string_lossy()
        .into_owned();
    assert_eq!("browse-1", browse_id);

    let search_id_ptr = unsafe { panda_engine_get_search_result_id(engine, 0) };
    let search_id = unsafe { CString::from_raw(search_id_ptr as *mut c_char) }
        .to_string_lossy()
        .into_owned();
    assert_eq!("search-1", search_id);

    unsafe {
        panda_engine_destroy(engine);
    }
}
