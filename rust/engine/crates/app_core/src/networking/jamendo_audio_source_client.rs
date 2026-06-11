use anyhow::Context;
use std::time::Duration;
use tonic::Status;
use tonic::metadata::MetadataValue;

use crate::networking::audio_source_client::{AudioChunk, AudioSourceClient, PlaybackSource};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JamendoResolveTrackRequest {
    pub track_id: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JamendoResolveTrackResponse {
    pub source_id: String,
    pub uri: Option<String>,
    pub mime_type: Option<String>,
    pub duration_seconds: Option<u64>,
}

#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
#[allow(clippy::result_large_err)]
pub trait JamendoGrpcApi: Send + Sync {
    async fn resolve_track(
        &self,
        request: tonic::Request<JamendoResolveTrackRequest>,
    ) -> Result<tonic::Response<JamendoResolveTrackResponse>, Status>;
}

pub struct JamendoAudioSourceClient<C> {
    grpc: C,
    resolve_timeout: Duration,
    client_name: MetadataValue<tonic::metadata::Ascii>,
    client_version: MetadataValue<tonic::metadata::Ascii>,
}

impl<C> JamendoAudioSourceClient<C> {
    pub fn new(grpc: C) -> Self {
        Self::new_with_config(
            grpc,
            Duration::from_secs(3),
            "panda-engine",
            env!("CARGO_PKG_VERSION"),
        )
    }

    pub fn new_with_resolve_timeout(grpc: C, resolve_timeout: Duration) -> Self {
        Self::new_with_config(
            grpc,
            resolve_timeout,
            "panda-engine",
            env!("CARGO_PKG_VERSION"),
        )
    }

    pub fn new_with_config(
        grpc: C,
        resolve_timeout: Duration,
        client_name: &str,
        client_version: &str,
    ) -> Self {
        Self {
            grpc,
            resolve_timeout,
            client_name: MetadataValue::try_from(client_name)
                .expect("client name must be valid ASCII metadata"),
            client_version: MetadataValue::try_from(client_version)
                .expect("client version must be valid ASCII metadata"),
        }
    }

    fn map_track_response(body: JamendoResolveTrackResponse) -> anyhow::Result<PlaybackSource> {
        let uri = body
            .uri
            .context("jamendo track has no playable audio URI")?;

        Ok(PlaybackSource {
            source_id: body.source_id,
            uri,
            mime_type: body.mime_type,
            expected_duration_ms: body
                .duration_seconds
                .map(|seconds| seconds.saturating_mul(1000)),
        })
    }

    fn normalize_track_id(track_id: &str) -> anyhow::Result<String> {
        let normalized = track_id.trim();
        if normalized.is_empty() {
            anyhow::bail!("jamendo resolve_track requires a non-empty track id")
        }

        Ok(normalized.to_string())
    }

    fn build_request(
        &self,
        normalized_track_id: String,
    ) -> tonic::Request<JamendoResolveTrackRequest> {
        let mut request = tonic::Request::new(JamendoResolveTrackRequest {
            track_id: normalized_track_id,
        });
        request.set_timeout(self.resolve_timeout);
        request
            .metadata_mut()
            .insert("x-client-name", self.client_name.clone());
        request
            .metadata_mut()
            .insert("x-client-version", self.client_version.clone());
        request
    }
}

pub fn is_retryable_grpc_error(error: &anyhow::Error) -> bool {
    let mut current = error.chain();
    if let Some(status) = current.find_map(|cause| cause.downcast_ref::<Status>()) {
        matches!(
            status.code(),
            tonic::Code::Unavailable
                | tonic::Code::DeadlineExceeded
                | tonic::Code::ResourceExhausted
                | tonic::Code::Aborted
        )
    } else {
        false
    }
}

#[async_trait::async_trait]
impl<C> AudioSourceClient for JamendoAudioSourceClient<C>
where
    C: JamendoGrpcApi,
{
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource> {
        let normalized_track_id = Self::normalize_track_id(track_id)?;
        let request = self.build_request(normalized_track_id);

        let response = self.grpc.resolve_track(request).await.map_err(|status| {
            anyhow::Error::new(status).context("jamendo grpc resolve_track failed")
        })?;

        let body = response.into_inner();

        Self::map_track_response(body).context("jamendo resolve_track returned invalid payload")
    }

    async fn prefetch_full(&self, source_id: &str) -> anyhow::Result<String> {
        anyhow::bail!(
            "jamendo prefetch_full is not implemented for source {} (prototype)",
            source_id
        )
    }

    async fn fetch_chunk(
        &self,
        source_id: &str,
        from_chunk_index: u64,
    ) -> anyhow::Result<AudioChunk> {
        anyhow::bail!(
            "jamendo fetch_chunk is not implemented for source {} from chunk {} (prototype)",
            source_id,
            from_chunk_index
        )
    }
}

#[cfg(test)]
#[allow(clippy::result_large_err)]
mod tests {
    use super::*;
    use tonic::Code;

    fn response_with_uri() -> JamendoResolveTrackResponse {
        JamendoResolveTrackResponse {
            source_id: "123".to_string(),
            uri: Some("https://cdn.test/audio.mp3".to_string()),
            mime_type: Some("audio/mpeg".to_string()),
            duration_seconds: Some(210),
        }
    }

    #[test]
    fn map_track_response_maps_first_result_to_playback_source() {
        let response = response_with_uri();

        let source =
            JamendoAudioSourceClient::<MockJamendoGrpcApi>::map_track_response(response).unwrap();
        assert_eq!(source.source_id, "123");
        assert_eq!(source.uri, "https://cdn.test/audio.mp3");
        assert_eq!(source.expected_duration_ms, Some(210_000));
    }

    #[test]
    fn map_track_response_fails_when_no_audio_uri_present() {
        let response = JamendoResolveTrackResponse {
            source_id: "123".to_string(),
            uri: None,
            mime_type: Some("audio/mpeg".to_string()),
            duration_seconds: Some(180),
        };

        let error = JamendoAudioSourceClient::<MockJamendoGrpcApi>::map_track_response(response)
            .unwrap_err();
        assert!(error.to_string().contains("no playable audio URI"));
    }

    #[tokio::test]
    async fn resolve_track_maps_grpc_response_to_playback_source() {
        let mut grpc = MockJamendoGrpcApi::new();
        grpc.expect_resolve_track().once().return_once(|request| {
            let req = request.into_inner();
            assert_eq!(req.track_id, "123");
            Ok(tonic::Response::new(response_with_uri()))
        });

        let client = JamendoAudioSourceClient::new(grpc);
        let source = client.resolve_track("123").await.unwrap();

        assert_eq!(source.source_id, "123");
        assert_eq!(source.expected_duration_ms, Some(210_000));
    }

    #[tokio::test]
    async fn resolve_track_trims_whitespace_from_track_id() {
        let mut grpc = MockJamendoGrpcApi::new();
        grpc.expect_resolve_track().once().return_once(|request| {
            let req = request.into_inner();
            assert_eq!(req.track_id, "123");
            Ok(tonic::Response::new(response_with_uri()))
        });

        let client = JamendoAudioSourceClient::new(grpc);
        let source = client.resolve_track(" 123 ").await.unwrap();

        assert_eq!(source.source_id, "123");
    }

    #[tokio::test]
    async fn resolve_track_sets_client_metadata_and_timeout() {
        let mut grpc = MockJamendoGrpcApi::new();
        grpc.expect_resolve_track().once().return_once(|request| {
            let metadata = request.metadata();
            assert_eq!(metadata.get("x-client-name").unwrap(), "panda-engine");
            assert_eq!(
                metadata.get("x-client-version").unwrap(),
                env!("CARGO_PKG_VERSION")
            );
            assert!(metadata.get("grpc-timeout").is_some());
            Ok(tonic::Response::new(response_with_uri()))
        });

        let client =
            JamendoAudioSourceClient::new_with_resolve_timeout(grpc, Duration::from_secs(5));
        let _ = client.resolve_track("123").await.unwrap();
    }

    #[tokio::test]
    async fn resolve_track_fails_fast_for_blank_track_id() {
        let grpc = MockJamendoGrpcApi::new();
        let client = JamendoAudioSourceClient::new(grpc);

        let error = client.resolve_track("   ").await.unwrap_err();

        assert!(error.to_string().contains("non-empty track id"));
    }

    #[tokio::test]
    async fn resolve_track_surfaces_grpc_status_context() {
        let mut grpc = MockJamendoGrpcApi::new();
        grpc.expect_resolve_track()
            .once()
            .return_once(|_| Err(Status::new(Code::Unavailable, "upstream unavailable")));

        let client = JamendoAudioSourceClient::new(grpc);
        let error = client.resolve_track("123").await.unwrap_err();

        assert!(error.to_string().contains("grpc resolve_track failed"));
        assert!(is_retryable_grpc_error(&error));
    }

    #[tokio::test]
    async fn resolve_track_fails_when_payload_has_no_uri() {
        let mut grpc = MockJamendoGrpcApi::new();
        grpc.expect_resolve_track().once().return_once(|_| {
            Ok(tonic::Response::new(JamendoResolveTrackResponse {
                source_id: "123".to_string(),
                uri: None,
                mime_type: Some("audio/mpeg".to_string()),
                duration_seconds: Some(180),
            }))
        });

        let client = JamendoAudioSourceClient::new(grpc);
        let error = client.resolve_track("123").await.unwrap_err();

        assert!(error.to_string().contains("invalid payload"));
    }

    #[test]
    fn retryable_status_codes_are_classified_as_retryable() {
        let error = anyhow::Error::new(Status::new(Code::Unavailable, "temporary"))
            .context("jamendo grpc resolve_track failed");

        assert!(is_retryable_grpc_error(&error));
    }

    #[test]
    fn non_retryable_status_codes_are_not_classified_as_retryable() {
        let error = anyhow::Error::new(Status::new(Code::InvalidArgument, "bad input"))
            .context("jamendo grpc resolve_track failed");

        assert!(!is_retryable_grpc_error(&error));
    }
}
