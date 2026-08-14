use std::time::Duration;

use tonic_014::Request;
use tonic_014::transport::Channel;

use crate::networking::SystemPort;
use crate::{
    EngineBackendStatus, EngineDependencyStatus, EngineError, EngineErrorType, EngineStatusValue,
};

use super::CanopyChannel;
use super::operation::CanopyOperation;
use super::request::execute;
use super::sdk::{
    clients::system_service_client::SystemServiceClient,
    resources::{GetStatusRequest, GetStatusResponse},
};

pub struct CanopySystemClient {
    client: SystemServiceClient<Channel>,
}

impl CanopySystemClient {
    pub fn new(channel: &CanopyChannel) -> Self {
        Self {
            client: SystemServiceClient::new(channel.clone_inner()),
        }
    }
}

#[async_trait::async_trait]
impl SystemPort for CanopySystemClient {
    async fn get_status(&self) -> Result<EngineBackendStatus, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::GetStatus,
            status_request,
            move |request| {
                let mut client = client.clone();
                async move { client.get_status(request).await }
            },
        )
        .await?
        .into_inner();
        map_status_response(response)
    }
}

fn status_request() -> Request<GetStatusRequest> {
    let mut request = Request::new(GetStatusRequest {});
    request.set_timeout(Duration::from_secs(3));
    request
}

fn map_status_response(response: GetStatusResponse) -> Result<EngineBackendStatus, EngineError> {
    let checked_at_epoch_millis = response
        .checked_at
        .map(timestamp_to_epoch_millis)
        .transpose()?;

    Ok(EngineBackendStatus {
        healthy: response.healthy,
        version: response.version,
        status: EngineStatusValue::from_wire(response.status),
        dependencies: response
            .dependencies
            .into_iter()
            .map(|dependency| EngineDependencyStatus {
                name: dependency.name,
                status: EngineStatusValue::from_wire(dependency.status),
                message: dependency.message,
            })
            .collect(),
        checked_at_epoch_millis,
    })
}

fn timestamp_to_epoch_millis(
    timestamp: super::sdk::well_known_types::Timestamp,
) -> Result<u64, EngineError> {
    if timestamp.seconds < 0 || !(0..1_000_000_000).contains(&timestamp.nanos) {
        return Err(mapping_defect());
    }

    let seconds = u64::try_from(timestamp.seconds).map_err(|_| mapping_defect())?;
    seconds
        .checked_mul(1_000)
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(mapping_defect)
}

fn mapping_defect() -> EngineError {
    EngineError::new(
        EngineErrorType::MappingDefect,
        "invalid canonical Canopy status response",
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::super::sdk::well_known_types::Timestamp;
    use super::{map_status_response, status_request};
    use crate::networking::canopy::sdk::resources::{DependencyStatus, GetStatusResponse};

    #[test]
    fn maps_status_and_preserves_open_dependency_values() {
        let response = GetStatusResponse {
            healthy: true,
            version: "0.2.0".into(),
            status: "future-ready-state".into(),
            dependencies: vec![DependencyStatus {
                name: "catalog".into(),
                status: "future-dependency-state".into(),
                message: "available".into(),
            }],
            checked_at: Some(Timestamp {
                seconds: 1_750_000_000,
                nanos: 250_000_000,
            }),
        };

        let mapped = map_status_response(response).unwrap();

        assert!(mapped.healthy);
        assert_eq!(mapped.status.as_wire(), "future-ready-state");
        assert_eq!(
            mapped.dependencies[0].status.as_wire(),
            "future-dependency-state"
        );
        assert_eq!(mapped.checked_at_epoch_millis, Some(1_750_000_000_250));
    }

    #[test]
    fn invalid_status_timestamp_is_a_mapping_defect() {
        let response = GetStatusResponse {
            healthy: false,
            version: String::new(),
            status: String::new(),
            dependencies: vec![],
            checked_at: Some(Timestamp {
                seconds: -1,
                nanos: 0,
            }),
        };

        assert!(map_status_response(response).is_err());
    }

    #[test]
    fn status_request_has_a_bounded_deadline() {
        let request = status_request();

        assert_eq!(
            request
                .metadata()
                .get("grpc-timeout")
                .and_then(|value| value.to_str().ok()),
            Some("3000000u")
        );
    }
}
