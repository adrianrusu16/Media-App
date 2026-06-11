use anyhow::Context;
use serde::Deserialize;

use crate::networking::audio_source_client::{AudioChunk, AudioSourceClient, PlaybackSource};

const JAMENDO_BASE_URL: &str = "https://api.jamendo.com/v3.0";

pub struct JamendoAudioSourceClient {
    http: reqwest::Client,
    client_id: String,
}

impl JamendoAudioSourceClient {
    pub fn new(client_id: impl Into<String>) -> Self {
        Self {
            http: reqwest::Client::new(),
            client_id: client_id.into(),
        }
    }

    fn build_track_url(&self, track_id: &str) -> String {
        format!(
            "{}/tracks/?client_id={}&id={}",
            JAMENDO_BASE_URL, self.client_id, track_id
        )
    }

    fn map_track_response(body: JamendoTracksResponse) -> anyhow::Result<PlaybackSource> {
        let track = body
            .results
            .into_iter()
            .next()
            .context("jamendo resolve_track returned no results")?;

        let uri = track
            .audio
            .or(track.audiodownload)
            .context("jamendo track has no playable audio URI")?;

        Ok(PlaybackSource {
            source_id: track.id,
            uri,
            mime_type: Some("audio/mpeg".to_string()),
            expected_duration_ms: track.duration.map(|seconds| seconds.saturating_mul(1000)),
        })
    }
}

#[async_trait::async_trait]
impl AudioSourceClient for JamendoAudioSourceClient {
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource> {
        let url = self.build_track_url(track_id);
        let response = self
            .http
            .get(url)
            .send()
            .await
            .context("jamendo request failed")?;

        let response = response
            .error_for_status()
            .context("jamendo resolve_track returned non-success status")?;

        let body: JamendoTracksResponse = response
            .json()
            .await
            .context("jamendo resolve_track returned invalid JSON")?;

        Self::map_track_response(body)
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

#[derive(Debug, Deserialize)]
struct JamendoTracksResponse {
    results: Vec<JamendoTrack>,
}

#[derive(Debug, Deserialize)]
struct JamendoTrack {
    id: String,
    audio: Option<String>,
    audiodownload: Option<String>,
    duration: Option<u64>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn map_track_response_maps_first_result_to_playback_source() {
        let response = JamendoTracksResponse {
            results: vec![JamendoTrack {
                id: "123".to_string(),
                audio: Some("https://cdn.test/audio.mp3".to_string()),
                audiodownload: None,
                duration: Some(210),
            }],
        };

        let source = JamendoAudioSourceClient::map_track_response(response).unwrap();
        assert_eq!(source.source_id, "123");
        assert_eq!(source.uri, "https://cdn.test/audio.mp3");
        assert_eq!(source.expected_duration_ms, Some(210_000));
    }

    #[test]
    fn map_track_response_fails_when_result_list_is_empty() {
        let response = JamendoTracksResponse { results: vec![] };

        let error = JamendoAudioSourceClient::map_track_response(response).unwrap_err();
        assert!(error.to_string().contains("returned no results"));
    }

    #[test]
    fn map_track_response_fails_when_no_audio_uri_present() {
        let response = JamendoTracksResponse {
            results: vec![JamendoTrack {
                id: "123".to_string(),
                audio: None,
                audiodownload: None,
                duration: Some(180),
            }],
        };

        let error = JamendoAudioSourceClient::map_track_response(response).unwrap_err();
        assert!(error.to_string().contains("no playable audio URI"));
    }
}
