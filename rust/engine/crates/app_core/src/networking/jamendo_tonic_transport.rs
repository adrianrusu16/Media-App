use std::sync::Arc;

use anyhow::Context;
use tokio::sync::Mutex;
use tonic::metadata::MetadataValue;
use tonic::service::Interceptor;
use tonic::service::interceptor::InterceptedService;
use tonic::transport::{Channel, Endpoint};

use crate::networking::jamendo_audio_source_client::JamendoGrpcApi;
use crate::networking::jamendo_proto::generated::{
    HealthRequest, ResolveTrackRequest, ResolveTrackResponse,
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
