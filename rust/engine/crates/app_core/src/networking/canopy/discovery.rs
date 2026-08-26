use std::sync::Arc;

use tonic_014::Request;
use url::Url;

use crate::model::discovery::DiscoveryPort;
use crate::{
    EngineDiscoveryIdentity, EngineError, EnginePageRequest, EnginePagedResult, EngineTrack,
};

use super::catalog::{map_page, map_page_request};
use super::operation::CanopyOperation;
use super::request::execute_with_bound_auth;
use super::sdk::clients::discovery_service_client::DiscoveryServiceClient;
use super::sdk::resources::{
    GetDiscoveryFeedRequest, GetDiscoveryFeedResponse, GetForYouFeedRequest, GetForYouFeedResponse,
    GetRecommendationsRequest, GetRecommendationsResponse,
};
use super::{CanopyChannel, SessionCoordinator};

/// Canonical authenticated Canopy discovery adapter.
#[derive(Clone)]
pub struct CanopyDiscoveryClient {
    client: DiscoveryServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
    media_origin: Url,
}

impl CanopyDiscoveryClient {
    pub fn new(
        channel: &CanopyChannel,
        session: Arc<SessionCoordinator>,
        media_origin: Url,
    ) -> Self {
        Self {
            client: DiscoveryServiceClient::new(channel.clone_inner()),
            session,
            media_origin,
        }
    }
}

#[async_trait::async_trait]
impl DiscoveryPort for CanopyDiscoveryClient {
    async fn get_feed(
        &self,
        feed: crate::DiscoveryFeed,
        expected_identity: &EngineDiscoveryIdentity,
        excluded_track_ids: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
        let request = map_discovery_request(excluded_track_ids, page);
        let client = self.client.clone();
        let media_origin = self.media_origin.clone();
        let bound = crate::EngineHistoryIdentity {
            account_id: expected_identity.account_id.clone(),
            session_id: expected_identity.session_id.clone(),
        };
        execute_with_bound_auth(
            self.session.as_ref(),
            &bound,
            feed.operation(),
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                let media_origin = media_origin.clone();
                async move {
                    match feed {
                        crate::DiscoveryFeed::Discovery => client
                            .get_discovery_feed(request.map(|request| GetDiscoveryFeedRequest {
                                exclude_track_ids: request.exclude_track_ids,
                                page: request.page,
                            }))
                            .await
                            .map(|response| {
                                response
                                    .map(|inner| map_discovery_response(inner, Some(&media_origin)))
                            }),
                        crate::DiscoveryFeed::ForYou => client
                            .get_for_you_feed(request.map(|request| GetForYouFeedRequest {
                                exclude_track_ids: request.exclude_track_ids,
                                page: request.page,
                            }))
                            .await
                            .map(|response| {
                                response
                                    .map(|inner| map_for_you_response(inner, Some(&media_origin)))
                            }),
                        crate::DiscoveryFeed::Recommendations => client
                            .get_recommendations(request.map(|request| GetRecommendationsRequest {
                                exclude_track_ids: request.exclude_track_ids,
                                page: request.page,
                            }))
                            .await
                            .map(|response| {
                                response.map(|inner| {
                                    map_recommendations_response(inner, Some(&media_origin))
                                })
                            }),
                    }
                }
            },
        )
        .await?
        .into_inner()
    }
}

fn map_discovery_request(
    excluded_track_ids: &[String],
    page: EnginePageRequest,
) -> GetDiscoveryFeedRequest {
    GetDiscoveryFeedRequest {
        exclude_track_ids: excluded_track_ids.to_vec(),
        page: Some(map_page_request(page)),
    }
}

fn map_discovery_response(
    response: GetDiscoveryFeedResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info, media_origin)
}

fn map_for_you_response(
    response: GetForYouFeedResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info, media_origin)
}

fn map_recommendations_response(
    response: GetRecommendationsResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info, media_origin)
}

impl crate::DiscoveryFeed {
    const fn operation(self) -> CanopyOperation {
        match self {
            Self::Discovery => CanopyOperation::GetDiscoveryFeed,
            Self::ForYou => CanopyOperation::GetForYouFeed,
            Self::Recommendations => CanopyOperation::GetRecommendations,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::canopy::sdk::resources::{
        ArtistSummary, GetDiscoveryFeedResponse, PageInfo, TrackSummary,
    };
    use crate::{EnginePageRequest, EnginePageToken};

    #[test]
    fn forwards_exclusions_and_preserves_opaque_next_page_token() {
        let request = map_discovery_request(
            &["played-1".into(), "played-2".into()],
            EnginePageRequest {
                page_size: 25,
                page_token: Some(EnginePageToken::new("incoming+/=".into()).unwrap()),
            },
        );

        assert_eq!(request.exclude_track_ids, ["played-1", "played-2"]);
        assert_eq!(request.page.as_ref().unwrap().page_size, 25);
        assert_eq!(request.page.unwrap().page_token, "incoming+/=");

        let page = map_discovery_response(
            GetDiscoveryFeedResponse {
                tracks: vec![TrackSummary {
                    id: "recommended-1".into(),
                    title: "A Recommendation".into(),
                    artist: Some(ArtistSummary {
                        id: "artist-1".into(),
                        name: "An Artist".into(),
                    }),
                    album: None,
                    duration_ms: 123_000,
                    explicit: false,
                    artwork: None,
                }],
                page_info: Some(PageInfo {
                    next_page_token: "outgoing+/=".into(),
                }),
            },
            None,
        )
        .unwrap();

        assert_eq!(page.items[0].id, "recommended-1");
        assert_eq!(page.next_page_token.unwrap().as_str(), "outgoing+/=");
    }
}
