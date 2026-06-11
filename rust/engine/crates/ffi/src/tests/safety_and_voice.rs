use super::super::*;
use std::ffi::{CStr, c_char};
use std::ptr;

#[test]
fn ffi_null_pointer_safety() {
    unsafe {
        let outcome = panda_engine_dispatch(ptr::null_mut(), FFI_COMMAND_BOOTSTRAP, ptr::null(), 0);
        assert_eq!(outcome, FfiEngineOutcome::invalid());

        assert_eq!(panda_engine_get_effects_count(ptr::null_mut()), 0);

        let mut types = vec![0i32; 10];
        panda_engine_get_effects_types(ptr::null_mut(), types.as_mut_ptr());
        assert!(types.iter().all(|&t| t == 0));

        let snapshot = panda_engine_snapshot(ptr::null_mut());
        assert!(!snapshot.can_dispatch);

        let config = panda_engine_get_config(ptr::null_mut());
        assert!(!config.auto_resume);

        panda_engine_tick(ptr::null_mut(), 100);
        panda_engine_destroy(ptr::null_mut());
    }
}

#[test]
fn ffi_voice_hypothesis_retrieval() {
    let engine = panda_engine_create(100);

    unsafe {
        let outcome = panda_engine_dispatch(engine, FFI_COMMAND_START_VOICE, ptr::null(), 0);
        assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);

        let audio: [c_char; 160] = [0; 160];
        let outcome = panda_engine_dispatch(engine, FFI_COMMAND_PROCESS_VOICE, audio.as_ptr(), 0);
        assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);

        let buf = [0u8; 256];
        assert_eq!(panda_engine_get_voice_hypothesis(engine), ptr::null_mut());

        let hypothesis = CStr::from_ptr(buf.as_ptr() as *const i8).to_string_lossy();
        assert_eq!(hypothesis.len(), 0);
    }

    unsafe {
        panda_engine_destroy(engine);
    }
}
