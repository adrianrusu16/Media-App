use std::ffi::{CStr, CString, c_char};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jlong, jlongArray, jobjectArray, jstring};

use crate::api::auth::AuthOperationResult;
use crate::mappings::{effect_to_ffi, media_item_type_to_ffi};
use crate::{FfiEngineSnapshot, panda_engine_free_string};

pub(super) const PAGE_KIND_BROWSE: i32 = 0;
pub(super) const PAGE_KIND_SEARCH: i32 = 1;
pub(super) const PAGE_KIND_DISCOVERY: i32 = 2;
pub(super) const PAGE_KIND_FOR_YOU: i32 = 3;
pub(super) const PAGE_KIND_RECOMMENDATIONS: i32 = 4;
pub(super) const PAGE_KIND_SAVED: i32 = 5;
pub(super) const PAGE_KIND_LIKED: i32 = 6;
pub(super) const PAGE_KIND_PLAYLISTS: i32 = 7;
pub(super) const PAGE_KIND_PLAYLIST_TRACKS: i32 = 8;
pub(super) const PAGE_KIND_PENDING_IDS: i32 = 9;
pub(super) const PAGE_KIND_DEVICE_SESSIONS: i32 = 10;

fn artwork_identity_strings(
    artwork: Option<&panda_engine_core::EngineArtwork>,
) -> (String, String, String) {
    match artwork {
        Some(artwork) => (
            artwork.id.clone(),
            artwork.content_hash.clone(),
            artwork.uri.clone().unwrap_or_default(),
        ),
        None => (String::new(), String::new(), String::new()),
    }
}

pub(super) fn jni_string_to_c_string(env: &mut JNIEnv, value: JObject) -> Option<CString> {
    if value.is_null() {
        return None;
    }

    let string = JString::from(value);
    let value = env.get_string(&string).ok()?;
    CString::new(value.to_string_lossy().as_bytes()).ok()
}

pub(super) fn jni_string_to_string(env: &mut JNIEnv, value: JObject) -> Option<String> {
    if value.is_null() {
        return None;
    }
    let string = JString::from(value);
    env.get_string(&string)
        .ok()
        .map(|value| value.to_string_lossy().into_owned())
}

pub(super) fn jni_secret_to_string(env: &mut JNIEnv, value: &JByteArray) -> Option<SecretString> {
    let mut bytes = env.convert_byte_array(value).ok()?;
    let zeros = vec![0_i8; bytes.len()];
    let cleared = env.set_byte_array_region(value, 0, &zeros).is_ok();
    if !cleared {
        bytes.fill(0);
        return None;
    }
    match String::from_utf8(bytes) {
        Ok(secret) => Some(SecretString(secret)),
        Err(error) => {
            let mut invalid_bytes = error.into_bytes();
            invalid_bytes.fill(0);
            None
        }
    }
}

pub(super) struct SecretString(String);

impl std::ops::Deref for SecretString {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl Drop for SecretString {
    fn drop(&mut self) {
        // SAFETY: bytes are overwritten without changing length or violating
        // the allocation; the string is never read after this drop begins.
        unsafe { self.0.as_bytes_mut().fill(0) };
    }
}

pub(super) fn invalid_auth_input() -> AuthOperationResult {
    AuthOperationResult::Error(panda_engine_core::EngineError::new(
        panda_engine_core::EngineErrorType::InvalidInput,
        "invalid authentication input",
        false,
    ))
}

pub(super) fn auth_result_array(env: &mut JNIEnv, result: AuthOperationResult) -> jobjectArray {
    strings_to_jobject_array(env, result.to_strings().into())
}

pub(super) fn owned_c_string_to_jstring(env: &mut JNIEnv, value: *const c_char) -> jstring {
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

pub(super) fn strings_to_jobject_array(env: &mut JNIEnv, values: Vec<String>) -> jobjectArray {
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

pub(super) fn library_track_to_strings(
    item: &panda_engine_core::EngineLibraryTrack,
) -> Vec<String> {
    let (artwork_id, artwork_hash, artwork_uri) = artwork_identity_strings(item.track.artwork.as_ref());
    let packed = vec![
        item.relationship_id.clone(),
        item.track.id.clone(),
        item.track.title.clone(),
        item.track.artist.id.clone(),
        item.track.artist.name.clone(),
        item.track
            .album
            .as_ref()
            .map(|album| album.title.clone())
            .unwrap_or_default(),
        item.track.duration_millis.to_string(),
        if item.track.explicit { "1" } else { "0" }.into(),
        artwork_uri,
        item.relationship_at_epoch_millis.to_string(),
        artwork_id,
        artwork_hash,
    ];
    debug_assert_eq!(packed.len(), LIBRARY_TRACK_VALUE_COUNT);
    packed
}

pub(super) const HISTORY_ENTRY_VALUE_COUNT: usize = 12;
pub(super) const CATALOG_ITEM_VALUE_COUNT: usize = 10;
pub(super) const LIBRARY_TRACK_VALUE_COUNT: usize = 12;
pub(super) const PLAYLIST_TRACK_VALUE_COUNT: usize = 14;
pub(super) const METADATA_VALUE_COUNT: usize = 10;
pub(super) const DEVICE_SESSION_VALUE_COUNT: usize = 6;
pub(super) const MAX_ENGINE_PAGE_QUERY_SIZE: usize = 50;

pub(super) fn page_range(offset: i32, limit: i32, len: usize) -> std::ops::Range<usize> {
    let start = offset.max(0) as usize;
    let count = (limit.max(0) as usize).min(MAX_ENGINE_PAGE_QUERY_SIZE);
    let end = start.saturating_add(count).min(len);
    if start >= end { 0..0 } else { start..end }
}

pub(super) fn pack_page<T>(
    items: &[T],
    offset: i32,
    limit: i32,
    encode: impl Fn(&T) -> Vec<String>,
) -> Vec<String> {
    let range = page_range(offset, limit, items.len());
    let mut values = Vec::new();
    for item in &items[range] {
        values.extend(encode(item));
    }
    values
}

pub(super) fn catalog_item_to_strings(item: &panda_engine_core::MediaItem) -> Vec<String> {
    let packed = vec![
        item.id.clone(),
        item.title.clone(),
        item.artist.clone(),
        item.album.clone().unwrap_or_default(),
        item.thumbnail_url.clone().unwrap_or_default(),
        item.source_uri.clone().unwrap_or_default(),
        item.mime_type.clone().unwrap_or_default(),
        media_item_type_to_ffi(&item.item_type).to_string(),
        item.artwork_id.clone().unwrap_or_default(),
        item.artwork_content_hash.clone().unwrap_or_default(),
    ];
    debug_assert_eq!(packed.len(), CATALOG_ITEM_VALUE_COUNT);
    packed
}

pub(super) fn playlist_to_strings(item: &panda_engine_core::EnginePlaylist) -> Vec<String> {
    vec![
        item.id.clone(),
        item.name.clone(),
        item.description.clone().unwrap_or_default(),
        item.revision.to_string(),
        item.created_at_epoch_millis.to_string(),
        item.updated_at_epoch_millis.to_string(),
        if item.description.is_some() { "1" } else { "0" }.into(),
    ]
}

pub(super) fn playlist_track_to_strings(
    item: &panda_engine_core::EnginePlaylistTrack,
) -> Vec<String> {
    let (artwork_id, artwork_hash, artwork_uri) = artwork_identity_strings(item.track.artwork.as_ref());
    let packed = vec![
        item.membership_id.clone(),
        item.playlist_id.clone(),
        item.track.id.clone(),
        item.track.title.clone(),
        item.track.artist.id.clone(),
        item.track.artist.name.clone(),
        item.track
            .album
            .as_ref()
            .map(|album| album.title.clone())
            .unwrap_or_default(),
        item.track.duration_millis.to_string(),
        (item.track.explicit as u8).to_string(),
        artwork_uri,
        item.position.to_string(),
        item.added_at_epoch_millis.to_string(),
        artwork_id,
        artwork_hash,
    ];
    debug_assert_eq!(packed.len(), PLAYLIST_TRACK_VALUE_COUNT);
    packed
}

pub(super) fn snapshot_page_to_strings(
    snapshot: &panda_engine_core::EngineSnapshot,
    kind: i32,
    offset: i32,
    limit: i32,
) -> Option<Vec<String>> {
    Some(match kind {
        PAGE_KIND_BROWSE => pack_page(
            &snapshot.browse_results,
            offset,
            limit,
            catalog_item_to_strings,
        ),
        PAGE_KIND_SEARCH => pack_page(
            &snapshot.search_results,
            offset,
            limit,
            catalog_item_to_strings,
        ),
        PAGE_KIND_DISCOVERY => pack_page(
            &snapshot.discovery_results,
            offset,
            limit,
            catalog_item_to_strings,
        ),
        PAGE_KIND_FOR_YOU => pack_page(
            &snapshot.for_you_results,
            offset,
            limit,
            catalog_item_to_strings,
        ),
        PAGE_KIND_RECOMMENDATIONS => pack_page(
            &snapshot.recommendations_results,
            offset,
            limit,
            catalog_item_to_strings,
        ),
        PAGE_KIND_SAVED => pack_page(
            &snapshot.saved_tracks,
            offset,
            limit,
            library_track_to_strings,
        ),
        PAGE_KIND_LIKED => pack_page(
            &snapshot.liked_tracks,
            offset,
            limit,
            library_track_to_strings,
        ),
        PAGE_KIND_PLAYLISTS => pack_page(&snapshot.playlists, offset, limit, playlist_to_strings),
        PAGE_KIND_PLAYLIST_TRACKS => pack_page(
            &snapshot.playlist_tracks,
            offset,
            limit,
            playlist_track_to_strings,
        ),
        PAGE_KIND_PENDING_IDS => pack_page(
            &snapshot.library_pending_track_ids,
            offset,
            limit,
            |track_id| vec![track_id.clone()],
        ),
        PAGE_KIND_DEVICE_SESSIONS => {
            pack_page(&snapshot.device_sessions, offset, limit, session_to_strings)
        }
        _ => return None,
    })
}

pub(super) fn effects_page_to_strings(
    effects: &[panda_engine_core::EngineEffect],
    offset: i32,
    limit: i32,
) -> Vec<String> {
    pack_page(effects, offset, limit, effect_to_strings)
}

pub(super) fn history_entry_to_strings(
    item: &panda_engine_core::EngineHistoryEntry,
) -> Vec<String> {
    let track = item.track.as_ref();
    let (artwork_id, artwork_hash, artwork_uri) =
        artwork_identity_strings(track.and_then(|track| track.artwork.as_ref()));
    vec![
        item.id.clone(),
        track.map(|track| track.id.clone()).unwrap_or_default(),
        track
            .map(|track| track.title.clone())
            .unwrap_or_else(|| "Unavailable track".into()),
        track
            .map(|track| track.artist.name.clone())
            .unwrap_or_default(),
        track
            .and_then(|track| track.album.as_ref())
            .map(|album| album.title.clone())
            .unwrap_or_default(),
        artwork_uri,
        item.played_at_epoch_millis
            .map(|value| value.to_string())
            .unwrap_or_default(),
        item.duration_millis.to_string(),
        item.completion_ratio.to_string(),
        if track.is_some() { "1" } else { "0" }.into(),
        artwork_id,
        artwork_hash,
    ]
}

/// Packs `[generation, ...entry fields]` for one bounded history page.
/// Generation mismatch and empty slices return only the current generation.
pub(super) fn history_page_to_strings(
    snapshot: &panda_engine_core::EngineSnapshot,
    offset: i32,
    limit: i32,
    requested_generation: i64,
) -> Vec<String> {
    let current_generation = snapshot.history_state.generation as i64;
    let mut values = vec![current_generation.to_string()];
    if current_generation != requested_generation {
        return values;
    }
    values.extend(pack_page(
        &snapshot.history_entries,
        offset,
        limit,
        |item| {
            let packed = history_entry_to_strings(item);
            debug_assert_eq!(packed.len(), HISTORY_ENTRY_VALUE_COUNT);
            packed
        },
    ));
    values
}

pub(super) fn metadata_to_strings(snapshot: &panda_engine_core::EngineSnapshot) -> Vec<String> {
    let packed = vec![
        snapshot.media_id.clone().unwrap_or_default(),
        snapshot.title.clone().unwrap_or_default(),
        snapshot.artist.clone().unwrap_or_default(),
        snapshot.album.clone().unwrap_or_default(),
        snapshot.thumbnail_url.clone().unwrap_or_default(),
        snapshot.source_uri.clone().unwrap_or_default(),
        snapshot.mime_type.clone().unwrap_or_default(),
        snapshot
            .session
            .as_ref()
            .map(|session| session.user_id.clone())
            .unwrap_or_default(),
        snapshot.artwork_id.clone().unwrap_or_default(),
        snapshot.artwork_content_hash.clone().unwrap_or_default(),
    ];
    debug_assert_eq!(packed.len(), METADATA_VALUE_COUNT);
    packed
}

pub(super) fn effect_to_strings(effect: &panda_engine_core::EngineEffect) -> Vec<String> {
    let media_id = match effect {
        panda_engine_core::EngineEffect::PreparePlaybackSource { media_id, .. }
        | panda_engine_core::EngineEffect::RecreatePlayerAndLoad { media_id, .. }
        | panda_engine_core::EngineEffect::UpdateMetadata { media_id, .. } => media_id.as_str(),
        _ => "",
    };
    let message = match effect {
        panda_engine_core::EngineEffect::NotifyUser { message } => message.as_str(),
        _ => "",
    };
    let position_millis = match effect {
        panda_engine_core::EngineEffect::Seek(position_millis)
        | panda_engine_core::EngineEffect::PreparePlaybackSource {
            position_millis, ..
        }
        | panda_engine_core::EngineEffect::RecreatePlayerAndLoad {
            position_millis, ..
        } => position_millis.to_string(),
        _ => "-1".into(),
    };
    let speed = match effect {
        panda_engine_core::EngineEffect::SetSpeed(speed) => speed.to_string(),
        _ => "NaN".into(),
    };
    let playback_instance_id = match effect {
        panda_engine_core::EngineEffect::PreparePlaybackSource {
            playback_instance_id,
            ..
        }
        | panda_engine_core::EngineEffect::RecreatePlayerAndLoad {
            playback_instance_id,
            ..
        } => playback_instance_id.to_string(),
        _ => "-1".into(),
    };

    vec![
        effect_to_ffi(effect).to_string(),
        media_id.into(),
        message.into(),
        position_millis,
        speed,
        playback_instance_id,
    ]
}

pub(super) fn backend_status_to_strings(
    status: &panda_engine_core::EngineBackendStatus,
) -> Vec<String> {
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

pub(super) fn profile_to_strings(
    profile: Option<&panda_engine_core::EngineProfile>,
) -> Vec<String> {
    let Some(profile) = profile else {
        return Vec::new();
    };
    vec![
        profile.id.clone(),
        profile.external_user_id.clone(),
        if profile.display_name.is_some() {
            "1"
        } else {
            "0"
        }
        .into(),
        profile.display_name.clone().unwrap_or_default(),
        profile
            .created_at_epoch_millis
            .map(|value| value.to_string())
            .unwrap_or_default(),
        profile
            .updated_at_epoch_millis
            .map(|value| value.to_string())
            .unwrap_or_default(),
    ]
}

pub(super) fn auth_state_to_strings(state: &panda_engine_core::AuthState) -> Vec<String> {
    match state {
        panda_engine_core::AuthState::Anonymous => vec!["anonymous".into()],
        panda_engine_core::AuthState::LoginRequired => vec!["login_required".into()],
        panda_engine_core::AuthState::Authenticated { account, session } => vec![
            "authenticated".into(),
            account.id.clone(),
            account.primary_email.clone(),
            account.status.clone(),
            account.created_at_epoch_millis.to_string(),
            session.id.clone(),
            session.device_label.clone(),
            session.created_at_epoch_millis.to_string(),
            session.last_used_at_epoch_millis.to_string(),
            session.expires_at_epoch_millis.to_string(),
            if session.current { "1" } else { "0" }.into(),
        ],
    }
}

pub(super) fn account_to_strings(account: Option<&panda_engine_core::Account>) -> Vec<String> {
    account
        .map(|account| {
            vec![
                account.id.clone(),
                account.primary_email.clone(),
                account.status.clone(),
                account.created_at_epoch_millis.to_string(),
            ]
        })
        .unwrap_or_default()
}

pub(super) fn session_to_strings(session: &panda_engine_core::AuthSession) -> Vec<String> {
    let packed = vec![
        session.id.clone(),
        session.device_label.clone(),
        session.created_at_epoch_millis.to_string(),
        session.last_used_at_epoch_millis.to_string(),
        session.expires_at_epoch_millis.to_string(),
        if session.current { "1" } else { "0" }.into(),
    ];
    debug_assert_eq!(packed.len(), DEVICE_SESSION_VALUE_COUNT);
    packed
}

pub(super) fn snapshot_to_jlong_array(env: &mut JNIEnv, snapshot: FfiEngineSnapshot) -> jlongArray {
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

fn snapshot_to_jlong_values(snapshot: FfiEngineSnapshot) -> [jlong; 62] {
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
        snapshot.auth_state as jlong,
        bool_to_jlong(snapshot.has_history_settings),
        bool_to_jlong(snapshot.history_enabled),
        snapshot.history_deleted_count as jlong,
        snapshot.history_entries_count as jlong,
        snapshot.saved_tracks_count as jlong,
        snapshot.liked_tracks_count as jlong,
        snapshot.library_pending_count as jlong,
        bool_to_jlong(snapshot.has_saved_tracks_next_page),
        bool_to_jlong(snapshot.has_liked_tracks_next_page),
        snapshot.playlists_count as jlong,
        snapshot.playlist_tracks_count as jlong,
        bool_to_jlong(snapshot.has_playlists_next_page),
        bool_to_jlong(snapshot.has_playlist_tracks_next_page),
        bool_to_jlong(snapshot.has_playlist_reconciliation),
        bool_to_jlong(snapshot.has_protected_account),
        snapshot.device_sessions_count as jlong,
        bool_to_jlong(snapshot.has_device_sessions_next_page),
        snapshot.discovery_results_count as jlong,
        bool_to_jlong(snapshot.has_discovery_next_page),
        bool_to_jlong(snapshot.has_history_next_page),
        snapshot.for_you_results_count as jlong,
        snapshot.recommendations_results_count as jlong,
        snapshot.backend_availability as jlong,
        snapshot.backend_unavailable_reason as jlong,
        snapshot.history_generation as jlong,
        snapshot.last_progress_tick_epoch_millis as jlong,
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
    use panda_engine_core::{Account, AuthSession, AuthState};

    #[test]
    fn authenticated_state_projects_only_sanitized_account_and_session_fields() {
        let state = AuthState::Authenticated {
            account: Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 10,
            },
            session: AuthSession {
                id: "session-1".into(),
                device_label: "emulator".into(),
                created_at_epoch_millis: 20,
                last_used_at_epoch_millis: 30,
                expires_at_epoch_millis: 40,
                current: true,
            },
        };

        assert_eq!(
            auth_state_to_strings(&state),
            vec![
                "authenticated",
                "account-1",
                "driver@example.com",
                "active",
                "10",
                "session-1",
                "emulator",
                "20",
                "30",
                "40",
                "1",
            ]
        );
    }

    #[test]
    fn snapshot_values_match_kotlin_compact_layout() {
        let snapshot = FfiEngineSnapshot {
            auth_state: crate::FFI_AUTH_AUTHENTICATED,
            has_history_settings: true,
            history_enabled: true,
            history_deleted_count: 7,
            history_entries_count: 2,
            saved_tracks_count: 3,
            liked_tracks_count: 4,
            library_pending_count: 1,
            has_saved_tracks_next_page: true,
            has_liked_tracks_next_page: false,
            playlists_count: 6,
            playlist_tracks_count: 7,
            has_playlists_next_page: true,
            has_playlist_tracks_next_page: false,
            has_playlist_reconciliation: true,
            has_protected_account: true,
            device_sessions_count: 2,
            has_device_sessions_next_page: true,
            discovery_results_count: 6,
            has_discovery_next_page: true,
            has_history_next_page: true,
            for_you_results_count: 8,
            recommendations_results_count: 9,
            backend_availability: crate::FFI_BACKEND_UNAVAILABLE,
            backend_unavailable_reason: crate::FFI_BACKEND_REASON_TIMEOUT,
            history_generation: 11,
            last_progress_tick_epoch_millis: 77,
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

        let values = snapshot_to_jlong_values(snapshot);
        assert_eq!(values.len(), 62);
        assert_eq!(values[61], 77);
        assert_eq!(
            &values[..45],
            &[
                1,
                0,
                42,
                1,
                1,
                2,
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
                crate::FFI_AUTH_AUTHENTICATED as jlong,
                1,
                1,
                7,
                2,
                3,
                4,
                1,
                1,
                0
            ]
        );
        assert_eq!(&values[45..50], &[6, 7, 1, 0, 1]);
        assert_eq!(&values[50..], &[1, 2, 1, 6, 1, 1, 8, 9, 2, 3, 11, 77]);
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
    #[test]
    fn packing_appends_artwork_identity_without_reordering_uri_slots() {
        let catalog = panda_engine_core::MediaItem {
            id: "track-1".into(),
            title: "Song".into(),
            artist: "Artist".into(),
            album: Some("Album".into()),
            thumbnail_url: Some("https://example.com/artwork/art-1/hash-1".into()),
            artwork_id: Some("art-1".into()),
            artwork_content_hash: Some("hash-1".into()),
            source_uri: Some("canopy://track-1".into()),
            mime_type: Some("audio/mpeg".into()),
            ..Default::default()
        };
        let catalog_packed = catalog_item_to_strings(&catalog);
        assert_eq!(catalog_packed.len(), CATALOG_ITEM_VALUE_COUNT);
        assert_eq!(catalog_packed[4], "https://example.com/artwork/art-1/hash-1");
        assert_eq!(catalog_packed[8], "art-1");
        assert_eq!(catalog_packed[9], "hash-1");

        let history = history_entry("history-1", Some("track-1"));
        let history_packed = history_entry_to_strings(&history);
        assert_eq!(history_packed.len(), HISTORY_ENTRY_VALUE_COUNT);
        assert_eq!(history_packed[5], "https://example.com/artwork/art-1/hash-1");
        assert_eq!(history_packed[10], "art-1");
        assert_eq!(history_packed[11], "hash-1");

        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.media_id = Some("track-1".into());
        snapshot.title = Some("Song".into());
        snapshot.artist = Some("Artist".into());
        snapshot.album = Some("Album".into());
        snapshot.thumbnail_url = Some("https://example.com/artwork/art-1/hash-1".into());
        snapshot.artwork_id = Some("art-1".into());
        snapshot.artwork_content_hash = Some("hash-1".into());
        snapshot.source_uri = Some("canopy://track-1".into());
        snapshot.mime_type = Some("audio/mpeg".into());
        let metadata = metadata_to_strings(&snapshot);
        assert_eq!(metadata.len(), METADATA_VALUE_COUNT);
        assert_eq!(metadata[4], "https://example.com/artwork/art-1/hash-1");
        assert_eq!(metadata[8], "art-1");
        assert_eq!(metadata[9], "hash-1");
    }

    #[test]
    fn profile_values_preserve_absent_display_name_distinct_from_empty_text() {
        let base = panda_engine_core::EngineProfile {
            id: "profile-1".into(),
            external_user_id: "account-1".into(),
            display_name: None,
            created_at_epoch_millis: Some(100),
            updated_at_epoch_millis: None,
        };
        let empty = panda_engine_core::EngineProfile {
            display_name: Some(String::new()),
            ..base.clone()
        };

        assert_eq!(
            profile_to_strings(Some(&base)),
            vec!["profile-1", "account-1", "0", "", "100", ""]
        );
        assert_eq!(
            profile_to_strings(Some(&empty)),
            vec!["profile-1", "account-1", "1", "", "100", ""]
        );
        assert!(profile_to_strings(None).is_empty());
    }

    #[test]
    fn history_page_packs_generation_and_bounded_entries_in_one_payload() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.history_state.generation = 4;
        snapshot.history_entries = vec![
            history_entry("history-1", Some("track-1")),
            history_entry("history-2", Some("track-2")),
            history_entry("history-3", None),
        ];

        let packed = history_page_to_strings(&snapshot, 1, 2, 4);
        assert_eq!(packed[0], "4");
        assert_eq!(packed.len(), 1 + 2 * HISTORY_ENTRY_VALUE_COUNT);
        assert_eq!(
            &packed[1..1 + HISTORY_ENTRY_VALUE_COUNT],
            history_entry_to_strings(&snapshot.history_entries[1])
        );
        assert_eq!(
            &packed[1 + HISTORY_ENTRY_VALUE_COUNT..],
            history_entry_to_strings(&snapshot.history_entries[2])
        );
    }

    #[test]
    fn history_page_mismatch_returns_current_generation_without_items() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.history_state.generation = 9;
        snapshot.history_entries = vec![history_entry("history-1", Some("track-1"))];

        assert_eq!(
            history_page_to_strings(&snapshot, 0, 50, 8),
            vec!["9".to_string()]
        );
    }

    #[test]
    fn history_page_limit_zero_or_past_end_is_generation_only() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.history_state.generation = 2;
        snapshot.history_entries = vec![history_entry("history-1", Some("track-1"))];

        assert_eq!(
            history_page_to_strings(&snapshot, 0, 0, 2),
            vec!["2".to_string()]
        );
        assert_eq!(
            history_page_to_strings(&snapshot, 5, 10, 2),
            vec!["2".to_string()]
        );
    }

    #[test]
    fn history_page_caps_at_max_query_size() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.history_state.generation = 1;
        snapshot.history_entries = (0..60)
            .map(|index| history_entry(&format!("history-{index}"), Some("track-1")))
            .collect();

        let packed = history_page_to_strings(&snapshot, 0, 100, 1);
        assert_eq!(
            (packed.len() - 1) / HISTORY_ENTRY_VALUE_COUNT,
            MAX_ENGINE_PAGE_QUERY_SIZE
        );
    }

    #[test]
    fn snapshot_page_packs_catalog_and_caps_query_size() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.browse_results = (0..60)
            .map(|index| catalog_item(&format!("track-{index}")))
            .collect();
        snapshot.search_results = vec![catalog_item("search-1"), catalog_item("search-2")];

        let browse = snapshot_page_to_strings(&snapshot, PAGE_KIND_BROWSE, 0, 100).expect("browse");
        assert_eq!(
            browse.len() / CATALOG_ITEM_VALUE_COUNT,
            MAX_ENGINE_PAGE_QUERY_SIZE
        );
        assert_eq!(
            &browse[..CATALOG_ITEM_VALUE_COUNT],
            catalog_item_to_strings(&snapshot.browse_results[0])
        );

        let search = snapshot_page_to_strings(&snapshot, PAGE_KIND_SEARCH, 1, 1).expect("search");
        assert_eq!(search, catalog_item_to_strings(&snapshot.search_results[1]));
        assert!(snapshot_page_to_strings(&snapshot, 99, 0, 10).is_none());
        assert!(
            snapshot_page_to_strings(&snapshot, PAGE_KIND_BROWSE, 80, 10)
                .expect("empty")
                .is_empty()
        );
    }

    #[test]
    fn snapshot_page_packs_library_pending_and_sessions() {
        let mut snapshot = panda_engine_core::EngineSnapshot::idle(1);
        snapshot.library_pending_track_ids =
            vec!["pending-1".into(), "pending-2".into(), "pending-3".into()];
        snapshot.device_sessions = vec![device_session("session-1"), device_session("session-2")];

        let pending =
            snapshot_page_to_strings(&snapshot, PAGE_KIND_PENDING_IDS, 1, 2).expect("pending");
        assert_eq!(
            pending,
            vec!["pending-2".to_string(), "pending-3".to_string()]
        );

        let sessions = snapshot_page_to_strings(&snapshot, PAGE_KIND_DEVICE_SESSIONS, 0, 50)
            .expect("sessions");
        assert_eq!(sessions.len() / DEVICE_SESSION_VALUE_COUNT, 2);
        assert_eq!(
            &sessions[..DEVICE_SESSION_VALUE_COUNT],
            session_to_strings(&snapshot.device_sessions[0])
        );
    }

    fn catalog_item(id: &str) -> panda_engine_core::MediaItem {
        panda_engine_core::MediaItem {
            id: id.into(),
            title: format!("Title {id}"),
            artist: "Artist".into(),
            ..Default::default()
        }
    }

    fn device_session(id: &str) -> panda_engine_core::AuthSession {
        panda_engine_core::AuthSession {
            id: id.into(),
            device_label: "Car".into(),
            created_at_epoch_millis: 1,
            last_used_at_epoch_millis: 2,
            expires_at_epoch_millis: 3,
            current: false,
        }
    }

    fn history_entry(id: &str, track_id: Option<&str>) -> panda_engine_core::EngineHistoryEntry {
        panda_engine_core::EngineHistoryEntry {
            id: id.into(),
            played_at_epoch_millis: Some(1_234),
            duration_millis: 90_000,
            completion_ratio: 0.75,
            track: track_id.map(|track_id| panda_engine_core::EngineTrack {
                id: track_id.into(),
                title: "Played Track".into(),
                artist: panda_engine_core::EngineArtist {
                    id: "artist-1".into(),
                    name: "Artist".into(),
                },
                album: Some(panda_engine_core::EngineAlbum {
                    id: "album-1".into(),
                    title: "Album".into(),
                }),
                duration_millis: 180_000,
                explicit: false,
                artwork: Some(panda_engine_core::EngineArtwork {
                    id: "art-1".into(),
                    content_hash: "hash-1".into(),
                    uri: Some("https://example.com/artwork/art-1/hash-1".into()),
                }),
                genres: Vec::new(),
            }),
        }
    }
}
