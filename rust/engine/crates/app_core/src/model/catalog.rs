use serde::{Deserialize, Serialize};
use url::Url;

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

/// Backend-neutral artwork reference.
///
/// Canopy `ArtworkRef` carries opaque `id` + `content_hash` only. A loadable
/// HTTP `uri` is derived by the client from its configured media origin
/// (for example `{stream_base_url}/artwork/{id}/{content_hash}`).
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineArtwork {
    pub id: String,
    pub content_hash: String,
    pub uri: Option<String>,
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
    pub artwork: Option<EngineArtwork>,
    pub genres: Vec<String>,
}

/// Builds a Canopy artwork HTTP URI from media origin + ArtworkRef fields.
///
/// Returns `None` when either field is empty. Does not treat `id` alone as a URL.
pub fn canopy_artwork_http_uri(media_origin: &Url, id: &str, content_hash: &str) -> Option<String> {
    if id.is_empty() || content_hash.is_empty() {
        return None;
    }
    media_origin
        .join(&format!("artwork/{id}/{content_hash}"))
        .ok()
        .map(|url| url.to_string())
}

/// Projects [`EngineArtwork`] into `(artwork_id, artwork_content_hash, thumbnail_url)`.
///
/// Empty id/hash strings become `None`. Absent artwork yields all `None`.
pub fn project_artwork_identity(
    artwork: Option<EngineArtwork>,
) -> (Option<String>, Option<String>, Option<String>) {
    match artwork {
        Some(artwork) => (
            Some(artwork.id).filter(|value| !value.is_empty()),
            Some(artwork.content_hash).filter(|value| !value.is_empty()),
            artwork.uri,
        ),
        None => (None, None, None),
    }
}
