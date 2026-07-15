use std::time::Duration;

use tonic_014::Request;
use tonic_014::transport::Channel;

use crate::networking::PlaybackPort;
use crate::{EngineError, EngineErrorType, EnginePlaybackSource};

use super::CanopyChannel;
use super::error::map_status;
use super::sdk::clients::playback_service_client::PlaybackServiceClient;
use super::sdk::resources::{PlaybackSource, ResolvePlaybackRequest};

/// Canonical unary Canopy playback adapter.
#[derive(Clone)]
pub struct CanopyPlaybackClient {
    client: PlaybackServiceClient<Channel>,
}

impl CanopyPlaybackClient {
    pub fn new(channel: &CanopyChannel) -> Self {
        Self {
            client: PlaybackServiceClient::new(channel.clone_inner()),
        }
    }
}

#[async_trait::async_trait]
impl PlaybackPort for CanopyPlaybackClient {
    async fn resolve_playback(&self, track_id: &str) -> Result<EnginePlaybackSource, EngineError> {
        let mut client = self.client.clone();
        let response = client
            .resolve_playback(playback_request(track_id))
            .await
            .map_err(map_status)?
            .into_inner();
        map_playback_source(response)
    }
}

fn playback_request(track_id: &str) -> Request<ResolvePlaybackRequest> {
    let mut request = Request::new(ResolvePlaybackRequest {
        track_id: track_id.to_owned(),
    });
    request.set_timeout(Duration::from_secs(3));
    request
}

fn map_playback_source(source: PlaybackSource) -> Result<EnginePlaybackSource, EngineError> {
    let expires_at_epoch_millis = source
        .expires_at
        .ok_or_else(mapping_defect)
        .and_then(timestamp_to_epoch_millis)?;

    Ok(EnginePlaybackSource {
        track_id: source.track_id,
        url: source.stream_url,
        content_type: source.content_type,
        codec: source.codec,
        duration_millis: source.duration_ms,
        expires_at_epoch_millis,
    })
}

fn timestamp_to_epoch_millis(
    timestamp: super::sdk::well_known_types::Timestamp,
) -> Result<u64, EngineError> {
    if !(0..=PROTOBUF_TIMESTAMP_MAX_SECONDS).contains(&timestamp.seconds)
        || !(0..1_000_000_000).contains(&timestamp.nanos)
    {
        return Err(mapping_defect());
    }

    let seconds = u64::try_from(timestamp.seconds).map_err(|_| mapping_defect())?;
    seconds
        .checked_mul(1_000)
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(mapping_defect)
}

const PROTOBUF_TIMESTAMP_MAX_SECONDS: i64 = 253_402_300_799;

fn mapping_defect() -> EngineError {
    EngineError::new(
        EngineErrorType::MappingDefect,
        "invalid canonical Canopy playback response",
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::{map_playback_source, playback_request};
    use crate::EngineErrorType;
    use crate::networking::canopy::sdk::resources::PlaybackSource;
    use crate::networking::canopy::sdk::well_known_types::Timestamp;

    fn timestamp_fixture() -> Timestamp {
        Timestamp {
            seconds: 1_750_000_000,
            nanos: 250_000_000,
        }
    }

    fn playback_fixture() -> PlaybackSource {
        PlaybackSource {
            track_id: "track-1".into(),
            stream_url: "http://10.0.2.2:8080/s/opaque?token=a%2Fb".into(),
            content_type: "audio/flac".into(),
            codec: "flac".into(),
            duration_ms: 42_000,
            expires_at: Some(timestamp_fixture()),
        }
    }

    #[test]
    fn maps_opaque_playback_capability_verbatim() {
        let source = map_playback_source(playback_fixture()).unwrap();

        assert_eq!(source.url, "http://10.0.2.2:8080/s/opaque?token=a%2Fb");
        assert_eq!(source.expires_at_epoch_millis, 1_750_000_000_250);
    }

    #[test]
    fn missing_or_invalid_expiry_is_a_mapping_defect() {
        let mut missing = playback_fixture();
        missing.expires_at = None;
        assert_eq!(
            map_playback_source(missing).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );

        let mut invalid = playback_fixture();
        invalid.expires_at = Some(Timestamp {
            seconds: -1,
            nanos: 0,
        });
        assert_eq!(
            map_playback_source(invalid).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );

        let mut above_canonical_maximum = playback_fixture();
        above_canonical_maximum.expires_at = Some(Timestamp {
            seconds: 253_402_300_800,
            nanos: 0,
        });
        assert_eq!(
            map_playback_source(above_canonical_maximum)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn playback_request_has_a_bounded_deadline() {
        let request = playback_request("track-1");

        assert_eq!(
            request
                .metadata()
                .get("grpc-timeout")
                .and_then(|value| value.to_str().ok()),
            Some("3000000u")
        );
    }
}
