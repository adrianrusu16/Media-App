use std::ffi::CString;
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jlong, jlongArray};

use crate::{
    FfiEngineSnapshot, PandaEngine, panda_engine_create, panda_engine_destroy,
    panda_engine_dispatch, panda_engine_dispatch_platform_event, panda_engine_snapshot,
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

fn jni_string_to_c_string(env: &mut JNIEnv, value: JObject) -> Option<CString> {
    if value.is_null() {
        return None;
    }

    let string = JString::from(value);
    let value = env.get_string(&string).ok()?;
    CString::new(value.to_string_lossy().as_bytes()).ok()
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

fn snapshot_to_jlong_values(snapshot: FfiEngineSnapshot) -> [jlong; 3] {
    [
        snapshot.playback_state as jlong,
        snapshot.restriction_state as jlong,
        snapshot.updated_at_epoch_millis as jlong,
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{FFI_PLAYBACK_PLAYING, FFI_RESTRICTION_UNKNOWN};

    #[test]
    fn snapshot_values_match_current_kotlin_compact_layout() {
        let snapshot = FfiEngineSnapshot {
            playback_state: FFI_PLAYBACK_PLAYING,
            restriction_state: FFI_RESTRICTION_UNKNOWN,
            updated_at_epoch_millis: 42,
            ..FfiEngineSnapshot::invalid()
        };

        assert_eq!(
            [
                FFI_PLAYBACK_PLAYING as jlong,
                FFI_RESTRICTION_UNKNOWN as jlong,
                42
            ],
            snapshot_to_jlong_values(snapshot)
        );
    }
}
