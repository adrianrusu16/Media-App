use std::sync::Arc;

use tonic_014::Request;

use crate::model::discovery::DiscoveryPort;
use crate::{EngineError, EnginePageRequest, EnginePagedResult, EngineTrack};

use super::catalog::{map_page, map_page_request};
use super::request::{ReplayPolicy, execute_with_auth};
use super::sdk::clients::discovery_service_client::DiscoveryServiceClient;
use super::sdk::resources::{GetDiscoveryFeedRequest, GetDiscoveryFeedResponse};
use super::{CanopyChannel, SessionCoordinator};

/// Canonical authenticated Canopy discovery adapter.
#[derive(Clone)]
pub struct CanopyDiscoveryClient {
    client: DiscoveryServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
}

impl CanopyDiscoveryClient {
    pub fn new(channel: &CanopyChannel, session: Arc<SessionCoordinator>) -> Self {
        Self {
            client: DiscoveryServiceClient::new(channel.clone_inner()),
            session,
        }
    }
}

#[async_trait::async_trait]
impl DiscoveryPort for CanopyDiscoveryClient {
    async fn get_feed(
        &self,
        excluded_track_ids: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
        let request = map_discovery_request(excluded_track_ids, page);
        let client = self.client.clone();
        let response = execute_with_auth(
            Some(self.session.as_ref()),
            ReplayPolicy::Safe,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.get_discovery_feed(request).await }
            },
        )
        .await?
        .into_inner();
        map_discovery_response(response)
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
) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
    map_page(response.tracks, response.page_info)
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

        let page = map_discovery_response(GetDiscoveryFeedResponse {
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
        })
        .unwrap();

        assert_eq!(page.items[0].id, "recommended-1");
        assert_eq!(page.next_page_token.unwrap().as_str(), "outgoing+/=");
    }
}
