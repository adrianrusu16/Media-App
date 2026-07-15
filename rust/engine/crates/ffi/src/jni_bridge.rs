use std::ffi::{CStr, CString, c_char};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jlongArray, jobjectArray, jstring};
use std::sync::Arc;

use crate::api::backend::configure_backend;
use crate::jni_audio_source_client::JniAudioSourceClient;
use crate::{
    FfiEngineSnapshot, PandaEngine, panda_engine_create, panda_engine_destroy,
    panda_engine_dispatch, panda_engine_dispatch_platform_event, panda_engine_free_string,
    panda_engine_get_browse_result_album, panda_engine_get_browse_result_artist,
    panda_engine_get_browse_result_id, panda_engine_get_browse_result_item_type,
    panda_engine_get_browse_result_mime_type, panda_engine_get_browse_result_source_uri,
    panda_engine_get_browse_result_thumbnail_url, panda_engine_get_browse_result_title,
    panda_engine_get_current_album, panda_engine_get_current_artist,
    panda_engine_get_current_media_id, panda_engine_get_current_mime_type,
    panda_engine_get_current_source_uri, panda_engine_get_current_thumbnail_url,
    panda_engine_get_current_title, panda_engine_get_current_user_id,
    panda_engine_get_effect_media_id, panda_engine_get_effect_notify_message,
    panda_engine_get_effect_position_millis, panda_engine_get_effect_speed,
    panda_engine_get_effect_type, panda_engine_get_effects_count,
    panda_engine_get_last_event_message, panda_engine_get_search_result_album,
    panda_engine_get_search_result_artist, panda_engine_get_search_result_id,
    panda_engine_get_search_result_item_type, panda_engine_get_search_result_mime_type,
    panda_engine_get_search_result_source_uri, panda_engine_get_search_result_thumbnail_url,
    panda_engine_get_search_result_title, panda_engine_snapshot,
};
use panda_engine_core::networking::canopy::DeploymentMode;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
    now_epoch_millis: jlong,
) -> jlong {
    create_engine_handle(now_epoch_millis)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_00024Companion_nativeCreate(
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeConfigureBackend(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    config_json: JObject,
    development: jboolean,
) -> jboolean {
    let Some(engine) = (unsafe { (handle as *mut PandaEngine).as_mut() }) else {
        return false.into();
    };
    let Some(config_json) = jni_string_to_c_string(&mut env, config_json) else {
        return false.into();
    };
    let Ok(config_json) = config_json.to_str() else {
        return false.into();
    };
    let mode = if development != 0 {
        DeploymentMode::Development
    } else {
        DeploymentMode::Production
    };
    configure_backend(engine, config_json, mode).is_ok().into()
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSnapshot(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jlongArray {
    let snapshot = unsafe { panda_engine_snapshot(handle as *const PandaEngine) };
    snapshot_to_jlong_array(&mut env, snapshot)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeDispatch(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    command_type: jint,
    payload: JObject,
    now_epoch_millis: jlong,
) -> jlongArray {
    let payload = jni_string_to_c_string(&mut env, payload);
    let payload_ptr = payload.as_ref().map_or(ptr::null(), |value| value.as_ptr());
    let outcome = unsafe {
        panda_engine_dispatch(
            handle as *mut PandaEngine,
            command_type,
            payload_ptr,
            now_epoch_millis as u64,
        )
    };
    snapshot_to_jlong_array(&mut env, outcome.snapshot)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeDispatchPlatformEvent(
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeDestroy(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    unsafe { panda_engine_destroy(handle as *mut PandaEngine) };
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSetAudioSourceResolver(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    resolver: JObject,
) -> jboolean {
    let Some(engine) = (unsafe { (handle as *mut PandaEngine).as_mut() }) else {
        return false.into();
    };
    let Ok(client) = JniAudioSourceClient::new(&mut env, resolver) else {
        return false.into();
    };

    engine
        .engine
        .with_engine(|engine| engine.set_audio_source_client(Arc::new(client)));
    true.into()
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentMediaId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_media_id(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentTitle(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_title(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentArtist(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_artist(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentAlbum(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_album(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentArtworkUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_thumbnail_url(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentSourceUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_source_uri(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentMimeType(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_mime_type(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeCurrentUserId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_current_user_id(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeLastEventMessage(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jstring {
    let value = unsafe { panda_engine_get_last_event_message(handle as *const PandaEngine) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBackendStatusValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jobjectArray {
    let engine = unsafe { (handle as *const PandaEngine).as_ref() };
    let Some(status) = engine
        .map(|engine| engine.engine.snapshot())
        .and_then(|snapshot| snapshot.backend_status)
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, backend_status_to_strings(&status))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectCount(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jint {
    unsafe { panda_engine_get_effects_count(handle as *const PandaEngine) as jint }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectType(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jint {
    unsafe { panda_engine_get_effect_type(handle as *const PandaEngine, index as usize) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectMediaId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value =
        unsafe { panda_engine_get_effect_media_id(handle as *const PandaEngine, index as usize) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectNotifyMessage(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_effect_notify_message(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectPositionMillis(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jlong {
    unsafe { panda_engine_get_effect_position_millis(handle as *const PandaEngine, index as usize) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectSpeed(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jfloat {
    unsafe { panda_engine_get_effect_speed(handle as *const PandaEngine, index as usize) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value =
        unsafe { panda_engine_get_search_result_id(handle as *const PandaEngine, index as usize) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultTitle(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_title(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultArtist(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_artist(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultAlbum(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_album(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultArtworkUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_thumbnail_url(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultSourceUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_source_uri(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultMimeType(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_search_result_mime_type(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSearchResultItemType(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jint {
    unsafe {
        panda_engine_get_search_result_item_type(handle as *const PandaEngine, index as usize)
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultId(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value =
        unsafe { panda_engine_get_browse_result_id(handle as *const PandaEngine, index as usize) };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultTitle(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_title(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultArtist(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_artist(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultAlbum(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_album(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultArtworkUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_thumbnail_url(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultSourceUri(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_source_uri(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultMimeType(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jstring {
    let value = unsafe {
        panda_engine_get_browse_result_mime_type(handle as *const PandaEngine, index as usize)
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeBrowseResultItemType(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jint {
    unsafe {
        panda_engine_get_browse_result_item_type(handle as *const PandaEngine, index as usize)
    }
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

fn strings_to_jobject_array(env: &mut JNIEnv, values: Vec<String>) -> jobjectArray {
    let array = match env.new_object_array(values.len() as i32, "java/lang/String", JObject::null())
    {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };
    for (index, value) in values.into_iter().enumerate() {
        let value = match env.new_string(value) {
            Ok(value) => value,
            Err(_) => return ptr::null_mut(),
        };
        if env
            .set_object_array_element(&array, index as i32, value)
            .is_err()
        {
            return ptr::null_mut();
        }
    }
    array.into_raw()
}

fn backend_status_to_strings(status: &panda_engine_core::EngineBackendStatus) -> Vec<String> {
    let mut values = Vec::with_capacity(5 + status.dependencies.len() * 3);
    values.push(if status.healthy { "1" } else { "0" }.into());
    values.push(status.version.clone());
    values.push(status.status.as_wire().into());
    values.push(
        status
            .checked_at_epoch_millis
            .map(|value| value.to_string())
            .unwrap_or_default(),
    );
    values.push(status.dependencies.len().to_string());
    for dependency in &status.dependencies {
        values.push(dependency.name.clone());
        values.push(dependency.status.as_wire().into());
        values.push(dependency.message.clone());
    }
    values
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

fn snapshot_to_jlong_values(snapshot: FfiEngineSnapshot) -> [jlong; 35] {
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
        snapshot.duration_millis as jlong,
        snapshot.theme_preference as jlong,
        snapshot.preference_source as jlong,
        snapshot.preference_revision as jlong,
        bool_to_jlong(snapshot.preference_initialized),
        snapshot.driving_state as jlong,
        bool_to_jlong(snapshot.has_backend_status),
        bool_to_jlong(snapshot.backend_healthy),
        snapshot.backend_checked_at_epoch_millis as jlong,
        snapshot.backend_dependencies_count as jlong,
        snapshot.playback_expires_at_epoch_millis as jlong,
    ]
}

fn bool_to_jlong(value: bool) -> jlong {
    if value { 1 } else { 0 }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        FFI_DRIVING_PARKED, FFI_ERROR_NETWORK, FFI_PLAYBACK_PLAYING, FFI_RESTRICTION_UNKNOWN,
        FfiControlState, FfiPlayerControls,
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
            duration_millis: 222_000,
            playback_expires_at_epoch_millis: 1_750_000_000_250,
            theme_preference: 4,
            preference_source: 3,
            preference_revision: 8,
            preference_initialized: true,
            driving_state: FFI_DRIVING_PARKED,
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
            has_backend_status: true,
            backend_healthy: true,
            backend_checked_at_epoch_millis: 1_725_000_000_000,
            backend_dependencies_count: 2,
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
                222_000,
                4,
                3,
                8,
                1,
                FFI_DRIVING_PARKED as jlong,
                1,
                1,
                1_725_000_000_000,
                2,
                1_750_000_000_250,
            ],
            snapshot_to_jlong_values(snapshot)
        );
    }

    #[test]
    fn backend_status_values_are_emitted_from_one_domain_snapshot() {
        let status = panda_engine_core::EngineBackendStatus {
            healthy: true,
            version: "0.2.0".into(),
            status: panda_engine_core::EngineStatusValue::from_wire("ready"),
            dependencies: vec![panda_engine_core::EngineDependencyStatus {
                name: "catalog".into(),
                status: panda_engine_core::EngineStatusValue::from_wire("healthy"),
                message: "available".into(),
            }],
            checked_at_epoch_millis: Some(1_750_000_000_250),
        };

        assert_eq!(
            backend_status_to_strings(&status),
            vec![
                "1",
                "0.2.0",
                "ready",
                "1750000000250",
                "1",
                "catalog",
                "healthy",
                "available",
            ]
        );
    }
}
