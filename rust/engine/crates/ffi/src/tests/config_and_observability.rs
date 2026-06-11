use super::super::*;
use std::ffi::{CString, c_char};
use std::ptr;

#[test]
fn analytics_middleware_reports_events() {
    let engine = panda_engine_create(100);
    unsafe {
        panda_engine_dispatch(engine, FFI_COMMAND_START_SESSION, ptr::null(), 150);
        panda_engine_dispatch(engine, FFI_COMMAND_PLAY, ptr::null(), 200);
    }

    let msg_ptr = unsafe { panda_engine_get_last_event_message(engine) };
    assert!(!msg_ptr.is_null());

    let msg = unsafe { CString::from_raw(msg_ptr as *mut c_char) }
        .to_string_lossy()
        .into_owned();

    assert!(
        msg.contains("state_transition") || msg.contains("play_requested") || msg.contains("play")
    );

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn ffi_config_and_save_restore() {
    let engine = panda_engine_create(100);

    unsafe {
        let persistence = Box::new(panda_engine_core::test_utils::MockPersistence::new());
        (*engine)
            .engine
            .with_engine(|e| e.set_persistence(persistence));
    }

    let config = unsafe { panda_engine_get_config(engine) };
    assert!(config.auto_resume);

    let saved = unsafe { panda_engine_save(engine) };
    assert!(saved);

    let restored = unsafe { panda_engine_restore(engine) };
    assert!(restored);

    unsafe {
        panda_engine_destroy(engine);
    }
}
