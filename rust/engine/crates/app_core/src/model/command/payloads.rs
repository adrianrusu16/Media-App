use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct InitialCatalogPagePayload {
    pub(super) page_size: u32,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct SearchCatalogPayload {
    pub(super) version: u32,
    pub(super) query: String,
    pub(super) page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct BrowseCatalogPayload {
    pub(super) version: u32,
    pub(super) parent_id: Option<String>,
    #[serde(default)]
    pub(super) genres: Vec<String>,
    pub(super) page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct LoadNextCatalogPagePayload {
    pub(super) version: u32,
    pub(super) operation_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct DiscoveryFeedPayload {
    pub(super) version: u32,
    #[serde(default)]
    pub(super) exclude_track_ids: Vec<String>,
    pub(super) page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct LoadNextDiscoveryPagePayload {
    pub(super) version: u32,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct UpdateHistorySettingsPayload {
    pub(super) version: u32,
    pub(super) enabled: bool,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct ListHistoryPayload {
    pub(super) version: u32,
    pub(super) page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct TrackRelationshipPayload {
    pub(super) version: u32,
    pub(super) track_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct ListLibraryPayload {
    pub(super) version: u32,
    pub(super) page: InitialCatalogPagePayload,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct DeleteHistoryEntryPayload {
    pub(super) version: u32,
    pub(super) history_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlaylistPayload {
    pub(super) version: u32,
    pub(super) playlist_id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlaylistPagePayload {
    pub(super) version: u32,
    pub(super) playlist_id: Option<String>,
    pub(super) page: InitialCatalogPagePayload,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlaylistDetailsPayload {
    pub(super) version: u32,
    pub(super) playlist_id: Option<String>,
    pub(super) name: String,
    pub(super) description: Option<String>,
    pub(super) expected_revision: Option<u64>,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlaylistTrackPayload {
    pub(super) version: u32,
    pub(super) playlist_id: String,
    pub(super) track_id: String,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlaylistReorderPayload {
    pub(super) version: u32,
    pub(super) playlist_id: String,
    pub(super) ordered_membership_ids: Vec<String>,
    pub(super) expected_revision: u64,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct ListDeviceSessionsPayload {
    pub(super) version: u32,
    pub(super) page: InitialCatalogPagePayload,
}
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct RevokeDeviceSessionPayload {
    pub(super) version: u32,
    pub(super) session_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct PlayQueuePayload {
    pub(super) version: u32,
    pub(super) media_ids: Vec<String>,
    pub(super) start_index: usize,
}
