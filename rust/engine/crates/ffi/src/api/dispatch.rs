use std::ffi::{CStr, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};

use futures_util::FutureExt;
use panda_engine_core::{EngineCommand, EngineCommandType, ThemePreference};
use serde::Deserialize;

use crate::engine_handle::remember_outcome;
use crate::mappings::{command_from_ffi, platform_event_from_ffi};
use crate::{
    FFI_COMMAND_APPLY_REMOTE_THEME_PREFERENCE, FFI_COMMAND_BROWSE,
    FFI_COMMAND_HYDRATE_THEME_PREFERENCE, FFI_COMMAND_LOAD_NEXT_CATALOG_PAGE,
    FFI_COMMAND_PLAY_MEDIA_BY_ID, FFI_COMMAND_PLAY_QUEUE, FFI_COMMAND_PROCESS_VOICE,
    FFI_COMMAND_SEARCH, FFI_COMMAND_SEEK, FFI_COMMAND_SET_SPEED, FFI_COMMAND_SET_THEME_PREFERENCE,
    FFI_COMMAND_START_SESSION, FFI_COMMAND_UNKNOWN, FfiEngineOutcome, PandaEngine,
};

#[derive(Deserialize)]
struct ThemePreferencePayload {
    version: u32,
    theme_id: String,
    #[serde(default)]
    user_id: Option<String>,
    #[serde(default)]
    baseline_revision: Option<u64>,
}

fn parse_theme_payload(payload: Option<&str>) -> Option<ThemePreferencePayload> {
    let payload: ThemePreferencePayload = serde_json::from_str(payload?).ok()?;
    (payload.version == 1 && ThemePreference::from_wire(&payload.theme_id).is_some())
        .then_some(payload)
}

#[derive(Deserialize)]
struct UpsertProfilePayload {
    version: u32,
    #[serde(default)]
    display_name: Option<String>,
}

#[derive(Deserialize)]
struct UpdateProfilePayload {
    version: u32,
    update_display_name: bool,
    #[serde(default)]
    display_name: Option<String>,
}

#[derive(Deserialize)]
struct UpdateProfilePreferencesPayload {
    version: u32,
    values: serde_json::Map<String, serde_json::Value>,
}

fn profile_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    match command_type {
        crate::FFI_COMMAND_UPSERT_PROFILE => {
            let payload: UpsertProfilePayload = serde_json::from_str(payload?).ok()?;
            (payload.version == 1).then(|| EngineCommand::upsert_profile(payload.display_name))
        }
        crate::FFI_COMMAND_GET_PROFILE => Some(EngineCommand::get_profile()),
        crate::FFI_COMMAND_UPDATE_PROFILE => {
            let payload: UpdateProfilePayload = serde_json::from_str(payload?).ok()?;
            (payload.version == 1 && payload.update_display_name).then(|| {
                EngineCommand::update_profile(panda_engine_core::EngineProfileUpdate::display_name(
                    payload.display_name,
                ))
            })
        }
        crate::FFI_COMMAND_DELETE_PROFILE => Some(EngineCommand::delete_profile()),
        crate::FFI_COMMAND_LOAD_PROFILE_PREFERENCES => {
            Some(EngineCommand::load_profile_preferences())
        }
        crate::FFI_COMMAND_UPDATE_PROFILE_PREFERENCES => {
            let payload: UpdateProfilePreferencesPayload = serde_json::from_str(payload?).ok()?;
            (payload.version == 1)
                .then(|| EngineCommand::update_profile_preferences(payload.values))
        }
        _ => None,
    }
}

fn parsed_history_wire_command(command_type: &str, payload: Option<&str>) -> Option<EngineCommand> {
    let command = EngineCommand::from_wire(command_type, payload.map(str::to_owned));
    if matches!(command.command_type, EngineCommandType::Unknown(_)) {
        None
    } else {
        Some(command)
    }
}

fn history_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    match command_type {
        crate::FFI_COMMAND_LOAD_HISTORY_SETTINGS => Some(EngineCommand::load_history_settings()),
        crate::FFI_COMMAND_UPDATE_HISTORY_SETTINGS => {
            parsed_history_wire_command(EngineCommandType::UPDATE_HISTORY_SETTINGS_WIRE, payload)
        }
        crate::FFI_COMMAND_LIST_HISTORY => {
            parsed_history_wire_command(EngineCommandType::LIST_HISTORY_WIRE, payload)
        }
        crate::FFI_COMMAND_LOAD_NEXT_HISTORY_PAGE => Some(EngineCommand::load_next_history_page()),
        crate::FFI_COMMAND_DELETE_HISTORY_ENTRY => {
            parsed_history_wire_command(EngineCommandType::DELETE_HISTORY_ENTRY_WIRE, payload)
        }
        crate::FFI_COMMAND_CLEAR_HISTORY => Some(EngineCommand::clear_history()),
        _ => None,
    }
}

fn library_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    let wire = match command_type {
        crate::FFI_COMMAND_SAVE_TRACK => EngineCommandType::SAVE_TRACK_WIRE,
        crate::FFI_COMMAND_REMOVE_SAVED_TRACK => EngineCommandType::REMOVE_SAVED_TRACK_WIRE,
        crate::FFI_COMMAND_LIST_SAVED_TRACKS => EngineCommandType::LIST_SAVED_TRACKS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE => {
            EngineCommandType::LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE
        }
        crate::FFI_COMMAND_LIKE_TRACK => EngineCommandType::LIKE_TRACK_WIRE,
        crate::FFI_COMMAND_UNLIKE_TRACK => EngineCommandType::UNLIKE_TRACK_WIRE,
        crate::FFI_COMMAND_LIST_LIKED_TRACKS => EngineCommandType::LIST_LIKED_TRACKS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE => {
            EngineCommandType::LOAD_NEXT_LIKED_TRACKS_PAGE_WIRE
        }
        _ => return None,
    };
    let command = EngineCommand::from_wire(wire, payload.map(str::to_owned));
    (!matches!(command.command_type, EngineCommandType::Unknown(_))).then_some(command)
}

fn playlist_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    let wire = match command_type {
        crate::FFI_COMMAND_CREATE_PLAYLIST => EngineCommandType::CREATE_PLAYLIST_WIRE,
        crate::FFI_COMMAND_UPDATE_PLAYLIST => EngineCommandType::UPDATE_PLAYLIST_WIRE,
        crate::FFI_COMMAND_DELETE_PLAYLIST => EngineCommandType::DELETE_PLAYLIST_WIRE,
        crate::FFI_COMMAND_LIST_PLAYLISTS => EngineCommandType::LIST_PLAYLISTS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_PLAYLISTS_PAGE => {
            EngineCommandType::LOAD_NEXT_PLAYLISTS_PAGE_WIRE
        }
        crate::FFI_COMMAND_ADD_PLAYLIST_TRACK => EngineCommandType::ADD_PLAYLIST_TRACK_WIRE,
        crate::FFI_COMMAND_REMOVE_PLAYLIST_TRACK => EngineCommandType::REMOVE_PLAYLIST_TRACK_WIRE,
        crate::FFI_COMMAND_LIST_PLAYLIST_TRACKS => EngineCommandType::LIST_PLAYLIST_TRACKS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_PLAYLIST_TRACKS_PAGE => {
            EngineCommandType::LOAD_NEXT_PLAYLIST_TRACKS_PAGE_WIRE
        }
        crate::FFI_COMMAND_REORDER_PLAYLIST_TRACKS => {
            EngineCommandType::REORDER_PLAYLIST_TRACKS_WIRE
        }
        _ => return None,
    };
    let command = EngineCommand::from_wire(wire, payload.map(str::to_owned));
    (!matches!(command.command_type, EngineCommandType::Unknown(_))).then_some(command)
}

fn account_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    let wire = match command_type {
        crate::FFI_COMMAND_GET_ACCOUNT => EngineCommandType::GET_ACCOUNT_WIRE,
        crate::FFI_COMMAND_DELETE_ACCOUNT => EngineCommandType::DELETE_ACCOUNT_WIRE,
        crate::FFI_COMMAND_LIST_DEVICE_SESSIONS => EngineCommandType::LIST_DEVICE_SESSIONS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_DEVICE_SESSIONS_PAGE => {
            EngineCommandType::LOAD_NEXT_DEVICE_SESSIONS_PAGE_WIRE
        }
        crate::FFI_COMMAND_REVOKE_DEVICE_SESSION => EngineCommandType::REVOKE_DEVICE_SESSION_WIRE,
        _ => return None,
    };
    let command = EngineCommand::from_wire(wire, payload.map(str::to_owned));
    (!matches!(command.command_type, EngineCommandType::Unknown(_))).then_some(command)
}

fn discovery_command_from_ffi(command_type: i32, payload: Option<&str>) -> Option<EngineCommand> {
    let wire = match command_type {
        crate::FFI_COMMAND_LOAD_DISCOVERY_FEED => EngineCommandType::LOAD_DISCOVERY_FEED_WIRE,
        crate::FFI_COMMAND_LOAD_FOR_YOU_FEED => EngineCommandType::LOAD_FOR_YOU_FEED_WIRE,
        crate::FFI_COMMAND_LOAD_RECOMMENDATIONS => EngineCommandType::LOAD_RECOMMENDATIONS_WIRE,
        crate::FFI_COMMAND_LOAD_NEXT_DISCOVERY_PAGE => {
            EngineCommandType::LOAD_NEXT_DISCOVERY_PAGE_WIRE
        }
        _ => return None,
    };
    let command = EngineCommand::from_wire(wire, payload.map(str::to_owned));
    (!matches!(command.command_type, EngineCommandType::Unknown(_))).then_some(command)
}

fn run_future_safely<T>(
    runtime: &tokio::runtime::Runtime,
    future: impl std::future::Future<Output = T>,
) -> Option<T> {
    let future_result = catch_unwind(AssertUnwindSafe(|| {
        runtime.block_on(AssertUnwindSafe(future).catch_unwind())
    }));

    match future_result {
        Ok(Ok(value)) => Some(value),
        Ok(Err(_)) | Err(_) => None,
    }
}

fn dispatch_voice_chunk(
    engine: &mut PandaEngine,
    chunk: Vec<i16>,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let command = EngineCommand::process_voice_audio(chunk);
    let outcome = match run_future_safely(
        &engine.runtime,
        engine.engine.dispatch(command, now_epoch_millis),
    ) {
        Some(Ok(outcome)) => outcome,
        None => return FfiEngineOutcome::invalid(),
        Some(Err(_)) => return FfiEngineOutcome::invalid(),
    };
    remember_outcome(engine, &outcome);
    FfiEngineOutcome::from((&outcome, FFI_COMMAND_PROCESS_VOICE))
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `payload` may be null; if non-null, it must point to a valid NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_dispatch(
    engine: *mut PandaEngine,
    command_type: i32,
    payload: *const c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let _trace = crate::perfetto_trace::section("PW.Native.dispatch");
    let engine = unsafe { engine.as_mut() };
    let payload_str = if payload.is_null() {
        None
    } else {
        Some(
            unsafe { std::ffi::CStr::from_ptr(payload) }
                .to_string_lossy()
                .into_owned(),
        )
    };

    match engine {
        Some(engine) => {
            let command = match command_type {
                FFI_COMMAND_SEARCH => {
                    EngineCommand::from_wire(EngineCommandType::SEARCH_WIRE, payload_str.clone())
                }
                FFI_COMMAND_BROWSE => {
                    EngineCommand::from_wire(EngineCommandType::BROWSE_WIRE, payload_str.clone())
                }
                FFI_COMMAND_LOAD_NEXT_CATALOG_PAGE => EngineCommand::from_wire(
                    EngineCommandType::LOAD_NEXT_CATALOG_PAGE_WIRE,
                    payload_str.clone(),
                ),
                FFI_COMMAND_SET_SPEED => {
                    let speed = payload_str
                        .and_then(|s| s.parse::<f32>().ok())
                        .unwrap_or(1.0);
                    EngineCommand::new(EngineCommandType::SetSpeed { speed }, None)
                }
                FFI_COMMAND_SEEK => {
                    let pos = payload_str.and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
                    EngineCommand::new(
                        EngineCommandType::Seek {
                            position_millis: pos,
                        },
                        None,
                    )
                }
                FFI_COMMAND_START_SESSION => EngineCommand::new(
                    EngineCommandType::StartSession {
                        user_id: payload_str
                            .as_deref()
                            .filter(|value| !value.is_empty())
                            .unwrap_or("unknown")
                            .to_string(),
                    },
                    None,
                ),
                FFI_COMMAND_PLAY_MEDIA_BY_ID => EngineCommand::new(
                    EngineCommandType::PlayMediaById {
                        media_id: payload_str.clone().unwrap_or_default(),
                    },
                    None,
                ),
                FFI_COMMAND_PLAY_QUEUE => EngineCommand::from_wire(
                    EngineCommandType::PLAY_QUEUE_WIRE,
                    payload_str.clone(),
                ),
                FFI_COMMAND_HYDRATE_THEME_PREFERENCE => parse_theme_payload(payload_str.as_deref())
                    .and_then(|payload| ThemePreference::from_wire(&payload.theme_id))
                    .map(EngineCommand::hydrate_theme_preference)
                    .unwrap_or_else(|| EngineCommand::from_wire("invalid_theme_payload", None)),
                FFI_COMMAND_SET_THEME_PREFERENCE => parse_theme_payload(payload_str.as_deref())
                    .and_then(|payload| ThemePreference::from_wire(&payload.theme_id))
                    .map(EngineCommand::set_theme_preference)
                    .unwrap_or_else(|| EngineCommand::from_wire("invalid_theme_payload", None)),
                FFI_COMMAND_APPLY_REMOTE_THEME_PREFERENCE => {
                    let parsed = parse_theme_payload(payload_str.as_deref());
                    match parsed.and_then(|payload| {
                        Some((
                            ThemePreference::from_wire(&payload.theme_id)?,
                            payload.user_id?,
                            payload.baseline_revision?,
                        ))
                    }) {
                        Some((theme, user_id, baseline_revision)) => {
                            EngineCommand::apply_remote_theme_preference(
                                theme,
                                user_id,
                                baseline_revision,
                            )
                        }
                        None => EngineCommand::from_wire("invalid_theme_payload", None),
                    }
                }
                crate::FFI_COMMAND_UPSERT_PROFILE
                | crate::FFI_COMMAND_GET_PROFILE
                | crate::FFI_COMMAND_UPDATE_PROFILE
                | crate::FFI_COMMAND_DELETE_PROFILE
                | crate::FFI_COMMAND_LOAD_PROFILE_PREFERENCES
                | crate::FFI_COMMAND_UPDATE_PROFILE_PREFERENCES => {
                    profile_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_profile_payload", None),
                    )
                }
                crate::FFI_COMMAND_LOAD_HISTORY_SETTINGS
                | crate::FFI_COMMAND_UPDATE_HISTORY_SETTINGS
                | crate::FFI_COMMAND_LIST_HISTORY
                | crate::FFI_COMMAND_LOAD_NEXT_HISTORY_PAGE
                | crate::FFI_COMMAND_DELETE_HISTORY_ENTRY
                | crate::FFI_COMMAND_CLEAR_HISTORY => {
                    history_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_history_payload", None),
                    )
                }
                crate::FFI_COMMAND_SAVE_TRACK
                | crate::FFI_COMMAND_REMOVE_SAVED_TRACK
                | crate::FFI_COMMAND_LIST_SAVED_TRACKS
                | crate::FFI_COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE
                | crate::FFI_COMMAND_LIKE_TRACK
                | crate::FFI_COMMAND_UNLIKE_TRACK
                | crate::FFI_COMMAND_LIST_LIKED_TRACKS
                | crate::FFI_COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE => {
                    library_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_library_payload", None),
                    )
                }
                crate::FFI_COMMAND_CREATE_PLAYLIST
                | crate::FFI_COMMAND_UPDATE_PLAYLIST
                | crate::FFI_COMMAND_DELETE_PLAYLIST
                | crate::FFI_COMMAND_LIST_PLAYLISTS
                | crate::FFI_COMMAND_LOAD_NEXT_PLAYLISTS_PAGE
                | crate::FFI_COMMAND_ADD_PLAYLIST_TRACK
                | crate::FFI_COMMAND_REMOVE_PLAYLIST_TRACK
                | crate::FFI_COMMAND_LIST_PLAYLIST_TRACKS
                | crate::FFI_COMMAND_LOAD_NEXT_PLAYLIST_TRACKS_PAGE
                | crate::FFI_COMMAND_REORDER_PLAYLIST_TRACKS => {
                    playlist_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_playlist_payload", None),
                    )
                }
                crate::FFI_COMMAND_GET_ACCOUNT
                | crate::FFI_COMMAND_DELETE_ACCOUNT
                | crate::FFI_COMMAND_LIST_DEVICE_SESSIONS
                | crate::FFI_COMMAND_LOAD_NEXT_DEVICE_SESSIONS_PAGE
                | crate::FFI_COMMAND_REVOKE_DEVICE_SESSION => {
                    account_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_account_payload", None),
                    )
                }
                crate::FFI_COMMAND_LOAD_DISCOVERY_FEED
                | crate::FFI_COMMAND_LOAD_FOR_YOU_FEED
                | crate::FFI_COMMAND_LOAD_RECOMMENDATIONS
                | crate::FFI_COMMAND_LOAD_NEXT_DISCOVERY_PAGE => {
                    discovery_command_from_ffi(command_type, payload_str.as_deref()).unwrap_or_else(
                        || EngineCommand::from_wire("invalid_discovery_payload", None),
                    )
                }
                FFI_COMMAND_PROCESS_VOICE => return FfiEngineOutcome::invalid(),
                _ => EngineCommand::new(command_from_ffi(command_type), payload_str),
            };

            let outcome = match run_future_safely(
                &engine.runtime,
                engine.engine.dispatch(command, now_epoch_millis),
            ) {
                Some(Ok(outcome)) => outcome,
                None => return FfiEngineOutcome::invalid(),
                Some(Err(_)) => return FfiEngineOutcome::invalid(),
            };
            remember_outcome(engine, &outcome);
            FfiEngineOutcome::from((&outcome, command_type))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `audio` must point to a readable buffer of `len` `i16` samples unless `len == 0`.
/// - If `len > 0`, `audio` must be non-null and valid for reads for the duration of this call.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_process_audio_raw(
    engine: *mut PandaEngine,
    audio: *const i16,
    len: usize,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            if len > 0 && audio.is_null() {
                return FfiEngineOutcome::invalid();
            }
            let chunk = if len == 0 {
                Vec::new()
            } else {
                unsafe { std::slice::from_raw_parts(audio, len) }.to_vec()
            };
            dispatch_voice_chunk(engine, chunk, now_epoch_millis)
        }
        None => FfiEngineOutcome::invalid(),
    }
}

#[unsafe(no_mangle)]
/// # Safety
/// - `engine` must be a valid pointer created by `panda_engine_create` and not yet destroyed.
/// - `payload` may be null; if non-null, it must point to a valid NUL-terminated C string.
/// - The caller must ensure no concurrent mutable access to the same engine instance.
pub unsafe extern "C" fn panda_engine_dispatch_platform_event(
    engine: *mut PandaEngine,
    event_type: i32,
    payload: *const c_char,
    now_epoch_millis: u64,
) -> FfiEngineOutcome {
    let _trace = crate::perfetto_trace::section("PW.Native.platformEvent");
    let engine = unsafe { engine.as_mut() };
    match engine {
        Some(engine) => {
            let payload = if payload.is_null() {
                None
            } else {
                unsafe { CStr::from_ptr(payload) }
                    .to_str()
                    .ok()
                    .map(str::to_owned)
            };
            let outcome = match run_future_safely(
                &engine.runtime,
                engine.engine.dispatch_platform_event(
                    panda_engine_core::EnginePlatformEvent::new(
                        platform_event_from_ffi(event_type),
                        payload,
                    ),
                    now_epoch_millis,
                ),
            ) {
                Some(Ok(outcome)) => outcome,
                None => return FfiEngineOutcome::invalid(),
                Some(Err(_)) => return FfiEngineOutcome::invalid(),
            };
            remember_outcome(engine, &outcome);
            FfiEngineOutcome::from((&outcome, FFI_COMMAND_UNKNOWN))
        }
        None => FfiEngineOutcome::invalid(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn profile_update_payload_preserves_clear_distinct_from_empty_text() {
        let clear = profile_command_from_ffi(
            crate::FFI_COMMAND_UPDATE_PROFILE,
            Some(r#"{"version":1,"update_display_name":true,"display_name":null}"#),
        )
        .unwrap();
        let empty = profile_command_from_ffi(
            crate::FFI_COMMAND_UPDATE_PROFILE,
            Some(r#"{"version":1,"update_display_name":true,"display_name":""}"#),
        )
        .unwrap();

        match clear.command_type {
            EngineCommandType::UpdateProfile { update } => {
                assert_eq!(update.display_name, Some(None));
            }
            other => panic!("unexpected command: {other:?}"),
        }
        match empty.command_type {
            EngineCommandType::UpdateProfile { update } => {
                assert_eq!(update.display_name, Some(Some(String::new())));
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn nested_history_page_payload_reaches_engine_command() {
        let command = history_command_from_ffi(
            crate::FFI_COMMAND_LIST_HISTORY,
            Some(r#"{"version":1,"page":{"page_size":37}}"#),
        )
        .expect("canonical nested history page payload");

        assert_eq!(
            command.command_type,
            EngineCommandType::ListHistory {
                page: panda_engine_core::EnginePageRequest {
                    page_size: 37,
                    page_token: None,
                },
            },
        );
    }

    #[test]
    fn playlist_ffi_discriminants_parse_their_wire_payloads() {
        let cases = [
            (
                crate::FFI_COMMAND_CREATE_PLAYLIST,
                r#"{"version":1,"playlist_id":null,"name":"Mix","description":"","expected_revision":null}"#,
            ),
            (
                crate::FFI_COMMAND_UPDATE_PLAYLIST,
                r#"{"version":1,"playlist_id":"p1","name":"Mix","description":null,"expected_revision":7}"#,
            ),
            (
                crate::FFI_COMMAND_DELETE_PLAYLIST,
                r#"{"version":1,"playlist_id":"p1"}"#,
            ),
            (
                crate::FFI_COMMAND_LIST_PLAYLISTS,
                r#"{"version":1,"playlist_id":null,"page":{"page_size":25}}"#,
            ),
            (crate::FFI_COMMAND_LOAD_NEXT_PLAYLISTS_PAGE, ""),
            (
                crate::FFI_COMMAND_ADD_PLAYLIST_TRACK,
                r#"{"version":1,"playlist_id":"p1","track_id":"t1"}"#,
            ),
            (
                crate::FFI_COMMAND_REMOVE_PLAYLIST_TRACK,
                r#"{"version":1,"playlist_id":"p1","track_id":"t1"}"#,
            ),
            (
                crate::FFI_COMMAND_LIST_PLAYLIST_TRACKS,
                r#"{"version":1,"playlist_id":"p1","page":{"page_size":25}}"#,
            ),
            (crate::FFI_COMMAND_LOAD_NEXT_PLAYLIST_TRACKS_PAGE, ""),
            (
                crate::FFI_COMMAND_REORDER_PLAYLIST_TRACKS,
                r#"{"version":1,"playlist_id":"p1","ordered_membership_ids":["m2","m1"],"expected_revision":7}"#,
            ),
        ];

        for (command_type, payload) in cases {
            assert!(
                playlist_command_from_ffi(command_type, (!payload.is_empty()).then_some(payload))
                    .is_some(),
                "playlist FFI command {command_type} did not parse",
            );
        }
    }

    #[test]
    fn account_ffi_discriminants_parse_strict_credential_free_payloads() {
        let cases = [
            (crate::FFI_COMMAND_GET_ACCOUNT, None),
            (crate::FFI_COMMAND_DELETE_ACCOUNT, None),
            (
                crate::FFI_COMMAND_LIST_DEVICE_SESSIONS,
                Some(r#"{"version":1,"page":{"page_size":25}}"#),
            ),
            (crate::FFI_COMMAND_LOAD_NEXT_DEVICE_SESSIONS_PAGE, None),
            (
                crate::FFI_COMMAND_REVOKE_DEVICE_SESSION,
                Some(r#"{"version":1,"session_id":"session-1"}"#),
            ),
        ];

        for (command_type, payload) in cases {
            assert!(
                account_command_from_ffi(command_type, payload).is_some(),
                "account FFI command {command_type} did not parse",
            );
        }
        assert!(
            account_command_from_ffi(
                crate::FFI_COMMAND_REVOKE_DEVICE_SESSION,
                Some(r#"{"version":1,"session_id":"session-1","access_token":"forbidden"}"#),
            )
            .is_none()
        );
    }

    #[test]
    fn discovery_ffi_discriminants_parse_strict_credential_free_payloads() {
        let first = discovery_command_from_ffi(
            crate::FFI_COMMAND_LOAD_DISCOVERY_FEED,
            Some(r#"{"version":1,"exclude_track_ids":["track-1"],"page":{"page_size":17}}"#),
        )
        .expect("first discovery page");
        assert_eq!(
            first.command_type,
            EngineCommandType::LoadDiscoveryFeed {
                excluded_track_ids: vec!["track-1".to_owned()],
                page: panda_engine_core::EnginePageRequest {
                    page_size: 17,
                    page_token: None,
                },
            }
        );
        assert!(
            discovery_command_from_ffi(
                crate::FFI_COMMAND_LOAD_NEXT_DISCOVERY_PAGE,
                Some(r#"{"version":1}"#),
            )
            .is_some()
        );
        assert!(discovery_command_from_ffi(
            crate::FFI_COMMAND_LOAD_DISCOVERY_FEED,
            Some(r#"{"version":1,"exclude_track_ids":[],"page":{"page_size":1,"page_token":"forbidden"}}"#),
        )
        .is_none());
        assert!(
            discovery_command_from_ffi(
                crate::FFI_COMMAND_LOAD_NEXT_DISCOVERY_PAGE,
                Some(r#"{"version":1,"page_token":"forbidden"}"#),
            )
            .is_none()
        );
        assert!(discovery_command_from_ffi(
            crate::FFI_COMMAND_LOAD_DISCOVERY_FEED,
            Some(r#"{"version":1,"exclude_track_ids":[],"page":{"page_size":1},"access_token":"forbidden"}"#),
        )
        .is_none());
    }

    #[test]
    fn invalid_profile_payload_is_rejected_before_dispatch() {
        assert!(
            profile_command_from_ffi(
                crate::FFI_COMMAND_UPDATE_PROFILE,
                Some(r#"{"version":2,"update_display_name":true}"#),
            )
            .is_none()
        );
    }
}
