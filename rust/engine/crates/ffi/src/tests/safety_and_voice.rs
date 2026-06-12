use super::super::*;
use std::ffi::c_char;
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
fn ffi_voice_dispatch_path_is_rejected() {
    let engine = panda_engine_create(100);

    unsafe {
        let outcome = panda_engine_dispatch(engine, FFI_COMMAND_START_VOICE, ptr::null(), 0);
        assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);

        let audio: [c_char; 160] = [0; 160];
        let outcome = panda_engine_dispatch(engine, FFI_COMMAND_PROCESS_VOICE, audio.as_ptr(), 0);
        assert_eq!(outcome, FfiEngineOutcome::invalid());
    }

    unsafe {
        panda_engine_destroy(engine);
    }
}

#[test]
fn ffi_process_audio_raw_supports_pcm_buffers() {
    let engine = panda_engine_create(100);

    unsafe {
        let start = panda_engine_dispatch(engine, FFI_COMMAND_START_VOICE, ptr::null(), 150);
        assert_eq!(start.event_type, FFI_EVENT_COMMAND_APPLIED);

        let audio: [i16; 160] = [0; 160];
        let outcome = panda_engine_process_audio_raw(engine, audio.as_ptr(), audio.len(), 200);
        assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);
        assert_eq!(outcome.applied_command_type, FFI_COMMAND_PROCESS_VOICE);

        let invalid = panda_engine_process_audio_raw(engine, ptr::null(), 4, 250);
        assert_eq!(invalid, FfiEngineOutcome::invalid());

        panda_engine_destroy(engine);
    }
}
