use std::sync::Arc;

use tonic_014::Request;

use crate::networking::backend_client::CatalogPort;
use crate::{
    EngineAlbum, EngineArtist, EngineError, EngineErrorType, EnginePageRequest, EnginePageToken,
    EnginePagedResult, EngineTrack,
};

use super::CanopyChannel;
use super::request::{ReplayPolicy, execute_with_auth};
use super::sdk::clients::catalog_service_client::CatalogServiceClient;
use super::sdk::resources::{
    BrowseRequest, BrowseResponse, GetMediaRequest, PageInfo, PageRequest, SearchRequest,
    SearchResponse, Track, TrackSummary,
};
use super::session::SessionCoordinator;

/// Canonical unary Canopy catalog adapter.
#[derive(Clone)]
pub struct CanopyCatalogClient {
    client: CatalogServiceClient<super::sdk::runtime::transport::Channel>,
    session: Option<Arc<SessionCoordinator>>,
}

impl CanopyCatalogClient {
    pub fn new(channel: &CanopyChannel) -> Self {
        Self {
            client: CatalogServiceClient::new(channel.clone_inner()),
            session: None,
        }
    }

    /// Composes catalog transport with Rust-owned Canopy session handling.
    pub fn with_session_coordinator(
        channel: &CanopyChannel,
        session: Arc<SessionCoordinator>,
    ) -> Self {
        Self {
            client: CatalogServiceClient::new(channel.clone_inner()),
            session: Some(session),
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
        let response = execute_with_auth(
            self.session.as_deref(),
            ReplayPolicy::Safe,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.browse(request).await }
            },
        )
        .await?
        .into_inner();
        map_browse_response(response)
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
        let response = execute_with_auth(
            self.session.as_deref(),
            ReplayPolicy::Safe,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.search(request).await }
            },
        )
        .await?
        .into_inner();
        map_search_response(response)
    }

    async fn get_media(&self, track_id: &str) -> Result<EngineTrack, EngineError> {
        let request = GetMediaRequest {
            track_id: track_id.to_owned(),
        };
        let client = self.client.clone();
        let response = execute_with_auth(
            self.session.as_deref(),
            ReplayPolicy::Safe,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.get_media(request).await }
            },
        )
        .await?
        .into_inner();
        map_track(response)
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
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info)
}

fn map_browse_response(
    response: BrowseResponse,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info)
}

pub(super) fn map_page(
    tracks: Vec<TrackSummary>,
    page_info: Option<PageInfo>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    Ok(EnginePagedResult {
        items: tracks
            .into_iter()
            .map(|summary| map_track_summary(summary, Vec::new()))
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

fn map_track(track: Track) -> Result<EngineTrack, EngineError> {
    let summary = track
        .summary
        .ok_or_else(|| mapping_defect("catalog track missing summary"))?;
    map_track_summary(summary, track.genres)
}

fn map_track_summary(
    summary: TrackSummary,
    genres: Vec<String>,
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
        artwork_id: summary.artwork.map(|artwork| artwork.id),
        genres,
    })
}

fn mapping_defect(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::MappingDefect, message, false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::canopy::sdk::resources::{
        ArtistSummary, PageInfo, SearchResponse, TrackSummary,
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

        let page = map_search_response(response).unwrap();

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
            map_search_response(response).unwrap_err().error_type,
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

        let page = map_search_response(response).unwrap();

        assert_eq!(page.items[0].artist.id, "");
        assert_eq!(page.items[0].artist.name, "An Artist");
    }

    #[test]
    fn maps_full_track_genres() {
        let track = Track {
            summary: Some(track_summary_fixture()),
            genres: vec!["jazz".into(), "fusion".into()],
        };

        let mapped = map_track(track).unwrap();

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

        assert_eq!(map_search_response(response).unwrap().next_page_token, None);
    }
}
