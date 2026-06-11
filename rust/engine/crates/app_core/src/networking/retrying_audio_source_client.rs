use std::sync::Arc;
use std::time::Duration;

use anyhow::Context;

use crate::networking::audio_source_client::{AudioChunk, AudioSourceClient, PlaybackSource};

#[derive(Clone, Copy)]
enum RetryDelayStrategy {
    Fixed,
    Exponential {
        max_delay: Duration,
    },
    ExponentialJittered {
        max_delay: Duration,
        jitter_percent: u8,
    },
}

pub struct RetryingAudioSourceClient<C> {
    inner: Arc<C>,
    max_retry_attempts: usize,
    retry_delay: Duration,
    max_total_retry_delay: Option<Duration>,
    retry_delay_strategy: RetryDelayStrategy,
    retry_if: fn(&anyhow::Error) -> bool,
}

impl<C> RetryingAudioSourceClient<C>
where
    C: AudioSourceClient,
{
    pub fn new(inner: Arc<C>, max_retries: usize, retry_delay: Duration) -> Self {
        Self {
            inner,
            max_retry_attempts: max_retries,
            retry_delay,
            max_total_retry_delay: None,
            retry_delay_strategy: RetryDelayStrategy::Fixed,
            retry_if: |_| true,
        }
    }

    pub fn new_with_exponential_backoff(
        inner: Arc<C>,
        max_retries: usize,
        retry_delay: Duration,
        max_retry_delay: Duration,
    ) -> Self {
        Self {
            inner,
            max_retry_attempts: max_retries,
            retry_delay,
            max_total_retry_delay: None,
            retry_delay_strategy: RetryDelayStrategy::Exponential {
                max_delay: max_retry_delay,
            },
            retry_if: |_| true,
        }
    }

    pub fn new_with_exponential_backoff_and_jitter(
        inner: Arc<C>,
        max_retries: usize,
        retry_delay: Duration,
        max_retry_delay: Duration,
        jitter_percent: u8,
    ) -> Self {
        Self {
            inner,
            max_retry_attempts: max_retries,
            retry_delay,
            max_total_retry_delay: None,
            retry_delay_strategy: RetryDelayStrategy::ExponentialJittered {
                max_delay: max_retry_delay,
                jitter_percent,
            },
            retry_if: |_| true,
        }
    }

    pub fn new_with_policy(
        inner: Arc<C>,
        max_retries: usize,
        retry_delay: Duration,
        retry_if: fn(&anyhow::Error) -> bool,
    ) -> Self {
        Self {
            inner,
            max_retry_attempts: max_retries,
            retry_delay,
            max_total_retry_delay: None,
            retry_delay_strategy: RetryDelayStrategy::Fixed,
            retry_if,
        }
    }

    pub fn with_retry_policy(mut self, retry_if: fn(&anyhow::Error) -> bool) -> Self {
        self.retry_if = retry_if;
        self
    }

    pub fn with_retry_time_budget(mut self, max_total_retry_delay: Duration) -> Self {
        self.max_total_retry_delay = Some(max_total_retry_delay);
        self
    }

    fn retry_delay_for_attempt(&self, next_attempt_number: usize) -> Duration {
        match self.retry_delay_strategy {
            RetryDelayStrategy::Fixed => self.retry_delay,
            RetryDelayStrategy::Exponential { max_delay } => {
                let shift = next_attempt_number.saturating_sub(1) as u32;
                let multiplier = 1u32.checked_shl(shift).unwrap_or(u32::MAX);
                self.retry_delay.saturating_mul(multiplier).min(max_delay)
            }
            RetryDelayStrategy::ExponentialJittered {
                max_delay,
                jitter_percent,
            } => {
                let shift = next_attempt_number.saturating_sub(1) as u32;
                let multiplier = 1u32.checked_shl(shift).unwrap_or(u32::MAX);
                let capped = self.retry_delay.saturating_mul(multiplier).min(max_delay);
                apply_jitter(capped, next_attempt_number, jitter_percent).min(max_delay)
            }
        }
    }

    fn retry_sleep_delay_for_attempt(
        &self,
        next_attempt_number: usize,
        total_retry_delay: Duration,
    ) -> Option<Duration> {
        let retry_delay = self.retry_delay_for_attempt(next_attempt_number);
        match self.max_total_retry_delay {
            None => Some(retry_delay),
            Some(budget) if total_retry_delay >= budget => None,
            Some(budget) => Some(retry_delay.min(budget - total_retry_delay)),
        }
    }
}

fn apply_jitter(base_delay: Duration, attempt_number: usize, jitter_percent: u8) -> Duration {
    let jitter_percent = jitter_percent.min(100) as u128;
    if jitter_percent == 0 {
        return base_delay;
    }

    let base_ms = base_delay.as_millis();
    if base_ms == 0 {
        return base_delay;
    }

    let min_ms = base_ms.saturating_mul(100u128.saturating_sub(jitter_percent)) / 100;
    let max_ms = base_ms.saturating_mul(100u128.saturating_add(jitter_percent)) / 100;
    let spread = max_ms.saturating_sub(min_ms);
    let seed = (attempt_number as u128)
        .wrapping_mul(1_103_515_245)
        .wrapping_add(12_345);
    let jittered_ms = min_ms + if spread == 0 { 0 } else { seed % (spread + 1) };

    Duration::from_millis(jittered_ms.min(u64::MAX as u128) as u64)
}

#[async_trait::async_trait]
impl<C> AudioSourceClient for RetryingAudioSourceClient<C>
where
    C: AudioSourceClient,
{
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource> {
        let mut attempts = 0;
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.resolve_track(track_id).await {
                Ok(source) => return Ok(source),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error).context(format!(
                            "resolve_track failed after {} attempt(s)",
                            attempts
                        ));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
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
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.prefetch_full(source_id).await {
                Ok(uri) => return Ok(uri),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error).context(format!(
                            "prefetch_full failed after {} attempt(s)",
                            attempts
                        ));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
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
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.fetch_chunk(source_id, from_chunk_index).await {
                Ok(chunk) => return Ok(chunk),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error)
                            .context(format!("fetch_chunk failed after {} attempt(s)", attempts));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
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
    use crate::networking::canopy_audio_source_client::is_retryable_grpc_error;
    use tonic::{Code, Status};

    #[test]
    fn exponential_backoff_delay_caps_at_max_retry_delay() {
        let client = MockAudioSourceClient::new();
        let retrying = RetryingAudioSourceClient::new_with_exponential_backoff(
            Arc::new(client),
            5,
            Duration::from_millis(10),
            Duration::from_millis(25),
        );

        assert_eq!(
            retrying.retry_delay_for_attempt(1),
            Duration::from_millis(10)
        );
        assert_eq!(
            retrying.retry_delay_for_attempt(2),
            Duration::from_millis(20)
        );
        assert_eq!(
            retrying.retry_delay_for_attempt(3),
            Duration::from_millis(25)
        );
        assert_eq!(
            retrying.retry_delay_for_attempt(4),
            Duration::from_millis(25)
        );
    }

    #[test]
    fn jittered_exponential_backoff_stays_bounded_and_capped() {
        let client = MockAudioSourceClient::new();
        let retrying = RetryingAudioSourceClient::new_with_exponential_backoff_and_jitter(
            Arc::new(client),
            5,
            Duration::from_millis(10),
            Duration::from_millis(25),
            20,
        );

        let delay1 = retrying.retry_delay_for_attempt(1).as_millis();
        assert!((8..=12).contains(&delay1));

        let delay2 = retrying.retry_delay_for_attempt(2).as_millis();
        assert!((16..=24).contains(&delay2));

        let delay3 = retrying.retry_delay_for_attempt(3).as_millis();
        assert!((20..=25).contains(&delay3));
    }

    #[test]
    fn retry_delay_budget_caps_cumulative_sleep_delay() {
        let client = MockAudioSourceClient::new();
        let retrying = RetryingAudioSourceClient::new_with_exponential_backoff(
            Arc::new(client),
            5,
            Duration::from_millis(10),
            Duration::from_millis(80),
        )
        .with_retry_time_budget(Duration::from_millis(25));

        assert_eq!(
            retrying.retry_sleep_delay_for_attempt(1, Duration::from_millis(0)),
            Some(Duration::from_millis(10))
        );
        assert_eq!(
            retrying.retry_sleep_delay_for_attempt(2, Duration::from_millis(10)),
            Some(Duration::from_millis(15))
        );
        assert_eq!(
            retrying.retry_sleep_delay_for_attempt(3, Duration::from_millis(25)),
            None
        );
    }

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
    async fn resolve_track_retry_policy_retries_retryable_grpc_status() {
        let mut client = MockAudioSourceClient::new();
        let mut calls = 0;

        client.expect_resolve_track().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::Error::new(Status::new(
                    Code::Unavailable,
                    "upstream temporarily unavailable",
                )))
            } else {
                Ok(PlaybackSource {
                    source_id: "source-1".to_string(),
                    uri: "https://example.test/stream/source-1".to_string(),
                    mime_type: Some("audio/mpeg".to_string()),
                    expected_duration_ms: Some(210_000),
                })
            }
        });

        let retrying = RetryingAudioSourceClient::new_with_policy(
            Arc::new(client),
            2,
            Duration::from_millis(1),
            is_retryable_grpc_error,
        );
        let result = retrying.resolve_track("track-1").await.unwrap();

        assert_eq!(result.source_id, "source-1");
    }

    #[tokio::test]
    async fn resolve_track_retry_policy_does_not_retry_non_retryable_grpc_status() {
        let mut client = MockAudioSourceClient::new();
        client.expect_resolve_track().times(1).returning(|_| {
            Err(anyhow::Error::new(Status::new(
                Code::InvalidArgument,
                "invalid track id",
            )))
        });

        let retrying = RetryingAudioSourceClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            is_retryable_grpc_error,
        );
        let error = retrying.resolve_track("bad-track").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("resolve_track failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn jitter_strategy_can_be_combined_with_retry_policy() {
        let mut client = MockAudioSourceClient::new();
        client.expect_resolve_track().times(1).returning(|_| {
            Err(anyhow::Error::new(Status::new(
                Code::InvalidArgument,
                "invalid track id",
            )))
        });

        let retrying = RetryingAudioSourceClient::new_with_exponential_backoff_and_jitter(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            Duration::from_millis(4),
            20,
        )
        .with_retry_policy(is_retryable_grpc_error);
        let error = retrying.resolve_track("bad-track").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("resolve_track failed after 1 attempt(s)")
        );
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
