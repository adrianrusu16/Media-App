use std::sync::Arc;
use std::time::Duration;

use anyhow::Context;

use crate::networking::audio_source_client::{AudioChunk, AudioSourceClient, PlaybackSource};

pub struct RetryingAudioSourceClient {
    inner: Arc<dyn AudioSourceClient>,
    max_retries: usize,
    retry_delay: Duration,
    retry_if: fn(&anyhow::Error) -> bool,
}

impl RetryingAudioSourceClient {
    pub fn new(
        inner: Arc<dyn AudioSourceClient>,
        max_retries: usize,
        retry_delay: Duration,
    ) -> Self {
        Self {
            inner,
            max_retries,
            retry_delay,
            retry_if: |_| true,
        }
    }

    pub fn new_with_policy(
        inner: Arc<dyn AudioSourceClient>,
        max_retries: usize,
        retry_delay: Duration,
        retry_if: fn(&anyhow::Error) -> bool,
    ) -> Self {
        Self {
            inner,
            max_retries,
            retry_delay,
            retry_if,
        }
    }
}

#[async_trait::async_trait]
impl AudioSourceClient for RetryingAudioSourceClient {
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource> {
        let mut attempts = 0;
        loop {
            match self.inner.resolve_track(track_id).await {
                Ok(source) => return Ok(source),
                Err(error) if attempts < self.max_retries && (self.retry_if)(&error) => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error).context(format!(
                        "resolve_track failed after {} attempt(s)",
                        attempts + 1
                    ));
                }
            }
        }
    }

    async fn prefetch_full(&self, source_id: &str) -> anyhow::Result<String> {
        let mut attempts = 0;
        loop {
            match self.inner.prefetch_full(source_id).await {
                Ok(uri) => return Ok(uri),
                Err(error) if attempts < self.max_retries && (self.retry_if)(&error) => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error).context(format!(
                        "prefetch_full failed after {} attempt(s)",
                        attempts + 1
                    ));
                }
            }
        }
    }

    async fn fetch_chunk(
        &self,
        source_id: &str,
        from_chunk_index: u64,
    ) -> anyhow::Result<AudioChunk> {
        let mut attempts = 0;
        loop {
            match self.inner.fetch_chunk(source_id, from_chunk_index).await {
                Ok(chunk) => return Ok(chunk),
                Err(error) if attempts < self.max_retries && (self.retry_if)(&error) => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error).context(format!(
                        "fetch_chunk failed after {} attempt(s)",
                        attempts + 1
                    ));
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::audio_source_client::MockAudioSourceClient;

    #[tokio::test]
    async fn resolve_track_retries_until_success() {
        let mut client = MockAudioSourceClient::new();
        let mut calls = 0;

        client.expect_resolve_track().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient: unavailable"))
            } else {
                Ok(PlaybackSource {
                    source_id: "source-1".to_string(),
                    uri: "https://example.test/stream/source-1".to_string(),
                    mime_type: Some("audio/mpeg".to_string()),
                    expected_duration_ms: Some(210_000),
                })
            }
        });

        let retrying =
            RetryingAudioSourceClient::new(Arc::new(client), 1, Duration::from_millis(1));
        let result = retrying.resolve_track("track-1").await.unwrap();

        assert_eq!(result.source_id, "source-1");
    }

    #[tokio::test]
    async fn prefetch_non_retryable_error_fails_immediately() {
        let mut client = MockAudioSourceClient::new();
        client
            .expect_prefetch_full()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("fatal: unauthorized")));

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingAudioSourceClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let error = retrying.prefetch_full("source-1").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("prefetch_full failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn fetch_chunk_retryable_error_retries_and_succeeds() {
        let mut client = MockAudioSourceClient::new();
        let mut calls = 0;

        client.expect_fetch_chunk().times(2).returning(move |_, _| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient: timeout"))
            } else {
                Ok(AudioChunk {
                    source_id: "source-1".to_string(),
                    index: 3,
                    bytes: vec![1, 2, 3],
                    is_last: false,
                })
            }
        });

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingAudioSourceClient::new_with_policy(
            Arc::new(client),
            2,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let chunk = retrying.fetch_chunk("source-1", 3).await.unwrap();

        assert_eq!(chunk.index, 3);
        assert_eq!(chunk.bytes, vec![1, 2, 3]);
    }
}
