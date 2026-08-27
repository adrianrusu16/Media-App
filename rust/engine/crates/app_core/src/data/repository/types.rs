use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum MediaItemType {
    #[default]
    Track,
    Artist,
    Album,
    Folder,
    Playlist,
    RadioStation,
}

/// Represents a media item in the system.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct MediaItem {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub duration_millis: Option<u64>,
    pub thumbnail_url: Option<String>,
    /// Opaque Canopy artwork id when known.
    #[serde(default)]
    pub artwork_id: Option<String>,
    /// Artwork content hash used as a cache/version key.
    #[serde(default)]
    pub artwork_content_hash: Option<String>,
    pub source_uri: Option<String>,
    pub mime_type: Option<String>,
    pub item_type: MediaItemType,
    pub parent_id: Option<String>,
}
