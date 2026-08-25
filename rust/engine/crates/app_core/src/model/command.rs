mod parsing;
mod payloads;
mod types;

#[cfg(test)]
mod tests;

use crate::EnginePageRequest;

use parsing::{
    parse_browse_catalog_payload, parse_create_playlist_payload,
    parse_delete_history_entry_payload, parse_delete_playlist_payload,
    parse_discovery_feed_payload, parse_list_device_sessions_payload, parse_list_history_payload,
    parse_list_library_payload, parse_list_playlist_tracks_payload, parse_list_playlists_payload,
    parse_load_next_catalog_page_payload, parse_load_next_discovery_page_payload,
    parse_play_queue_payload, parse_playlist_track_payload, parse_reorder_playlist_tracks_payload,
    parse_revoke_device_session_payload, parse_search_catalog_payload,
    parse_track_relationship_payload, parse_update_history_settings_payload,
    parse_update_playlist_payload,
};
use payloads::{
    BrowseCatalogPayload, DiscoveryFeedPayload, InitialCatalogPagePayload,
    LoadNextCatalogPagePayload, LoadNextDiscoveryPagePayload, SearchCatalogPayload,
};
pub use types::EngineCommandType;

const CATALOG_PAYLOAD_VERSION: u32 = 1;

/// A command sent to the engine to trigger a state transition.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineCommand {
    /// The type of the command.
    pub command_type: EngineCommandType,
    /// Optional JSON-encoded or raw string payload for the command.
    pub payload: Option<String>,
}

impl EngineCommand {
    /// Creates a new engine command.
    pub fn new(command_type: EngineCommandType, payload: Option<String>) -> Self {
        Self {
            command_type,
            payload,
        }
    }

    /// Convenience method to create a command from wire values.
    pub fn from_wire(command_type: impl Into<String>, payload: Option<String>) -> Self {
        let command_type = command_type.into();
        let parsed = match command_type.as_str() {
            EngineCommandType::SEARCH_WIRE => {
                payload.as_deref().and_then(parse_search_catalog_payload)
            }
            EngineCommandType::BROWSE_WIRE => {
                payload.as_deref().and_then(parse_browse_catalog_payload)
            }
            EngineCommandType::LOAD_NEXT_CATALOG_PAGE_WIRE => payload
                .as_deref()
                .and_then(parse_load_next_catalog_page_payload),
            EngineCommandType::LOAD_DISCOVERY_FEED_WIRE => payload.as_deref().and_then(|payload| {
                parse_discovery_feed_payload(crate::DiscoveryFeed::Discovery, payload)
            }),
            EngineCommandType::LOAD_FOR_YOU_FEED_WIRE => payload.as_deref().and_then(|payload| {
                parse_discovery_feed_payload(crate::DiscoveryFeed::ForYou, payload)
            }),
            EngineCommandType::LOAD_RECOMMENDATIONS_WIRE => {
                payload.as_deref().and_then(|payload| {
                    parse_discovery_feed_payload(crate::DiscoveryFeed::Recommendations, payload)
                })
            }
            EngineCommandType::LOAD_NEXT_DISCOVERY_PAGE_WIRE => payload
                .as_deref()
                .and_then(parse_load_next_discovery_page_payload),
            EngineCommandType::UPDATE_HISTORY_SETTINGS_WIRE => payload
                .as_deref()
                .and_then(parse_update_history_settings_payload),
            EngineCommandType::LIST_HISTORY_WIRE => {
                payload.as_deref().and_then(parse_list_history_payload)
            }
            EngineCommandType::DELETE_HISTORY_ENTRY_WIRE => payload
                .as_deref()
                .and_then(parse_delete_history_entry_payload),
            EngineCommandType::SAVE_TRACK_WIRE
            | EngineCommandType::REMOVE_SAVED_TRACK_WIRE
            | EngineCommandType::LIKE_TRACK_WIRE
            | EngineCommandType::UNLIKE_TRACK_WIRE => payload
                .as_deref()
                .and_then(|payload| parse_track_relationship_payload(&command_type, payload)),
            EngineCommandType::LIST_SAVED_TRACKS_WIRE
            | EngineCommandType::LIST_LIKED_TRACKS_WIRE => payload
                .as_deref()
                .and_then(|payload| parse_list_library_payload(&command_type, payload)),
            EngineCommandType::LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE
            | EngineCommandType::LOAD_NEXT_LIKED_TRACKS_PAGE_WIRE => payload
                .is_none()
                .then(|| EngineCommandType::from_wire(command_type.clone())),
            EngineCommandType::CREATE_PLAYLIST_WIRE => {
                payload.as_deref().and_then(parse_create_playlist_payload)
            }
            EngineCommandType::UPDATE_PLAYLIST_WIRE => {
                payload.as_deref().and_then(parse_update_playlist_payload)
            }
            EngineCommandType::DELETE_PLAYLIST_WIRE => {
                payload.as_deref().and_then(parse_delete_playlist_payload)
            }
            EngineCommandType::LIST_PLAYLISTS_WIRE => {
                payload.as_deref().and_then(parse_list_playlists_payload)
            }
            EngineCommandType::LOAD_NEXT_PLAYLISTS_PAGE_WIRE
            | EngineCommandType::LOAD_NEXT_PLAYLIST_TRACKS_PAGE_WIRE => payload
                .is_none()
                .then(|| EngineCommandType::from_wire(command_type.clone())),
            EngineCommandType::ADD_PLAYLIST_TRACK_WIRE
            | EngineCommandType::REMOVE_PLAYLIST_TRACK_WIRE => payload
                .as_deref()
                .and_then(|payload| parse_playlist_track_payload(&command_type, payload)),
            EngineCommandType::LIST_PLAYLIST_TRACKS_WIRE => payload
                .as_deref()
                .and_then(parse_list_playlist_tracks_payload),
            EngineCommandType::REORDER_PLAYLIST_TRACKS_WIRE => payload
                .as_deref()
                .and_then(parse_reorder_playlist_tracks_payload),
            EngineCommandType::GET_ACCOUNT_WIRE
            | EngineCommandType::DELETE_ACCOUNT_WIRE
            | EngineCommandType::LOAD_NEXT_DEVICE_SESSIONS_PAGE_WIRE => payload
                .is_none()
                .then(|| EngineCommandType::from_wire(command_type.clone())),
            EngineCommandType::LIST_DEVICE_SESSIONS_WIRE => payload
                .as_deref()
                .and_then(parse_list_device_sessions_payload),
            EngineCommandType::REVOKE_DEVICE_SESSION_WIRE => payload
                .as_deref()
                .and_then(parse_revoke_device_session_payload),
            EngineCommandType::PLAY_QUEUE_WIRE => {
                payload.as_deref().and_then(parse_play_queue_payload)
            }
            _ => Some(EngineCommandType::from_wire(command_type.clone())),
        };
        Self::new(
            parsed.unwrap_or_else(|| {
                EngineCommandType::Unknown(format!("invalid_{command_type}_payload"))
            }),
            payload,
        )
    }

    /// Creates a Play command.
    pub fn play() -> Self {
        Self::new(EngineCommandType::Play, None)
    }

    /// Creates a Pause command.
    pub fn pause() -> Self {
        Self::new(EngineCommandType::Pause, None)
    }

    /// Creates a SkipNext command.
    pub fn skip_next() -> Self {
        Self::new(EngineCommandType::SkipNext, None)
    }

    /// Creates a SkipPrevious command.
    pub fn skip_previous() -> Self {
        Self::new(EngineCommandType::SkipPrevious, None)
    }

    /// Creates a StartSession command.
    pub fn start_session(user_id: String) -> Self {
        Self::new(EngineCommandType::StartSession { user_id }, None)
    }

    /// Creates an EndSession command.
    pub fn end_session() -> Self {
        Self::new(EngineCommandType::EndSession, None)
    }

    /// Creates a public backend health refresh command.
    pub fn refresh_backend_status() -> Self {
        Self::new(EngineCommandType::RefreshBackendStatus, None)
    }

    /// Creates a Search command.
    pub fn search(query: String) -> Self {
        Self::search_catalog(query, EnginePageRequest::default())
    }

    /// Creates a Browse command.
    pub fn browse(parent_id: String) -> Self {
        Self::browse_catalog(Some(parent_id), Vec::new(), EnginePageRequest::default())
    }

    pub fn search_catalog(query: String, page: EnginePageRequest) -> Self {
        let page = EnginePageRequest {
            page_size: page.page_size,
            page_token: None,
        };
        let payload = SearchCatalogPayload {
            version: CATALOG_PAYLOAD_VERSION,
            query: query.clone(),
            page: InitialCatalogPagePayload {
                page_size: page.page_size,
            },
        };
        Self::new(
            EngineCommandType::SearchCatalog { query, page },
            serde_json::to_string(&payload).ok(),
        )
    }

    pub fn browse_catalog(
        parent_id: Option<String>,
        genres: Vec<String>,
        page: EnginePageRequest,
    ) -> Self {
        let page = EnginePageRequest {
            page_size: page.page_size,
            page_token: None,
        };
        let payload = BrowseCatalogPayload {
            version: CATALOG_PAYLOAD_VERSION,
            parent_id: parent_id.clone(),
            genres: genres.clone(),
            page: InitialCatalogPagePayload {
                page_size: page.page_size,
            },
        };
        Self::new(
            EngineCommandType::BrowseCatalog {
                parent_id,
                genres,
                page,
            },
            serde_json::to_string(&payload).ok(),
        )
    }

    pub fn load_next_catalog_page(operation_id: String) -> Self {
        let payload = LoadNextCatalogPagePayload {
            version: CATALOG_PAYLOAD_VERSION,
            operation_id: operation_id.clone(),
        };
        Self::new(
            EngineCommandType::LoadNextCatalogPage { operation_id },
            serde_json::to_string(&payload).ok(),
        )
    }

    pub fn load_discovery_feed(excluded_track_ids: Vec<String>, page: EnginePageRequest) -> Self {
        let page = EnginePageRequest {
            page_size: page.page_size,
            page_token: None,
        };
        let payload = DiscoveryFeedPayload {
            version: CATALOG_PAYLOAD_VERSION,
            exclude_track_ids: excluded_track_ids.clone(),
            page: InitialCatalogPagePayload {
                page_size: page.page_size,
            },
        };
        Self::new(
            EngineCommandType::LoadDiscoveryFeed {
                excluded_track_ids,
                page,
            },
            serde_json::to_string(&payload).ok(),
        )
    }

    pub fn load_next_discovery_page() -> Self {
        let payload = LoadNextDiscoveryPagePayload {
            version: CATALOG_PAYLOAD_VERSION,
        };
        Self::new(
            EngineCommandType::LoadNextDiscoveryPage,
            serde_json::to_string(&payload).ok(),
        )
    }

    pub fn load_history_settings() -> Self {
        Self::new(EngineCommandType::LoadHistorySettings, None)
    }

    pub fn update_history_settings(enabled: bool) -> Self {
        Self::new(EngineCommandType::UpdateHistorySettings { enabled }, None)
    }

    pub fn list_history(page_size: u32) -> Self {
        Self::new(
            EngineCommandType::ListHistory {
                page: EnginePageRequest {
                    page_size,
                    page_token: None,
                },
            },
            None,
        )
    }

    pub fn load_next_history_page() -> Self {
        Self::new(EngineCommandType::LoadNextHistoryPage, None)
    }

    pub fn delete_history_entry(history_id: impl Into<String>) -> Self {
        Self::new(
            EngineCommandType::DeleteHistoryEntry {
                history_id: history_id.into(),
            },
            None,
        )
    }

    pub fn clear_history() -> Self {
        Self::new(EngineCommandType::ClearHistory, None)
    }

    pub fn save_track(track_id: impl Into<String>) -> Self {
        Self::new(
            EngineCommandType::SaveTrack {
                track_id: track_id.into(),
            },
            None,
        )
    }

    pub fn remove_saved_track(track_id: impl Into<String>) -> Self {
        Self::new(
            EngineCommandType::RemoveSavedTrack {
                track_id: track_id.into(),
            },
            None,
        )
    }

    pub fn list_saved_tracks(page_size: u32) -> Self {
        Self::new(
            EngineCommandType::ListSavedTracks {
                page: EnginePageRequest {
                    page_size,
                    page_token: None,
                },
            },
            None,
        )
    }

    pub fn load_next_saved_tracks_page() -> Self {
        Self::new(EngineCommandType::LoadNextSavedTracksPage, None)
    }

    pub fn like_track(track_id: impl Into<String>) -> Self {
        Self::new(
            EngineCommandType::LikeTrack {
                track_id: track_id.into(),
            },
            None,
        )
    }

    pub fn unlike_track(track_id: impl Into<String>) -> Self {
        Self::new(
            EngineCommandType::UnlikeTrack {
                track_id: track_id.into(),
            },
            None,
        )
    }

    pub fn list_liked_tracks(page_size: u32) -> Self {
        Self::new(
            EngineCommandType::ListLikedTracks {
                page: EnginePageRequest {
                    page_size,
                    page_token: None,
                },
            },
            None,
        )
    }

    pub fn load_next_liked_tracks_page() -> Self {
        Self::new(EngineCommandType::LoadNextLikedTracksPage, None)
    }

    /// Creates a SetSpeed command.
    pub fn set_speed(speed: f32) -> Self {
        Self::new(EngineCommandType::SetSpeed { speed }, None)
    }

    /// Creates a Seek command.
    pub fn seek(position_millis: u64) -> Self {
        Self::new(EngineCommandType::Seek { position_millis }, None)
    }

    /// Creates an UpdateConfig command.
    pub fn update_config(config: crate::model::config::EngineConfig) -> Self {
        Self::new(EngineCommandType::UpdateConfig { config }, None)
    }

    /// Creates a command to hydrate the locally cached theme.
    pub fn hydrate_theme_preference(theme: crate::model::preferences::ThemePreference) -> Self {
        Self::new(EngineCommandType::HydrateThemePreference { theme }, None)
    }

    /// Creates a command to apply an explicit local theme selection.
    pub fn set_theme_preference(theme: crate::model::preferences::ThemePreference) -> Self {
        Self::new(EngineCommandType::SetThemePreference { theme }, None)
    }

    /// Creates a command to apply a synchronized profile theme.
    pub fn apply_remote_theme_preference(
        theme: crate::model::preferences::ThemePreference,
        user_id: String,
        baseline_revision: u64,
    ) -> Self {
        Self::new(
            EngineCommandType::ApplyRemoteThemePreference {
                theme,
                user_id,
                baseline_revision,
            },
            None,
        )
    }

    /// Creates a command to create or replace the authenticated profile.
    pub fn upsert_profile(display_name: Option<String>) -> Self {
        Self::new(EngineCommandType::UpsertProfile { display_name }, None)
    }

    /// Creates a command to load the authenticated profile.
    pub fn get_profile() -> Self {
        Self::new(EngineCommandType::GetProfile, None)
    }

    /// Creates a command to apply a typed authenticated profile patch.
    pub fn update_profile(update: crate::model::profile::EngineProfileUpdate) -> Self {
        Self::new(EngineCommandType::UpdateProfile { update }, None)
    }

    /// Creates a command to delete the authenticated profile.
    pub fn delete_profile() -> Self {
        Self::new(EngineCommandType::DeleteProfile, None)
    }

    /// Creates a command to load authenticated profile preferences.
    pub fn load_profile_preferences() -> Self {
        Self::new(EngineCommandType::LoadProfilePreferences, None)
    }

    /// Creates a command to merge authenticated profile preferences.
    pub fn update_profile_preferences(values: serde_json::Map<String, serde_json::Value>) -> Self {
        Self::new(EngineCommandType::UpdateProfilePreferences { values }, None)
    }

    /// Creates a VoicePlay command.
    pub fn voice_play(query: String) -> Self {
        Self::new(EngineCommandType::VoicePlay { query }, None)
    }

    /// Creates a command to start a voice interaction.
    pub fn start_voice_interaction() -> Self {
        Self::new(EngineCommandType::StartVoiceInteraction, None)
    }

    /// Creates a command to stop a voice interaction.
    pub fn stop_voice_interaction() -> Self {
        Self::new(EngineCommandType::StopVoiceInteraction, None)
    }

    /// Creates a command to process voice audio data.
    pub fn process_voice_audio(chunk: Vec<i16>) -> Self {
        Self::new(EngineCommandType::ProcessVoiceAudio { chunk }, None)
    }

    /// Creates a PlayMediaById command.
    pub fn play_media_by_id(media_id: String) -> Self {
        Self::new(EngineCommandType::PlayMediaById { media_id }, None)
    }

    /// Creates a PlayQueue command from an ordered media snapshot.
    pub fn play_queue(media_ids: Vec<String>, start_index: usize) -> Self {
        Self::new(
            EngineCommandType::PlayQueue {
                media_ids,
                start_index,
            },
            None,
        )
    }

    /// Creates a SetSleepTimer command.
    pub fn set_sleep_timer(duration_millis: Option<u64>) -> Self {
        Self::new(EngineCommandType::SetSleepTimer { duration_millis }, None)
    }
}
