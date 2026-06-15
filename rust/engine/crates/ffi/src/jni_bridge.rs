use std::ffi::{CStr, CString, c_char};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jlong, jlongArray, jstring};

use crate::{
    FfiEngineSnapshot, PandaEngine, panda_engine_create, panda_engine_destroy,
    panda_engine_dispatch, panda_engine_dispatch_platform_event, panda_engine_free_string,
    panda_engine_get_current_album, panda_engine_get_current_artist,
    panda_engine_get_current_duration_millis, panda_engine_get_current_media_id,
    panda_engine_get_current_thumbnail_url, panda_engine_get_current_title,
    panda_engine_get_current_user_id, panda_engine_snapshot,
};

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
    now_epoch_millis: jlong,
) -> jlong {
    create_engine_handle(now_epoch_millis)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_00024Companion_nativeCreate(
    _env: JNIEnv,
    _this: JObject,
    now_epoch_millis: jlong,
) -> jlong {
    create_engine_handle(now_epoch_millis)
}

fn create_engine_handle(now_epoch_millis: jlong) -> jlong {
    panda_engine_create(now_epoch_millis as u64) as jlong
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeSnapshot(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jlongArray {
    let snapshot = unsafe { panda_engine_snapshot(handle as *const PandaEngine) };
    snapshot_to_jlong_array(&mut env, snapshot)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeDispatch(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    command_type: jint,
    now_epoch_millis: jlong,
) -> jlongArray {
    let outcome = unsafe {
        panda_engine_dispatch(
            handle as *mut PandaEngine,
            command_type,
            ptr::null(),
            now_epoch_millis as u64,
        )
    };
    snapshot_to_jlong_array(&mut env, outcome.snapshot)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeDispatchPlatformEvent(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    event_type: jint,
    payload: JObject,
    now_epoch_millis: jlong,
) -> jlongArray {
    let payload = jni_string_to_c_string(&mut env, payload);
    let payload_ptr = payload.as_ref().map_or(ptr::null(), |value| value.as_ptr());
    let outcome = unsafe {
        panda_engine_dispatch_platform_event(
            handle as *mut PandaEngine,
            event_type,
            payload_ptr,
            now_epoch_millis as u64,
        )
    };
    snapshot_to_jlong_array(&mut env, outcome.snapshot)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeDestroy(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    unsafe { panda_engine_destroy(handle as *mut PandaEngine) };
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentMediaId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_media_id(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentTitle(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_title(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentArtist(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_artist(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentAlbum(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_album(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentDurationMillis(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jlong {
    unsafe { panda_engine_get_current_duration_millis(handle as *const PandaEngine) as jlong }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentArtworkUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_thumbnail_url(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_mediaapp_core_rust_bridge_engine_native_PandaEngine_nativeCurrentUserId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_user_id(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

fn jni_string_to_c_string(env: &mut JNIEnv, value: JObject) -> Option<CString> {
    if value.is_null() {
        return None;
    }

    let string = JString::from(value);
    let value = env.get_string(&string).ok()?;
    CString::new(value.to_string_lossy().as_bytes()).ok()
}

fn owned_c_string_to_jstring(env: &mut JNIEnv, value: *const c_char) -> jstring {
    if value.is_null() {
        return ptr::null_mut();
    }

    let raw_value = value;
    let value = unsafe { CStr::from_ptr(raw_value) }
        .to_string_lossy()
        .into_owned();
    unsafe { panda_engine_free_string(raw_value as *mut c_char) };

    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn snapshot_to_jlong_array(env: &mut JNIEnv, snapshot: FfiEngineSnapshot) -> jlongArray {
    let values = snapshot_to_jlong_values(snapshot);
    let array = match env.new_long_array(values.len() as i32) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };

    if env.set_long_array_region(&array, 0, &values).is_err() {
        return ptr::null_mut();
    }

    array.into_raw()
}

fn snapshot_to_jlong_values(snapshot: FfiEngineSnapshot) -> [jlong; 24] {
    [
        snapshot.playback_state as jlong,
        snapshot.restriction_state as jlong,
        snapshot.updated_at_epoch_millis as jlong,
        bool_to_jlong(snapshot.has_active_session),
        bool_to_jlong(snapshot.has_error),
        snapshot.error_type as jlong,
        snapshot.search_results_count as jlong,
        snapshot.playback_speed.to_bits() as jlong,
        snapshot.position_millis as jlong,
        bool_to_jlong(snapshot.is_busy),
        bool_to_jlong(snapshot.can_dispatch),
        bool_to_jlong(snapshot.controls.play_pause.is_visible),
        bool_to_jlong(snapshot.controls.play_pause.is_enabled),
        bool_to_jlong(snapshot.controls.play_pause.is_active),
        bool_to_jlong(snapshot.controls.skip_next.is_visible),
        bool_to_jlong(snapshot.controls.skip_next.is_enabled),
        bool_to_jlong(snapshot.controls.skip_next.is_active),
        bool_to_jlong(snapshot.controls.skip_prev.is_visible),
        bool_to_jlong(snapshot.controls.skip_prev.is_enabled),
        bool_to_jlong(snapshot.controls.skip_prev.is_active),
        bool_to_jlong(snapshot.controls.show_play_icon),
        bool_to_jlong(snapshot.has_voice_hypothesis),
        snapshot.browse_results_count as jlong,
        snapshot.metadata_revision as jlong,
    ]
}

fn bool_to_jlong(value: bool) -> jlong {
    if value { 1 } else { 0 }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        FFI_ERROR_NETWORK, FFI_PLAYBACK_PLAYING, FFI_RESTRICTION_UNKNOWN, FfiControlState,
        FfiPlayerControls,
    };

    #[test]
    fn snapshot_values_match_kotlin_compact_layout() {
        let snapshot = FfiEngineSnapshot {
            playback_state: FFI_PLAYBACK_PLAYING,
            restriction_state: FFI_RESTRICTION_UNKNOWN,
            updated_at_epoch_millis: 42,
            has_active_session: true,
            has_error: true,
            error_type: FFI_ERROR_NETWORK,
            metadata_revision: 7,
            search_results_count: 3,
            playback_speed: 1.25,
            position_millis: 9_000,
            is_busy: true,
            can_dispatch: false,
            controls: FfiPlayerControls {
                play_pause: FfiControlState {
                    is_visible: true,
                    is_enabled: true,
                    is_active: true,
                },
                skip_next: FfiControlState {
                    is_visible: true,
                    is_enabled: false,
                    is_active: false,
                },
                skip_prev: FfiControlState {
                    is_visible: false,
                    is_enabled: true,
                    is_active: false,
                },
                show_play_icon: false,
            },
            has_voice_hypothesis: true,
            browse_results_count: 5,
            ..FfiEngineSnapshot::invalid()
        };

        assert_eq!(
            [
                FFI_PLAYBACK_PLAYING as jlong,
                FFI_RESTRICTION_UNKNOWN as jlong,
                42,
                1,
                1,
                FFI_ERROR_NETWORK as jlong,
                3,
                1.25_f32.to_bits() as jlong,
                9_000,
                1,
                0,
                1,
                1,
                1,
                1,
                0,
                0,
                0,
                1,
                0,
                0,
                1,
                5,
                7,
            ],
            snapshot_to_jlong_values(snapshot)
        );
    }
}
