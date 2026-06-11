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
    pub item_type: MediaItemType,
    pub parent_id: Option<String>,
}
