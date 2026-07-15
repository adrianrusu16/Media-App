use serde::{Deserialize, Serialize};

/// Backend-neutral artist projection.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineArtist {
    pub id: String,
    pub name: String,
}

/// Backend-neutral album projection.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineAlbum {
    pub id: String,
    pub title: String,
}

/// Backend-neutral catalog track.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineTrack {
    pub id: String,
    pub title: String,
    pub artist: EngineArtist,
    pub album: Option<EngineAlbum>,
    pub duration_millis: u64,
    pub explicit: bool,
    pub artwork_id: Option<String>,
    pub genres: Vec<String>,
}
