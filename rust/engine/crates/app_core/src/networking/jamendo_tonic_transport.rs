use std::sync::Arc;

use anyhow::Context;
use tokio::sync::Mutex;
use tokio_stream::StreamExt;
use tonic::metadata::MetadataValue;
use tonic::service::Interceptor;
use tonic::service::interceptor::InterceptedService;
use tonic::transport::{Channel, Endpoint};

use crate::data::repository::MediaItem;
use crate::networking::backend_client::{BackendClient, MediaItemStream};
use crate::networking::jamendo_audio_source_client::JamendoGrpcApi;
use crate::networking::jamendo_proto::generated::{
    HealthRequest, ResolveTrackRequest, ResolveTrackResponse, SearchRequest, SearchResult,
    jamendo_service_client::JamendoServiceClient,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JamendoHealth {
    Healthy,
    Reachable,
    Degraded,
}

#[derive(Clone)]
struct StaticMetadataInterceptor {
    client_name: MetadataValue<tonic::metadata::Ascii>,
    client_version: MetadataValue<tonic::metadata::Ascii>,
}

impl StaticMetadataInterceptor {
    fn new(client_name: &str, client_version: &str) -> anyhow::Result<Self> {
        Ok(Self {
            client_name: MetadataValue::try_from(client_name)
                .context("client name must be valid ASCII metadata")?,
            client_version: MetadataValue::try_from(client_version)
                .context("client version must be valid ASCII metadata")?,
        })
    }
}

impl Interceptor for StaticMetadataInterceptor {
    fn call(
        &mut self,
        mut request: tonic::Request<()>,
    ) -> Result<tonic::Request<()>, tonic::Status> {
        request
            .metadata_mut()
            .insert("x-client-name", self.client_name.clone());
        request
            .metadata_mut()
            .insert("x-client-version", self.client_version.clone());
        Ok(request)
    }
}

type JamendoGrpcClient =
    JamendoServiceClient<InterceptedService<Channel, StaticMetadataInterceptor>>;

struct SearchStreamLifecycle {
    query: String,
    completed: bool,
}

impl Drop for SearchStreamLifecycle {
    fn drop(&mut self) {
        if !self.completed {
            tracing::debug!(
                query = %self.query,
                "jamendo search gRPC stream cancelled"
            );
        }
    }
}

pub struct JamendoTonicTransport {
    client: Arc<Mutex<JamendoGrpcClient>>,
}

impl JamendoTonicTransport {
    pub async fn connect(
        endpoint: &str,
        client_name: &str,
        client_version: &str,
    ) -> anyhow::Result<Self> {
        let endpoint =
            Endpoint::from_shared(endpoint.to_string()).context("invalid jamendo gRPC endpoint")?;
        let channel = endpoint
            .connect()
            .await
            .context("failed to connect jamendo gRPC channel")?;

        Self::from_channel(channel, client_name, client_version)
    }

    pub fn from_channel(
        channel: Channel,
        client_name: &str,
        client_version: &str,
    ) -> anyhow::Result<Self> {
        let interceptor = StaticMetadataInterceptor::new(client_name, client_version)?;
        let client = JamendoServiceClient::with_interceptor(channel, interceptor);
        Ok(Self {
            client: Arc::new(Mutex::new(client)),
        })
    }

    fn map_health_state(value: i32) -> JamendoHealth {
        use crate::networking::jamendo_proto::generated::health_response::State;

        match State::try_from(value).unwrap_or(State::Unspecified) {
            State::Healthy => JamendoHealth::Healthy,
            State::Reachable => JamendoHealth::Reachable,
            State::Degraded | State::Unspecified => JamendoHealth::Degraded,
        }
    }

    pub async fn health(&self) -> anyhow::Result<JamendoHealth> {
        let mut client = self.client.lock().await;
        let response = client
            .health(tonic::Request::new(HealthRequest {}))
            .await
            .context("jamendo health RPC failed")?;
        let health = Self::map_health_state(response.into_inner().state);
        tracing::debug!(?health, "jamendo health probe completed");
        Ok(health)
    }

    fn map_search_result(item: SearchResult) -> anyhow::Result<MediaItem> {
        if item.id.trim().is_empty() {
            anyhow::bail!("jamendo search result missing id")
        }

        if item.title.trim().is_empty() {
            anyhow::bail!("jamendo search result missing title")
        }

        Ok(MediaItem {
            id: item.id,
            title: item.title,
            artist: item.artist.unwrap_or_default(),
            ..Default::default()
        })
    }
}

#[async_trait::async_trait]
impl BackendClient for JamendoTonicTransport {
    async fn fetch_children(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        Err(anyhow::anyhow!(
            "jamendo does not support browse/fetch_children for parent_id={parent_id}"
        ))
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        let mut stream = self.search_stream(query).await?;
        let mut items = Vec::new();
        while let Some(item) = stream.next().await {
            items.push(item?);
        }
        Ok(items)
    }

    async fn search_stream(&self, query: &str) -> anyhow::Result<MediaItemStream> {
        let normalized_query = query.trim().to_string();
        if normalized_query.is_empty() {
            anyhow::bail!("search query cannot be blank")
        }

        tracing::debug!(query = %normalized_query, "jamendo search gRPC stream start");
        let mut client = self.client.lock().await;
        let response = client
            .search(tonic::Request::new(SearchRequest {
                query: normalized_query.clone(),
            }))
            .await
            .with_context(|| format!("jamendo search RPC failed for query={normalized_query}"))?;
        let mut upstream = response.into_inner();
        let stream = async_stream::stream! {
            let mut lifecycle = SearchStreamLifecycle {
                query: normalized_query.clone(),
                completed: false,
            };

            while let Some(item) = upstream.next().await {
                match item {
                    Ok(search_result) => {
                        let mapped = Self::map_search_result(search_result);
                        match &mapped {
                            Ok(media_item) => tracing::debug!(
                                query = %normalized_query,
                                id = %media_item.id,
                                "jamendo search gRPC stream item_received"
                            ),
                            Err(error) => tracing::warn!(
                                query = %normalized_query,
                                error = %error,
                                "jamendo search gRPC stream mapping_failed"
                            ),
                        }
                        yield mapped;
                    }
                    Err(status) => {
                        tracing::warn!(
                            query = %normalized_query,
                            code = ?status.code(),
                            "jamendo search gRPC stream failed"
                        );
                        yield Err(anyhow::Error::new(status).context(format!(
                            "jamendo search stream failed for query={normalized_query}"
                        )));
                        return;
                    }
                }
            }

            lifecycle.completed = true;
            tracing::debug!(query = %normalized_query, "jamendo search gRPC stream completed");
        };
        Ok(Box::pin(stream))
    }
}

#[async_trait::async_trait]
impl JamendoGrpcApi for JamendoTonicTransport {
    async fn resolve_track(
        &self,
        request: tonic::Request<ResolveTrackRequest>,
    ) -> Result<tonic::Response<ResolveTrackResponse>, tonic::Status> {
        let track_id = request.get_ref().track_id.clone();
        tracing::debug!(track_id = %track_id, "jamendo resolve_track gRPC call start");

        let mut client = self.client.lock().await;
        let response = client.resolve_track(request).await;
        match &response {
            Ok(_) => {
                tracing::debug!(track_id = %track_id, "jamendo resolve_track gRPC call success")
            }
            Err(status) => tracing::warn!(
                track_id = %track_id,
                code = ?status.code(),
                "jamendo resolve_track gRPC call failed"
            ),
        }
        response
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::jamendo_proto::generated::health_response::State;

    #[test]
    fn map_search_result_maps_payload_to_media_item() {
        let item = JamendoTonicTransport::map_search_result(SearchResult {
            id: "track-1".to_string(),
            title: "A Song".to_string(),
            artist: Some("An Artist".to_string()),
            album: None,
            art_uri: None,
        })
        .unwrap();

        assert_eq!(item.id, "track-1");
        assert_eq!(item.title, "A Song");
        assert_eq!(item.artist, "An Artist");
    }

    #[test]
    fn map_search_result_rejects_missing_required_fields() {
        assert!(
            JamendoTonicTransport::map_search_result(SearchResult {
                id: "".to_string(),
                title: "Title".to_string(),
                artist: None,
                album: None,
                art_uri: None,
            })
            .is_err()
        );

        assert!(
            JamendoTonicTransport::map_search_result(SearchResult {
                id: "track-1".to_string(),
                title: "".to_string(),
                artist: None,
                album: None,
                art_uri: None,
            })
            .is_err()
        );
    }

    #[test]
    fn maps_health_state_values() {
        assert_eq!(
            JamendoTonicTransport::map_health_state(State::Healthy as i32),
            JamendoHealth::Healthy
        );
        assert_eq!(
            JamendoTonicTransport::map_health_state(State::Reachable as i32),
            JamendoHealth::Reachable
        );
        assert_eq!(
            JamendoTonicTransport::map_health_state(State::Degraded as i32),
            JamendoHealth::Degraded
        );
        assert_eq!(
            JamendoTonicTransport::map_health_state(State::Unspecified as i32),
            JamendoHealth::Degraded
        );
        assert_eq!(
            JamendoTonicTransport::map_health_state(999),
            JamendoHealth::Degraded
        );
    }
}
