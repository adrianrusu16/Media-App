use serde::{Deserialize, Serialize};

use crate::EnginePageRequest;

const CATALOG_PAYLOAD_VERSION: u32 = 1;

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct InitialCatalogPagePayload {
    page_size: u32,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct SearchCatalogPayload {
    version: u32,
    query: String,
    page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct BrowseCatalogPayload {
    version: u32,
    parent_id: Option<String>,
    #[serde(default)]
    genres: Vec<String>,
    page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct LoadNextCatalogPagePayload {
    version: u32,
    operation_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct DiscoveryFeedPayload {
    version: u32,
    #[serde(default)]
    exclude_track_ids: Vec<String>,
    page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct LoadNextDiscoveryPagePayload {
    version: u32,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct UpdateHistorySettingsPayload {
    version: u32,
    enabled: bool,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct ListHistoryPayload {
    version: u32,
    page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct TrackRelationshipPayload {
    version: u32,
    track_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct ListLibraryPayload {
    version: u32,
    page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct DeleteHistoryEntryPayload {
    version: u32,
    history_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaylistPayload {
    version: u32,
    playlist_id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaylistPagePayload {
    version: u32,
    playlist_id: Option<String>,
    page: InitialCatalogPagePayload,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaylistDetailsPayload {
    version: u32,
    playlist_id: Option<String>,
    name: String,
    description: Option<String>,
    expected_revision: Option<u64>,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaylistTrackPayload {
    version: u32,
    playlist_id: String,
    track_id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaylistReorderPayload {
    version: u32,
    playlist_id: String,
    ordered_membership_ids: Vec<String>,
    expected_revision: u64,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct ListDeviceSessionsPayload {
    version: u32,
    page: InitialCatalogPagePayload,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct RevokeDeviceSessionPayload {
    version: u32,
    session_id: String,
}

/// Represents the different types of commands the engine can process.
#[derive(Clone, Debug, PartialEq)]
pub enum EngineCommandType {
    /// Initial setup command to prepare the engine.
    Bootstrap,
    /// Resumes or starts playback.
    Play,
    /// Pauses the current playback.
    Pause,
    /// Skips to the previous track or item.
    SkipPrevious,
    /// Skips to the next track or item.
    SkipNext,
    /// Starts a new media session.
    StartSession {
        user_id: String,
    },
    /// Ends the current media session.
    EndSession,
    /// Refreshes the public backend health projection.
    RefreshBackendStatus,
    /// Searches a unary catalog page.
    SearchCatalog {
        query: String,
        page: EnginePageRequest,
    },
    /// Browses a unary catalog page.
    BrowseCatalog {
        parent_id: Option<String>,
        genres: Vec<String>,
        page: EnginePageRequest,
    },
    /// Continues a previously dispatched catalog operation.
    LoadNextCatalogPage {
        operation_id: String,
    },
    /// Loads the first authenticated discovery-feed page.
    LoadDiscoveryFeed {
        excluded_track_ids: Vec<String>,
        page: EnginePageRequest,
    },
    /// Continues a previously dispatched discovery-feed operation.
    LoadNextDiscoveryPage,
    /// Loads server-backed history consent for the current account and session.
    LoadHistorySettings,
    /// Updates server-backed history consent.
    UpdateHistorySettings {
        enabled: bool,
    },
    /// Loads the first playback-history page.
    ListHistory {
        page: EnginePageRequest,
    },
    /// Continues the current playback-history page operation.
    LoadNextHistoryPage,
    /// Deletes one playback-history entry.
    DeleteHistoryEntry {
        history_id: String,
    },
    /// Clears all playback history for the current account.
    ClearHistory,
    SaveTrack {
        track_id: String,
    },
    RemoveSavedTrack {
        track_id: String,
    },
    ListSavedTracks {
        page: EnginePageRequest,
    },
    LoadNextSavedTracksPage,
    LikeTrack {
        track_id: String,
    },
    UnlikeTrack {
        track_id: String,
    },
    ListLikedTracks {
        page: EnginePageRequest,
    },
    LoadNextLikedTracksPage,
    CreatePlaylist {
        input: crate::EngineCreatePlaylist,
    },
    UpdatePlaylist {
        input: crate::EngineUpdatePlaylist,
    },
    DeletePlaylist {
        playlist_id: String,
    },
    ListPlaylists {
        page: EnginePageRequest,
    },
    LoadNextPlaylistsPage,
    AddPlaylistTrack {
        playlist_id: String,
        track_id: String,
    },
    RemovePlaylistTrack {
        playlist_id: String,
        track_id: String,
    },
    ListPlaylistTracks {
        playlist_id: String,
        page: EnginePageRequest,
    },
    LoadNextPlaylistTracksPage,
    ReorderPlaylistTracks {
        playlist_id: String,
        ordered_membership_ids: Vec<String>,
        expected_revision: u64,
    },
    GetAccount,
    DeleteAccount,
    ListDeviceSessions {
        page: EnginePageRequest,
    },
    LoadNextDeviceSessionsPage,
    RevokeDeviceSession {
        session_id: String,
    },
    /// Changes the playback speed.
    SetSpeed {
        speed: f32,
    },
    /// Seeks to a specific position in milliseconds.
    Seek {
        position_millis: u64,
    },
    /// Updates the engine's configuration.
    UpdateConfig {
        config: crate::model::config::EngineConfig,
    },
    /// Hydrates preferences from Android's durable local cache.
    HydrateThemePreference {
        theme: crate::model::preferences::ThemePreference,
    },
    /// Applies an explicit local user selection.
    SetThemePreference {
        theme: crate::model::preferences::ThemePreference,
    },
    /// Applies an authenticated profile preference if its session and revision are current.
    ApplyRemoteThemePreference {
        theme: crate::model::preferences::ThemePreference,
        user_id: String,
        baseline_revision: u64,
    },
    /// Creates or replaces the authenticated account profile.
    UpsertProfile {
        display_name: Option<String>,
    },
    /// Fetches the authenticated account profile.
    GetProfile,
    /// Applies a typed patch to the authenticated account profile.
    UpdateProfile {
        update: crate::model::profile::EngineProfileUpdate,
    },
    /// Deletes the authenticated account profile.
    DeleteProfile,
    /// Fetches authenticated profile preferences and projects known values into engine state.
    LoadProfilePreferences,
    /// Merges application-owned preference values into the full remote document.
    UpdateProfilePreferences {
        values: serde_json::Map<String, serde_json::Value>,
    },
    /// Voice-based search and play command.
    VoicePlay {
        query: String,
    },
    /// Start a new voice interaction (ASR/NLU).
    StartVoiceInteraction,
    /// Finalize and stop current voice interaction.
    StopVoiceInteraction,
    /// Process a chunk of audio for the current voice interaction.
    ProcessVoiceAudio {
        chunk: Vec<i16>,
    },
    /// Plays a specific media item by its ID.
    PlayMediaById {
        media_id: String,
    },
    /// Sets a sleep timer for a specific duration in milliseconds.
    SetSleepTimer {
        duration_millis: Option<u64>,
    },
    /// A command not recognized by this version of the engine.
    Unknown(String),
}

impl EngineCommandType {
    /// Wire value for Bootstrap command.
    pub const BOOTSTRAP_WIRE: &'static str = "bootstrap";
    /// Wire value for Play command.
    pub const PLAY_WIRE: &'static str = "play";
    /// Wire value for Pause command.
    pub const PAUSE_WIRE: &'static str = "pause";
    /// Wire value for SkipPrevious command.
    pub const SKIP_PREVIOUS_WIRE: &'static str = "skip_previous";
    /// Wire value for SkipNext command.
    pub const SKIP_NEXT_WIRE: &'static str = "skip_next";
    /// Wire value for StartSession command.
    pub const START_SESSION_WIRE: &'static str = "start_session";
    /// Wire value for EndSession command.
    pub const END_SESSION_WIRE: &'static str = "end_session";
    /// Wire value for RefreshBackendStatus command.
    pub const REFRESH_BACKEND_STATUS_WIRE: &'static str = "refresh_backend_status";
    /// Wire value for Search command.
    pub const SEARCH_WIRE: &'static str = "search";
    /// Wire value for Browse command.
    pub const BROWSE_WIRE: &'static str = "browse";
    /// Wire value for loading the next page of an active catalog operation.
    pub const LOAD_NEXT_CATALOG_PAGE_WIRE: &'static str = "load_next_catalog_page";
    /// Wire value for loading the first discovery-feed page.
    pub const LOAD_DISCOVERY_FEED_WIRE: &'static str = "load_discovery_feed";
    /// Wire value for loading the next discovery-feed page.
    pub const LOAD_NEXT_DISCOVERY_PAGE_WIRE: &'static str = "load_next_discovery_page";
    pub const LOAD_HISTORY_SETTINGS_WIRE: &'static str = "load_history_settings";
    pub const UPDATE_HISTORY_SETTINGS_WIRE: &'static str = "update_history_settings";
    pub const LIST_HISTORY_WIRE: &'static str = "list_history";
    pub const LOAD_NEXT_HISTORY_PAGE_WIRE: &'static str = "load_next_history_page";
    pub const DELETE_HISTORY_ENTRY_WIRE: &'static str = "delete_history_entry";
    pub const CLEAR_HISTORY_WIRE: &'static str = "clear_history";
    pub const SAVE_TRACK_WIRE: &'static str = "save_track";
    pub const REMOVE_SAVED_TRACK_WIRE: &'static str = "remove_saved_track";
    pub const LIST_SAVED_TRACKS_WIRE: &'static str = "list_saved_tracks";
    pub const LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE: &'static str = "load_next_saved_tracks_page";
    pub const LIKE_TRACK_WIRE: &'static str = "like_track";
    pub const UNLIKE_TRACK_WIRE: &'static str = "unlike_track";
    pub const LIST_LIKED_TRACKS_WIRE: &'static str = "list_liked_tracks";
    pub const LOAD_NEXT_LIKED_TRACKS_PAGE_WIRE: &'static str = "load_next_liked_tracks_page";
    pub const CREATE_PLAYLIST_WIRE: &'static str = "create_playlist";
    pub const UPDATE_PLAYLIST_WIRE: &'static str = "update_playlist";
    pub const DELETE_PLAYLIST_WIRE: &'static str = "delete_playlist";
    pub const LIST_PLAYLISTS_WIRE: &'static str = "list_playlists";
    pub const LOAD_NEXT_PLAYLISTS_PAGE_WIRE: &'static str = "load_next_playlists_page";
    pub const ADD_PLAYLIST_TRACK_WIRE: &'static str = "add_playlist_track";
    pub const REMOVE_PLAYLIST_TRACK_WIRE: &'static str = "remove_playlist_track";
    pub const LIST_PLAYLIST_TRACKS_WIRE: &'static str = "list_playlist_tracks";
    pub const LOAD_NEXT_PLAYLIST_TRACKS_PAGE_WIRE: &'static str = "load_next_playlist_tracks_page";
    pub const REORDER_PLAYLIST_TRACKS_WIRE: &'static str = "reorder_playlist_tracks";
    pub const GET_ACCOUNT_WIRE: &'static str = "get_account";
    pub const DELETE_ACCOUNT_WIRE: &'static str = "delete_account";
    pub const LIST_DEVICE_SESSIONS_WIRE: &'static str = "list_device_sessions";
    pub const LOAD_NEXT_DEVICE_SESSIONS_PAGE_WIRE: &'static str = "load_next_device_sessions_page";
    pub const REVOKE_DEVICE_SESSION_WIRE: &'static str = "revoke_device_session";
    /// Wire value for SetSpeed command.
    pub const SET_SPEED_WIRE: &'static str = "set_speed";
    /// Wire value for Seek command.
    pub const SEEK_WIRE: &'static str = "seek";
    /// Wire value for UpdateConfig command.
    pub const UPDATE_CONFIG_WIRE: &'static str = "update_config";
    /// Wire value for HydrateThemePreference command.
    pub const HYDRATE_THEME_PREFERENCE_WIRE: &'static str = "hydrate_theme_preference";
    /// Wire value for SetThemePreference command.
    pub const SET_THEME_PREFERENCE_WIRE: &'static str = "set_theme_preference";
    /// Wire value for ApplyRemoteThemePreference command.
    pub const APPLY_REMOTE_THEME_PREFERENCE_WIRE: &'static str = "apply_remote_theme_preference";
    /// Wire value for creating or replacing the authenticated profile.
    pub const UPSERT_PROFILE_WIRE: &'static str = "upsert_profile";
    /// Wire value for loading the authenticated profile.
    pub const GET_PROFILE_WIRE: &'static str = "get_profile";
    /// Wire value for applying a typed profile patch.
    pub const UPDATE_PROFILE_WIRE: &'static str = "update_profile";
    /// Wire value for deleting the authenticated profile.
    pub const DELETE_PROFILE_WIRE: &'static str = "delete_profile";
    /// Wire value for loading authenticated profile preferences.
    pub const LOAD_PROFILE_PREFERENCES_WIRE: &'static str = "load_profile_preferences";
    /// Wire value for merging authenticated profile preferences.
    pub const UPDATE_PROFILE_PREFERENCES_WIRE: &'static str = "update_profile_preferences";
    /// Wire value for VoicePlay command.
    pub const VOICE_PLAY_WIRE: &'static str = "voice_play";
    /// Wire value for StartVoiceInteraction command.
    pub const START_VOICE_INTERACTION_WIRE: &'static str = "start_voice_interaction";
    /// Wire value for StopVoiceInteraction command.
    pub const STOP_VOICE_INTERACTION_WIRE: &'static str = "stop_voice_interaction";
    /// Wire value for ProcessVoiceAudio command.
    pub const PROCESS_VOICE_AUDIO_WIRE: &'static str = "process_voice_audio";
    /// Wire value for PlayMediaById command.
    pub const PLAY_MEDIA_BY_ID_WIRE: &'static str = "play_media_by_id";
    /// Wire value for SetSleepTimer command.
    pub const SET_SLEEP_TIMER_WIRE: &'static str = "set_sleep_timer";

    /// Maps a wire string value to its corresponding enum variant.
    pub fn from_wire(value: impl Into<String>) -> Self {
        let value = value.into();
        match value.as_str() {
            Self::BOOTSTRAP_WIRE => Self::Bootstrap,
            Self::PLAY_WIRE => Self::Play,
            Self::PAUSE_WIRE => Self::Pause,
            Self::SKIP_PREVIOUS_WIRE => Self::SkipPrevious,
            Self::SKIP_NEXT_WIRE => Self::SkipNext,
            Self::START_SESSION_WIRE => Self::StartSession {
                user_id: "unknown".to_string(),
            },
            Self::END_SESSION_WIRE => Self::EndSession,
            Self::REFRESH_BACKEND_STATUS_WIRE => Self::RefreshBackendStatus,
            Self::SEARCH_WIRE => Self::SearchCatalog {
                query: String::new(),
                page: EnginePageRequest::default(),
            },
            Self::BROWSE_WIRE => Self::BrowseCatalog {
                parent_id: None,
                genres: Vec::new(),
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_CATALOG_PAGE_WIRE => Self::LoadNextCatalogPage {
                operation_id: String::new(),
            },
            Self::LOAD_DISCOVERY_FEED_WIRE => Self::LoadDiscoveryFeed {
                excluded_track_ids: Vec::new(),
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_DISCOVERY_PAGE_WIRE => Self::LoadNextDiscoveryPage,
            Self::LOAD_HISTORY_SETTINGS_WIRE => Self::LoadHistorySettings,
            Self::UPDATE_HISTORY_SETTINGS_WIRE => Self::UpdateHistorySettings { enabled: false },
            Self::LIST_HISTORY_WIRE => Self::ListHistory {
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_HISTORY_PAGE_WIRE => Self::LoadNextHistoryPage,
            Self::DELETE_HISTORY_ENTRY_WIRE => Self::DeleteHistoryEntry {
                history_id: String::new(),
            },
            Self::CLEAR_HISTORY_WIRE => Self::ClearHistory,
            Self::SAVE_TRACK_WIRE => Self::SaveTrack {
                track_id: String::new(),
            },
            Self::REMOVE_SAVED_TRACK_WIRE => Self::RemoveSavedTrack {
                track_id: String::new(),
            },
            Self::LIST_SAVED_TRACKS_WIRE => Self::ListSavedTracks {
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE => Self::LoadNextSavedTracksPage,
            Self::LIKE_TRACK_WIRE => Self::LikeTrack {
                track_id: String::new(),
            },
            Self::UNLIKE_TRACK_WIRE => Self::UnlikeTrack {
                track_id: String::new(),
            },
            Self::LIST_LIKED_TRACKS_WIRE => Self::ListLikedTracks {
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_LIKED_TRACKS_PAGE_WIRE => Self::LoadNextLikedTracksPage,
            Self::CREATE_PLAYLIST_WIRE => Self::CreatePlaylist {
                input: crate::EngineCreatePlaylist {
                    name: String::new(),
                    description: None,
                },
            },
            Self::UPDATE_PLAYLIST_WIRE => Self::UpdatePlaylist {
                input: crate::EngineUpdatePlaylist {
                    id: String::new(),
                    name: String::new(),
                    description: None,
                    expected_revision: 0,
                },
            },
            Self::DELETE_PLAYLIST_WIRE => Self::DeletePlaylist {
                playlist_id: String::new(),
            },
            Self::LIST_PLAYLISTS_WIRE => Self::ListPlaylists {
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_PLAYLISTS_PAGE_WIRE => Self::LoadNextPlaylistsPage,
            Self::ADD_PLAYLIST_TRACK_WIRE => Self::AddPlaylistTrack {
                playlist_id: String::new(),
                track_id: String::new(),
            },
            Self::REMOVE_PLAYLIST_TRACK_WIRE => Self::RemovePlaylistTrack {
                playlist_id: String::new(),
                track_id: String::new(),
            },
            Self::LIST_PLAYLIST_TRACKS_WIRE => Self::ListPlaylistTracks {
                playlist_id: String::new(),
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_PLAYLIST_TRACKS_PAGE_WIRE => Self::LoadNextPlaylistTracksPage,
            Self::REORDER_PLAYLIST_TRACKS_WIRE => Self::ReorderPlaylistTracks {
                playlist_id: String::new(),
                ordered_membership_ids: vec![],
                expected_revision: 0,
            },
            Self::GET_ACCOUNT_WIRE => Self::GetAccount,
            Self::DELETE_ACCOUNT_WIRE => Self::DeleteAccount,
            Self::LIST_DEVICE_SESSIONS_WIRE => Self::ListDeviceSessions {
                page: EnginePageRequest::default(),
            },
            Self::LOAD_NEXT_DEVICE_SESSIONS_PAGE_WIRE => Self::LoadNextDeviceSessionsPage,
            Self::REVOKE_DEVICE_SESSION_WIRE => Self::RevokeDeviceSession {
                session_id: String::new(),
            },
            Self::SET_SPEED_WIRE => Self::SetSpeed { speed: 1.0 },
            Self::SEEK_WIRE => Self::Seek { position_millis: 0 },
            Self::UPDATE_CONFIG_WIRE => Self::UpdateConfig {
                config: crate::model::config::EngineConfig::default(),
            },
            Self::HYDRATE_THEME_PREFERENCE_WIRE => Self::HydrateThemePreference {
                theme: crate::model::preferences::ThemePreference::SystemDefault,
            },
            Self::SET_THEME_PREFERENCE_WIRE => Self::SetThemePreference {
                theme: crate::model::preferences::ThemePreference::SystemDefault,
            },
            Self::APPLY_REMOTE_THEME_PREFERENCE_WIRE => Self::ApplyRemoteThemePreference {
                theme: crate::model::preferences::ThemePreference::SystemDefault,
                user_id: String::new(),
                baseline_revision: 0,
            },
            Self::UPSERT_PROFILE_WIRE => Self::UpsertProfile { display_name: None },
            Self::GET_PROFILE_WIRE => Self::GetProfile,
            Self::UPDATE_PROFILE_WIRE => Self::UpdateProfile {
                update: crate::model::profile::EngineProfileUpdate::default(),
            },
            Self::DELETE_PROFILE_WIRE => Self::DeleteProfile,
            Self::LOAD_PROFILE_PREFERENCES_WIRE => Self::LoadProfilePreferences,
            Self::UPDATE_PROFILE_PREFERENCES_WIRE => Self::UpdateProfilePreferences {
                values: serde_json::Map::new(),
            },
            Self::VOICE_PLAY_WIRE => Self::VoicePlay {
                query: "".to_string(),
            },
            Self::START_VOICE_INTERACTION_WIRE => Self::StartVoiceInteraction,
            Self::STOP_VOICE_INTERACTION_WIRE => Self::StopVoiceInteraction,
            Self::PROCESS_VOICE_AUDIO_WIRE => Self::ProcessVoiceAudio { chunk: vec![] },
            Self::PLAY_MEDIA_BY_ID_WIRE => Self::PlayMediaById {
                media_id: "".to_string(),
            },
            Self::SET_SLEEP_TIMER_WIRE => Self::SetSleepTimer {
                duration_millis: None,
            },
            _ => Self::Unknown(value),
        }
    }

    /// Returns the wire string representation of the command type.
    pub fn as_wire(&self) -> &str {
        match self {
            Self::Bootstrap => Self::BOOTSTRAP_WIRE,
            Self::Play => Self::PLAY_WIRE,
            Self::Pause => Self::PAUSE_WIRE,
            Self::SkipPrevious => Self::SKIP_PREVIOUS_WIRE,
            Self::SkipNext => Self::SKIP_NEXT_WIRE,
            Self::StartSession { .. } => Self::START_SESSION_WIRE,
            Self::EndSession => Self::END_SESSION_WIRE,
            Self::RefreshBackendStatus => Self::REFRESH_BACKEND_STATUS_WIRE,
            Self::SearchCatalog { .. } => Self::SEARCH_WIRE,
            Self::BrowseCatalog { .. } => Self::BROWSE_WIRE,
            Self::LoadNextCatalogPage { .. } => Self::LOAD_NEXT_CATALOG_PAGE_WIRE,
            Self::LoadDiscoveryFeed { .. } => Self::LOAD_DISCOVERY_FEED_WIRE,
            Self::LoadNextDiscoveryPage => Self::LOAD_NEXT_DISCOVERY_PAGE_WIRE,
            Self::LoadHistorySettings => Self::LOAD_HISTORY_SETTINGS_WIRE,
            Self::UpdateHistorySettings { .. } => Self::UPDATE_HISTORY_SETTINGS_WIRE,
            Self::ListHistory { .. } => Self::LIST_HISTORY_WIRE,
            Self::LoadNextHistoryPage => Self::LOAD_NEXT_HISTORY_PAGE_WIRE,
            Self::DeleteHistoryEntry { .. } => Self::DELETE_HISTORY_ENTRY_WIRE,
            Self::ClearHistory => Self::CLEAR_HISTORY_WIRE,
            Self::SaveTrack { .. } => Self::SAVE_TRACK_WIRE,
            Self::RemoveSavedTrack { .. } => Self::REMOVE_SAVED_TRACK_WIRE,
            Self::ListSavedTracks { .. } => Self::LIST_SAVED_TRACKS_WIRE,
            Self::LoadNextSavedTracksPage => Self::LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE,
            Self::LikeTrack { .. } => Self::LIKE_TRACK_WIRE,
            Self::UnlikeTrack { .. } => Self::UNLIKE_TRACK_WIRE,
            Self::ListLikedTracks { .. } => Self::LIST_LIKED_TRACKS_WIRE,
            Self::LoadNextLikedTracksPage => Self::LOAD_NEXT_LIKED_TRACKS_PAGE_WIRE,
            Self::CreatePlaylist { .. } => Self::CREATE_PLAYLIST_WIRE,
            Self::UpdatePlaylist { .. } => Self::UPDATE_PLAYLIST_WIRE,
            Self::DeletePlaylist { .. } => Self::DELETE_PLAYLIST_WIRE,
            Self::ListPlaylists { .. } => Self::LIST_PLAYLISTS_WIRE,
            Self::LoadNextPlaylistsPage => Self::LOAD_NEXT_PLAYLISTS_PAGE_WIRE,
            Self::AddPlaylistTrack { .. } => Self::ADD_PLAYLIST_TRACK_WIRE,
            Self::RemovePlaylistTrack { .. } => Self::REMOVE_PLAYLIST_TRACK_WIRE,
            Self::ListPlaylistTracks { .. } => Self::LIST_PLAYLIST_TRACKS_WIRE,
            Self::LoadNextPlaylistTracksPage => Self::LOAD_NEXT_PLAYLIST_TRACKS_PAGE_WIRE,
            Self::ReorderPlaylistTracks { .. } => Self::REORDER_PLAYLIST_TRACKS_WIRE,
            Self::GetAccount => Self::GET_ACCOUNT_WIRE,
            Self::DeleteAccount => Self::DELETE_ACCOUNT_WIRE,
            Self::ListDeviceSessions { .. } => Self::LIST_DEVICE_SESSIONS_WIRE,
            Self::LoadNextDeviceSessionsPage => Self::LOAD_NEXT_DEVICE_SESSIONS_PAGE_WIRE,
            Self::RevokeDeviceSession { .. } => Self::REVOKE_DEVICE_SESSION_WIRE,
            Self::SetSpeed { .. } => Self::SET_SPEED_WIRE,
            Self::Seek { .. } => Self::SEEK_WIRE,
            Self::UpdateConfig { .. } => Self::UPDATE_CONFIG_WIRE,
            Self::HydrateThemePreference { .. } => Self::HYDRATE_THEME_PREFERENCE_WIRE,
            Self::SetThemePreference { .. } => Self::SET_THEME_PREFERENCE_WIRE,
            Self::ApplyRemoteThemePreference { .. } => Self::APPLY_REMOTE_THEME_PREFERENCE_WIRE,
            Self::UpsertProfile { .. } => Self::UPSERT_PROFILE_WIRE,
            Self::GetProfile => Self::GET_PROFILE_WIRE,
            Self::UpdateProfile { .. } => Self::UPDATE_PROFILE_WIRE,
            Self::DeleteProfile => Self::DELETE_PROFILE_WIRE,
            Self::LoadProfilePreferences => Self::LOAD_PROFILE_PREFERENCES_WIRE,
            Self::UpdateProfilePreferences { .. } => Self::UPDATE_PROFILE_PREFERENCES_WIRE,
            Self::VoicePlay { .. } => Self::VOICE_PLAY_WIRE,
            Self::StartVoiceInteraction => Self::START_VOICE_INTERACTION_WIRE,
            Self::StopVoiceInteraction => Self::STOP_VOICE_INTERACTION_WIRE,
            Self::ProcessVoiceAudio { .. } => Self::PROCESS_VOICE_AUDIO_WIRE,
            Self::PlayMediaById { .. } => Self::PLAY_MEDIA_BY_ID_WIRE,
            Self::SetSleepTimer { .. } => Self::SET_SLEEP_TIMER_WIRE,
            Self::Unknown(value) => value.as_str(),
        }
    }
}

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
            EngineCommandType::LOAD_DISCOVERY_FEED_WIRE => {
                payload.as_deref().and_then(parse_discovery_feed_payload)
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

    /// Creates a SetSleepTimer command.
    pub fn set_sleep_timer(duration_millis: Option<u64>) -> Self {
        Self::new(EngineCommandType::SetSleepTimer { duration_millis }, None)
    }
}

fn parse_search_catalog_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: SearchCatalogPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.query.trim().is_empty()).then_some(
        EngineCommandType::SearchCatalog {
            query: payload.query,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}

fn parse_browse_catalog_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: BrowseCatalogPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::BrowseCatalog {
        parent_id: payload.parent_id,
        genres: payload.genres,
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}

fn parse_load_next_catalog_page_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: LoadNextCatalogPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.operation_id.trim().is_empty())
        .then_some(EngineCommandType::LoadNextCatalogPage {
            operation_id: payload.operation_id,
        })
}

fn parse_discovery_feed_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: DiscoveryFeedPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::LoadDiscoveryFeed {
        excluded_track_ids: payload.exclude_track_ids,
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}

fn parse_load_next_discovery_page_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: LoadNextDiscoveryPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::LoadNextDiscoveryPage)
}

fn parse_update_history_settings_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: UpdateHistorySettingsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(
        EngineCommandType::UpdateHistorySettings {
            enabled: payload.enabled,
        },
    )
}

fn parse_list_history_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: ListHistoryPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::ListHistory {
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}
fn parse_delete_history_entry_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: DeleteHistoryEntryPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.history_id.trim().is_empty()).then_some(
        EngineCommandType::DeleteHistoryEntry {
            history_id: payload.history_id,
        },
    )
}

fn parse_track_relationship_payload(
    command_type: &str,
    payload: &str,
) -> Option<EngineCommandType> {
    let payload: TrackRelationshipPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION || payload.track_id.trim().is_empty() {
        return None;
    }
    match command_type {
        EngineCommandType::SAVE_TRACK_WIRE => Some(EngineCommandType::SaveTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::REMOVE_SAVED_TRACK_WIRE => Some(EngineCommandType::RemoveSavedTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::LIKE_TRACK_WIRE => Some(EngineCommandType::LikeTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::UNLIKE_TRACK_WIRE => Some(EngineCommandType::UnlikeTrack {
            track_id: payload.track_id,
        }),
        _ => None,
    }
}

fn parse_list_library_payload(command_type: &str, payload: &str) -> Option<EngineCommandType> {
    let payload: ListLibraryPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION {
        return None;
    }
    let page = EnginePageRequest {
        page_size: payload.page.page_size,
        page_token: None,
    };
    match command_type {
        EngineCommandType::LIST_SAVED_TRACKS_WIRE => {
            Some(EngineCommandType::ListSavedTracks { page })
        }
        EngineCommandType::LIST_LIKED_TRACKS_WIRE => {
            Some(EngineCommandType::ListLikedTracks { page })
        }
        _ => None,
    }
}

fn parse_create_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistDetailsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && payload.playlist_id.is_none()
        && payload.expected_revision.is_none()
        && !payload.name.trim().is_empty())
    .then_some(EngineCommandType::CreatePlaylist {
        input: crate::EngineCreatePlaylist {
            name: payload.name,
            description: payload.description,
        },
    })
}
fn parse_update_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistDetailsPayload = serde_json::from_str(payload).ok()?;
    let id = payload.playlist_id?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && !id.trim().is_empty()
        && !payload.name.trim().is_empty())
    .then_some(EngineCommandType::UpdatePlaylist {
        input: crate::EngineUpdatePlaylist {
            id,
            name: payload.name,
            description: payload.description,
            expected_revision: payload.expected_revision?,
        },
    })
}
fn parse_delete_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.playlist_id.trim().is_empty())
        .then_some(EngineCommandType::DeletePlaylist {
            playlist_id: payload.playlist_id,
        })
}
fn parse_list_playlists_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && payload.playlist_id.is_none()).then_some(
        EngineCommandType::ListPlaylists {
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}
fn parse_list_playlist_tracks_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPagePayload = serde_json::from_str(payload).ok()?;
    let playlist_id = payload.playlist_id?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !playlist_id.trim().is_empty()).then_some(
        EngineCommandType::ListPlaylistTracks {
            playlist_id,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}
fn parse_playlist_track_payload(command_type: &str, payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistTrackPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION
        || payload.playlist_id.trim().is_empty()
        || payload.track_id.trim().is_empty()
    {
        return None;
    }
    match command_type {
        EngineCommandType::ADD_PLAYLIST_TRACK_WIRE => Some(EngineCommandType::AddPlaylistTrack {
            playlist_id: payload.playlist_id,
            track_id: payload.track_id,
        }),
        EngineCommandType::REMOVE_PLAYLIST_TRACK_WIRE => {
            Some(EngineCommandType::RemovePlaylistTrack {
                playlist_id: payload.playlist_id,
                track_id: payload.track_id,
            })
        }
        _ => None,
    }
}
fn parse_reorder_playlist_tracks_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistReorderPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && !payload.playlist_id.trim().is_empty()
        && !payload.ordered_membership_ids.is_empty()
        && payload
            .ordered_membership_ids
            .iter()
            .all(|id| !id.trim().is_empty()))
    .then_some(EngineCommandType::ReorderPlaylistTracks {
        playlist_id: payload.playlist_id,
        ordered_membership_ids: payload.ordered_membership_ids,
        expected_revision: payload.expected_revision,
    })
}

fn parse_list_device_sessions_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: ListDeviceSessionsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::ListDeviceSessions {
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}

fn parse_revoke_device_session_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: RevokeDeviceSessionPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.session_id.trim().is_empty()).then_some(
        EngineCommandType::RevokeDeviceSession {
            session_id: payload.session_id,
        },
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn from_wire_unknown_maps_to_unknown_variant() {
        let command_type = EngineCommandType::from_wire("totally_unknown_command");
        assert_eq!(
            command_type,
            EngineCommandType::Unknown("totally_unknown_command".to_string())
        );
        assert_eq!(command_type.as_wire(), "totally_unknown_command");
    }

    #[test]
    fn create_playlist_rejects_an_expected_revision() {
        let command = EngineCommand::from_wire(
            EngineCommandType::CREATE_PLAYLIST_WIRE,
            Some(
                r#"{"version":1,"playlist_id":null,"name":"Mix","description":null,"expected_revision":7}"#
                    .into(),
            ),
        );

        assert!(matches!(
            command.command_type,
            EngineCommandType::Unknown(_)
        ));
    }

    #[test]
    fn from_wire_known_commands_use_safe_defaults() {
        assert_eq!(
            EngineCommandType::from_wire(EngineCommandType::START_SESSION_WIRE),
            EngineCommandType::StartSession {
                user_id: "unknown".to_string()
            }
        );
        assert_eq!(
            EngineCommandType::from_wire(EngineCommandType::SEARCH_WIRE),
            EngineCommandType::SearchCatalog {
                query: String::new(),
                page: EnginePageRequest::default(),
            }
        );
        assert_eq!(
            EngineCommandType::from_wire(EngineCommandType::BROWSE_WIRE),
            EngineCommandType::BrowseCatalog {
                parent_id: None,
                genres: Vec::new(),
                page: EnginePageRequest::default(),
            }
        );
    }

    #[test]
    fn engine_command_from_wire_preserves_payload() {
        let command = EngineCommand::from_wire("invalid_wire", Some("payload".to_string()));
        assert_eq!(
            command.command_type,
            EngineCommandType::Unknown("invalid_wire".to_string())
        );
        assert_eq!(command.payload.as_deref(), Some("payload"));
    }

    #[test]
    fn search_catalog_decodes_versioned_json_page_payload() {
        let payload = r#"{"version":1,"query":"jazz","page":{"page_size":25}}"#;

        let command = EngineCommand::from_wire("search", Some(payload.into()));

        assert_eq!(
            command.command_type,
            EngineCommandType::SearchCatalog {
                query: "jazz".into(),
                page: crate::EnginePageRequest {
                    page_size: 25,
                    page_token: None,
                },
            }
        );
    }

    #[test]
    fn initial_catalog_payload_rejects_page_token() {
        let payload =
            r#"{"version":1,"query":"jazz","page":{"page_size":25,"page_token":"opaque+/="}}"#;
        let command = EngineCommand::from_wire("search", Some(payload.into()));

        assert_eq!(
            command.command_type,
            EngineCommandType::Unknown("invalid_search_payload".into())
        );

        let browse_payload = r#"{"version":1,"parent_id":"root","genres":[],"page":{"page_size":25,"page_token":null}}"#;
        let browse = EngineCommand::from_wire("browse", Some(browse_payload.into()));
        assert_eq!(
            browse.command_type,
            EngineCommandType::Unknown("invalid_browse_payload".into())
        );
    }

    #[test]
    fn browse_catalog_decodes_versioned_json_filters() {
        let payload =
            r#"{"version":1,"parent_id":null,"genres":["jazz","fusion"],"page":{"page_size":10}}"#;

        let command = EngineCommand::from_wire("browse", Some(payload.into()));

        assert_eq!(
            command.command_type,
            EngineCommandType::BrowseCatalog {
                parent_id: None,
                genres: vec!["jazz".into(), "fusion".into()],
                page: crate::EnginePageRequest {
                    page_size: 10,
                    page_token: None,
                },
            }
        );
    }

    #[test]
    fn next_catalog_page_decodes_operation_id_from_json() {
        let payload = r#"{"version":1,"operation_id":"catalog-42"}"#;

        let command = EngineCommand::from_wire("load_next_catalog_page", Some(payload.into()));

        assert_eq!(
            command.command_type,
            EngineCommandType::LoadNextCatalogPage {
                operation_id: "catalog-42".into(),
            }
        );
    }
    #[test]
    fn discovery_commands_decode_versioned_engine_owned_pagination_payloads() {
        let load = EngineCommand::from_wire(
            "load_discovery_feed",
            Some(
                r#"{"version":1,"exclude_track_ids":["played-1"],"page":{"page_size":25}}"#.into(),
            ),
        );
        assert_eq!(
            load.command_type,
            EngineCommandType::LoadDiscoveryFeed {
                excluded_track_ids: vec!["played-1".into()],
                page: EnginePageRequest {
                    page_size: 25,
                    page_token: None,
                },
            }
        );

        let next =
            EngineCommand::from_wire("load_next_discovery_page", Some(r#"{"version":1}"#.into()));
        assert_eq!(next.command_type, EngineCommandType::LoadNextDiscoveryPage);
    }

    #[test]
    fn initial_discovery_payload_rejects_external_page_token() {
        let payload =
            r#"{"version":1,"exclude_track_ids":[],"page":{"page_size":25,"page_token":"opaque"}}"#;
        let command = EngineCommand::from_wire("load_discovery_feed", Some(payload.into()));

        assert_eq!(
            command.command_type,
            EngineCommandType::Unknown("invalid_load_discovery_feed_payload".into())
        );
    }
}
