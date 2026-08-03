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
    StartSession { user_id: String },
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
    LoadNextCatalogPage { operation_id: String },
    /// Loads the first authenticated discovery-feed page.
    LoadDiscoveryFeed {
        excluded_track_ids: Vec<String>,
        page: EnginePageRequest,
    },
    /// Continues a previously dispatched discovery-feed operation.
    LoadNextDiscoveryPage,
    /// Changes the playback speed.
    SetSpeed { speed: f32 },
    /// Seeks to a specific position in milliseconds.
    Seek { position_millis: u64 },
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
    /// Voice-based search and play command.
    VoicePlay { query: String },
    /// Start a new voice interaction (ASR/NLU).
    StartVoiceInteraction,
    /// Finalize and stop current voice interaction.
    StopVoiceInteraction,
    /// Process a chunk of audio for the current voice interaction.
    ProcessVoiceAudio { chunk: Vec<i16> },
    /// Plays a specific media item by its ID.
    PlayMediaById { media_id: String },
    /// Sets a sleep timer for a specific duration in milliseconds.
    SetSleepTimer { duration_millis: Option<u64> },
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
            Self::SetSpeed { .. } => Self::SET_SPEED_WIRE,
            Self::Seek { .. } => Self::SEEK_WIRE,
            Self::UpdateConfig { .. } => Self::UPDATE_CONFIG_WIRE,
            Self::HydrateThemePreference { .. } => Self::HYDRATE_THEME_PREFERENCE_WIRE,
            Self::SetThemePreference { .. } => Self::SET_THEME_PREFERENCE_WIRE,
            Self::ApplyRemoteThemePreference { .. } => Self::APPLY_REMOTE_THEME_PREFERENCE_WIRE,
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
