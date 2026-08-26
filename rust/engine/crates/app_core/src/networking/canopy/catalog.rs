use std::sync::Arc;

use tonic_014::Request;
use url::Url;

use crate::networking::backend_client::CatalogPort;
use crate::{
    EngineAlbum, EngineArtist, EngineArtwork, EngineError, EngineErrorType, EnginePageRequest,
    EnginePageToken, EnginePagedResult, EngineTrack, canopy_artwork_http_uri,
};

use super::CanopyChannel;
use super::operation::CanopyOperation;
use super::request::execute;
use super::sdk::clients::catalog_service_client::CatalogServiceClient;
use super::sdk::resources::{
    ArtworkRef, BrowseRequest, BrowseResponse, GetMediaRequest, PageInfo, PageRequest,
    SearchRequest, SearchResponse, Track, TrackSummary,
};
use super::session::SessionCoordinator;

/// Canonical unary Canopy catalog adapter.
#[derive(Clone)]
pub struct CanopyCatalogClient {
    client: CatalogServiceClient<super::sdk::runtime::transport::Channel>,
    session: Option<Arc<SessionCoordinator>>,
    media_origin: Url,
}

impl CanopyCatalogClient {
    pub fn new(channel: &CanopyChannel, media_origin: Url) -> Self {
        Self {
            client: CatalogServiceClient::new(channel.clone_inner()),
            session: None,
            media_origin,
        }
    }

    /// Composes catalog transport with Rust-owned Canopy session handling.
    pub fn with_session_coordinator(
        channel: &CanopyChannel,
        session: Arc<SessionCoordinator>,
        media_origin: Url,
    ) -> Self {
        Self {
            client: CatalogServiceClient::new(channel.clone_inner()),
            session: Some(session),
            media_origin,
        }
    }
}

#[async_trait::async_trait]
impl CatalogPort for CanopyCatalogClient {
    async fn browse(
        &self,
        parent_id: Option<&str>,
        genres: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
        let request = BrowseRequest {
            parent_id: parent_id.map(str::to_owned),
            genres: genres.to_vec(),
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute(
            self.session.as_deref(),
            CanopyOperation::Browse,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.browse(request).await }
            },
        )
        .await?
        .into_inner();
        map_browse_response(response, Some(&self.media_origin))
    }

    async fn search(
        &self,
        query: &str,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
        let request = SearchRequest {
            query: query.to_owned(),
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute(
            self.session.as_deref(),
            CanopyOperation::Search,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.search(request).await }
            },
        )
        .await?
        .into_inner();
        map_search_response(response, Some(&self.media_origin))
    }

    async fn get_media(&self, track_id: &str) -> Result<EngineTrack, EngineError> {
        let request = GetMediaRequest {
            track_id: track_id.to_owned(),
        };
        let client = self.client.clone();
        let response = execute(
            self.session.as_deref(),
            CanopyOperation::GetMedia,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.get_media(request).await }
            },
        )
        .await?
        .into_inner();
        map_track(response, Some(&self.media_origin))
    }
}

pub(super) fn map_page_request(page: EnginePageRequest) -> PageRequest {
    PageRequest {
        page_size: page.page_size,
        page_token: page
            .page_token
            .map(|token| token.as_str().to_owned())
            .unwrap_or_default(),
    }
}

fn map_search_response(
    response: SearchResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info, media_origin)
}

fn map_browse_response(
    response: BrowseResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info, media_origin)
}

pub(super) fn map_page(
    tracks: Vec<TrackSummary>,
    page_info: Option<PageInfo>,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    Ok(EnginePagedResult {
        items: tracks
            .into_iter()
            .map(|summary| map_track_summary(summary, Vec::new(), media_origin))
            .collect::<Result<_, _>>()?,
        next_page_token: map_page_token(page_info)?,
    })
}

fn map_page_token(page_info: Option<PageInfo>) -> Result<Option<EnginePageToken>, EngineError> {
    match page_info.map(|info| info.next_page_token) {
        Some(token) if !token.is_empty() => EnginePageToken::new(token).map(Some),
        _ => Ok(None),
    }
}

fn map_track(track: Track, media_origin: Option<&Url>) -> Result<EngineTrack, EngineError> {
    let summary = track
        .summary
        .ok_or_else(|| mapping_defect("catalog track missing summary"))?;
    map_track_summary(summary, track.genres, media_origin)
}

pub(super) fn map_track_summary(
    summary: TrackSummary,
    genres: Vec<String>,
    media_origin: Option<&Url>,
) -> Result<EngineTrack, EngineError> {
    if summary.id.is_empty() || summary.title.is_empty() {
        return Err(mapping_defect("catalog track missing required identity"));
    }
    let artist = summary
        .artist
        .ok_or_else(|| mapping_defect("catalog track missing artist"))?;
    if artist.name.is_empty() {
        return Err(mapping_defect("catalog track has invalid artist"));
    }

    Ok(EngineTrack {
        id: summary.id,
        title: summary.title,
        artist: EngineArtist {
            id: artist.id,
            name: artist.name,
        },
        album: summary.album.map(|album| EngineAlbum {
            id: album.id,
            title: album.title,
        }),
        duration_millis: summary.duration_ms,
        explicit: summary.explicit,
        artwork: summary
            .artwork
            .and_then(|artwork| map_artwork_ref(artwork, media_origin)),
        genres,
    })
}

/// Maps Canopy `ArtworkRef` into engine artwork.
///
/// Does not treat `id` alone as a loadable thumbnail URL. A URI is built only
/// when both `id` and `content_hash` are present and a media origin is supplied.
pub(super) fn map_artwork_ref(
    artwork: ArtworkRef,
    media_origin: Option<&Url>,
) -> Option<EngineArtwork> {
    if artwork.id.is_empty() {
        return None;
    }
    let uri = media_origin
        .and_then(|origin| canopy_artwork_http_uri(origin, &artwork.id, &artwork.content_hash));
    Some(EngineArtwork {
        id: artwork.id,
        content_hash: artwork.content_hash,
        uri,
    })
}

fn mapping_defect(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::MappingDefect, message, false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::canopy::sdk::resources::{
        ArtistSummary, ArtworkRef, PageInfo, SearchResponse, TrackSummary,
    };

    fn track_summary_fixture() -> TrackSummary {
        TrackSummary {
            id: "track-1".into(),
            title: "A Song".into(),
            artist: Some(ArtistSummary {
                id: "artist-1".into(),
                name: "An Artist".into(),
            }),
            album: None,
            duration_ms: 123_000,
            explicit: false,
            artwork: None,
        }
    }

    #[test]
    fn maps_search_page_and_preserves_token() {
        let response = SearchResponse {
            tracks: vec![track_summary_fixture()],
            page_info: Some(PageInfo {
                next_page_token: "opaque+/=".into(),
            }),
        };

        let page = map_search_response(response, None).unwrap();

        assert_eq!(page.items[0].id, "track-1");
        assert_eq!(page.next_page_token.unwrap().as_str(), "opaque+/=");
    }

    #[test]
    fn rejects_track_summary_without_required_artist() {
        let mut track = track_summary_fixture();
        track.artist = None;
        let response = SearchResponse {
            tracks: vec![track],
            page_info: None,
        };

        assert_eq!(
            map_search_response(response, None).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn maps_renderable_artist_when_embedded_id_is_empty() {
        let response = SearchResponse {
            tracks: vec![track_summary_fixture()],
            page_info: None,
        };
        let mut response = response;
        response.tracks[0].artist.as_mut().unwrap().id.clear();

        let page = map_search_response(response, None).unwrap();

        assert_eq!(page.items[0].artist.id, "");
        assert_eq!(page.items[0].artist.name, "An Artist");
    }

    #[test]
    fn maps_full_track_genres() {
        let track = Track {
            summary: Some(track_summary_fixture()),
            genres: vec!["jazz".into(), "fusion".into()],
        };

        let mapped = map_track(track, None).unwrap();

        assert_eq!(mapped.genres, ["jazz", "fusion"]);
    }

    #[test]
    fn empty_continuation_token_maps_to_none() {
        let response = SearchResponse {
            tracks: vec![],
            page_info: Some(PageInfo {
                next_page_token: String::new(),
            }),
        };

        assert_eq!(
            map_search_response(response, None).unwrap().next_page_token,
            None
        );
    }

    #[test]
    fn maps_artwork_id_and_content_hash_without_treating_id_as_url() {
        let mut summary = track_summary_fixture();
        summary.artwork = Some(ArtworkRef {
            id: "art-1".into(),
            content_hash: "abc123".into(),
        });

        let mapped = map_track_summary(summary, Vec::new(), None).unwrap();
        let artwork = mapped.artwork.unwrap();

        assert_eq!(artwork.id, "art-1");
        assert_eq!(artwork.content_hash, "abc123");
        assert_eq!(artwork.uri, None);
    }

    #[test]
    fn builds_artwork_uri_from_stream_base_when_both_fields_present() {
        let mut summary = track_summary_fixture();
        summary.artwork = Some(ArtworkRef {
            id: "art-1".into(),
            content_hash: "deadbeef".into(),
        });
        let origin = Url::parse("http://10.0.2.2:8080/").unwrap();

        let mapped = map_track_summary(summary, Vec::new(), Some(&origin)).unwrap();

        assert_eq!(
            mapped.artwork.unwrap().uri.as_deref(),
            Some("http://10.0.2.2:8080/artwork/art-1/deadbeef")
        );
    }

    #[test]
    fn does_not_build_uri_when_content_hash_missing() {
        let mut summary = track_summary_fixture();
        summary.artwork = Some(ArtworkRef {
            id: "art-1".into(),
            content_hash: String::new(),
        });
        let origin = Url::parse("http://10.0.2.2:8080/").unwrap();

        let mapped = map_track_summary(summary, Vec::new(), Some(&origin)).unwrap();
        let artwork = mapped.artwork.unwrap();

        assert_eq!(artwork.id, "art-1");
        assert!(artwork.content_hash.is_empty());
        assert_eq!(artwork.uri, None);
    }
}
