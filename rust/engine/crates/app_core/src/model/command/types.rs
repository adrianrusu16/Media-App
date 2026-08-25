use crate::EnginePageRequest;

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
    LoadForYouFeed {
        excluded_track_ids: Vec<String>,
        page: EnginePageRequest,
    },
    LoadRecommendations {
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
    /// Plays an immutable, ordered queue snapshot from the selected index.
    PlayQueue {
        media_ids: Vec<String>,
        start_index: usize,
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
    pub const LOAD_FOR_YOU_FEED_WIRE: &'static str = "load_for_you_feed";
    pub const LOAD_RECOMMENDATIONS_WIRE: &'static str = "load_recommendations";
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
    /// Wire value for PlayQueue command.
    pub const PLAY_QUEUE_WIRE: &'static str = "play_queue";
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
            Self::LOAD_FOR_YOU_FEED_WIRE => Self::LoadForYouFeed {
                excluded_track_ids: Vec::new(),
                page: EnginePageRequest::default(),
            },
            Self::LOAD_RECOMMENDATIONS_WIRE => Self::LoadRecommendations {
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
            Self::PLAY_QUEUE_WIRE => Self::PlayQueue {
                media_ids: Vec::new(),
                start_index: 0,
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
            Self::LoadForYouFeed { .. } => Self::LOAD_FOR_YOU_FEED_WIRE,
            Self::LoadRecommendations { .. } => Self::LOAD_RECOMMENDATIONS_WIRE,
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
            Self::PlayQueue { .. } => Self::PLAY_QUEUE_WIRE,
            Self::SetSleepTimer { .. } => Self::SET_SLEEP_TIMER_WIRE,
            Self::Unknown(value) => value.as_str(),
        }
    }
}
