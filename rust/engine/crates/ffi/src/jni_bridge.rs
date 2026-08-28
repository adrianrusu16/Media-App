use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject};
use jni::sys::{jboolean, jfloat, jint, jlong, jlongArray, jobjectArray, jstring};
use std::ptr;
use std::sync::Arc;

mod conversions;

use conversions::{
    account_to_strings, auth_result_array, auth_state_to_strings, backend_status_to_strings,
    effect_to_strings, effects_page_to_strings, history_page_to_strings, invalid_auth_input,
    jni_secret_to_string, jni_string_to_c_string, jni_string_to_string, metadata_to_strings,
    owned_c_string_to_jstring, profile_to_strings, session_to_strings, snapshot_page_to_strings,
    snapshot_to_jlong_array, strings_to_jobject_array,
};

use crate::api::auth::{
    login_password, logout, register_password, resend_verification, verify_email,
};
use crate::api::backend::configure_backend;
use crate::jni_audio_source_client::JniAudioSourceClient;
use crate::jni_session_cryptor::JniSessionCryptor;
use crate::{
    PandaEngine, panda_engine_create, panda_engine_destroy, panda_engine_dispatch,
    panda_engine_dispatch_platform_event, panda_engine_get_browse_result_album,
    panda_engine_get_browse_result_artist, panda_engine_get_browse_result_id,
    panda_engine_get_browse_result_item_type, panda_engine_get_browse_result_mime_type,
    panda_engine_get_browse_result_source_uri, panda_engine_get_browse_result_thumbnail_url,
    panda_engine_get_browse_result_title, panda_engine_get_current_album,
    panda_engine_get_current_artist, panda_engine_get_current_media_id,
    panda_engine_get_current_mime_type, panda_engine_get_current_source_uri,
    panda_engine_get_current_thumbnail_url, panda_engine_get_current_title,
    panda_engine_get_current_user_id, panda_engine_get_effect_media_id,
    panda_engine_get_effect_notify_message, panda_engine_get_effect_playback_instance_id,
    panda_engine_get_effect_position_millis, panda_engine_get_effect_speed,
    panda_engine_get_effect_type, panda_engine_get_effects_count,
    panda_engine_get_last_event_message, panda_engine_get_search_result_album,
    panda_engine_get_search_result_artist, panda_engine_get_search_result_id,
    panda_engine_get_search_result_item_type, panda_engine_get_search_result_mime_type,
    panda_engine_get_search_result_source_uri, panda_engine_get_search_result_thumbnail_url,
    panda_engine_get_search_result_title, panda_engine_init_logging, panda_engine_snapshot,
};
use panda_engine_core::networking::canopy::DeploymentMode;
use panda_engine_core::{
    BackendAvailability, BackendUnavailableReason, EncryptedFileSessionStore, SessionStore,
};
use std::path::PathBuf;

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
    panda_engine_init_logging(NATIVE_LOG_LEVEL_INFO);
    panda_engine_create(now_epoch_millis as u64) as jlong
}

const NATIVE_LOG_LEVEL_INFO: i32 = 3;

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeInstallSessionStore(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    session_path: JObject,
    cryptor: JObject,
) -> jboolean {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return false.into();
    };
    let Some(session_path) = jni_string_to_c_string(&mut env, session_path) else {
        return false.into();
    };
    let Ok(session_path) = session_path.to_str() else {
        return false.into();
    };
    let session_path = PathBuf::from(session_path);
    if !session_path.is_absolute() {
        return false.into();
    }
    let Ok(cryptor) = JniSessionCryptor::new(&mut env, cryptor) else {
        return false.into();
    };
    let store: Arc<dyn SessionStore> = Arc::new(EncryptedFileSessionStore::new(
        session_path,
        Arc::new(cryptor),
    ));
    engine.install_session_store(store).into()
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeConfigureBackend(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    config_json: JObject,
    development: jboolean,
) -> jboolean {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSetBackendAvailability(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    availability: jint,
    reason: jint,
) -> jboolean {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return false.into();
    };
    let availability = match availability {
        crate::FFI_BACKEND_CONNECTING => BackendAvailability::Connecting,
        crate::FFI_BACKEND_AVAILABLE => BackendAvailability::Available,
        crate::FFI_BACKEND_UNAVAILABLE => BackendAvailability::Unavailable(match reason {
            crate::FFI_BACKEND_REASON_NETWORK_UNAVAILABLE => {
                BackendUnavailableReason::NetworkUnavailable
            }
            crate::FFI_BACKEND_REASON_TIMEOUT => BackendUnavailableReason::Timeout,
            crate::FFI_BACKEND_REASON_SERVICE_UNAVAILABLE => {
                BackendUnavailableReason::ServiceUnavailable
            }
            _ => BackendUnavailableReason::ConnectionFailed,
        }),
        _ => return false.into(),
    };
    engine
        .engine
        .with_engine(move |inner| inner.set_backend_availability(availability));
    true.into()
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeMetadataValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, metadata_to_strings(&snapshot))
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeAuthStateValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, auth_state_to_strings(&snapshot.auth_state))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeProfileValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, profile_to_strings(snapshot.profile.as_ref()))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeProtectedAccountValues(
    mut env: JNIEnv,
    _: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(
        &mut env,
        account_to_strings(snapshot.protected_account.as_ref()),
    )
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeDeviceSessionValues(
    mut env: JNIEnv,
    _: JObject,
    handle: jlong,
    index: jint,
) -> jobjectArray {
    let Some(session) = (unsafe { (handle as *const PandaEngine).as_ref() })
        .map(|engine| engine.engine.snapshot())
        .and_then(|snapshot| {
            usize::try_from(index)
                .ok()
                .and_then(|index| snapshot.device_sessions.get(index).cloned())
        })
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, session_to_strings(&session))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeProfilePreferenceValue(
    mut env: JNIEnv,
    _: JObject,
    handle: jlong,
    key: JObject,
) -> jstring {
    let Some(key) = jni_string_to_c_string(&mut env, key) else {
        return ptr::null_mut();
    };
    let value = unsafe {
        crate::panda_engine_get_profile_preference_value(handle as *const PandaEngine, key.as_ptr())
    };
    owned_c_string_to_jstring(&mut env, value)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeHistoryPageValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    offset: jint,
    limit: jint,
    generation: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    let values = history_page_to_strings(&snapshot, offset, limit, generation);
    tracing::info!(
        offset,
        limit,
        requested_generation = generation,
        packed_len = values.len(),
        "engine.history.page"
    );
    strings_to_jobject_array(&mut env, values)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeSnapshotPageValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    kind: jint,
    offset: jint,
    limit: jint,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|engine| engine.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    let Some(values) = snapshot_page_to_strings(&snapshot, kind, offset, limit) else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, values)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativePlaylistSelectionValues(
    mut env: JNIEnv,
    _: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(snapshot) =
        (unsafe { (handle as *const PandaEngine).as_ref() }).map(|e| e.engine.snapshot())
    else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(
        &mut env,
        vec![
            snapshot.playlist_tracks_playlist_id.unwrap_or_default(),
            snapshot
                .playlist_reconciliation
                .as_ref()
                .map(|v| v.playlist_id.clone())
                .unwrap_or_default(),
            snapshot
                .playlist_reconciliation
                .as_ref()
                .map(|v| v.expected_revision.to_string())
                .unwrap_or_default(),
            snapshot
                .playlist_reconciliation
                .as_ref()
                .map(|v| v.server_revision.to_string())
                .unwrap_or_default(),
            snapshot
                .playlist_reconciliation
                .as_ref()
                .map(|v| v.server_membership_ids.join("\u{1f}"))
                .unwrap_or_default(),
            snapshot
                .playlist_reconciliation
                .as_ref()
                .map(|v| v.proposed_membership_ids.join("\u{1f}"))
                .unwrap_or_default(),
        ],
    )
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeRegisterPassword(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    email: JObject,
    password: JByteArray,
) -> jobjectArray {
    let Some(password) = jni_secret_to_string(&mut env, &password) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let Some(email) = jni_string_to_string(&mut env, email) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    auth_result_array(&mut env, register_password(engine, &email, &password))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeResendVerification(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    email: JObject,
) -> jobjectArray {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let Some(email) = jni_string_to_string(&mut env, email) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    auth_result_array(&mut env, resend_verification(engine, &email))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeVerifyEmail(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    verification_token: JByteArray,
    device_label: JObject,
) -> jobjectArray {
    let Some(verification_token) = jni_secret_to_string(&mut env, &verification_token) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let Some(device_label) = jni_string_to_string(&mut env, device_label) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    auth_result_array(
        &mut env,
        verify_email(engine, &verification_token, &device_label),
    )
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeLoginPassword(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    email: JObject,
    password: JByteArray,
    device_label: JObject,
) -> jobjectArray {
    let Some(password) = jni_secret_to_string(&mut env, &password) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let (Some(email), Some(device_label)) = (
        jni_string_to_string(&mut env, email),
        jni_string_to_string(&mut env, device_label),
    ) else {
        return auth_result_array(&mut env, invalid_auth_input());
    };
    auth_result_array(
        &mut env,
        login_password(engine, &email, &password, &device_label),
    )
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeLogout(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
) -> jobjectArray {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    auth_result_array(&mut env, logout(engine))
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jobjectArray {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let Ok(index) = usize::try_from(index) else {
        return ptr::null_mut();
    };
    let source_uri = engine.engine.snapshot().source_uri;
    let effects = engine.last_effects.lock().unwrap();
    let Some(effect) = effects.get(index) else {
        return ptr::null_mut();
    };
    strings_to_jobject_array(&mut env, effect_to_strings(effect, source_uri.as_deref()))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectPageValues(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    offset: jint,
    limit: jint,
) -> jobjectArray {
    let Some(engine) = (unsafe { (handle as *const PandaEngine).as_ref() }) else {
        return ptr::null_mut();
    };
    let source_uri = engine.engine.snapshot().source_uri;
    let effects = engine.last_effects.lock().unwrap();
    strings_to_jobject_array(
        &mut env,
        effects_page_to_strings(&effects, offset, limit, source_uri.as_deref()),
    )
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
pub unsafe extern "system" fn Java_com_adrianrusu_pandawave_core_rust_bridge_engine_native_PandaEngine_nativeEffectPlaybackInstanceId(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
    index: jint,
) -> jlong {
    unsafe {
        panda_engine_get_effect_playback_instance_id(handle as *const PandaEngine, index as usize)
    }
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
