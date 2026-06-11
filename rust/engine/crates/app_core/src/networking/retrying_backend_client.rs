use std::sync::Arc;
use std::time::Duration;

use crate::data::repository::MediaItem;
use crate::networking::backend_client::{BackendClient, MediaItemStream};
use anyhow::Context;

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

pub struct RetryingBackendClient<C> {
    inner: Arc<C>,
    max_retry_attempts: usize,
    retry_delay: Duration,
    max_total_retry_delay: Option<Duration>,
    retry_delay_strategy: RetryDelayStrategy,
    retry_if: fn(&anyhow::Error) -> bool,
}

impl<C> RetryingBackendClient<C>
where
    C: BackendClient,
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
impl<C> BackendClient for RetryingBackendClient<C>
where
    C: BackendClient,
{
    async fn fetch_children(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        let mut attempts = 0;
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.fetch_children(parent_id).await {
                Ok(items) => return Ok(items),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error).context(format!(
                            "fetch_children failed after {} attempt(s)",
                            attempts
                        ));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error).context(format!(
                        "fetch_children failed after {} attempt(s)",
                        attempts + 1
                    ));
                }
            }
        }
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        let mut attempts = 0;
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.search(query).await {
                Ok(items) => return Ok(items),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error)
                            .context(format!("search failed after {} attempt(s)", attempts));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error)
                        .context(format!("search failed after {} attempt(s)", attempts + 1));
                }
            }
        }
    }

    async fn search_stream(&self, query: &str) -> anyhow::Result<MediaItemStream> {
        let mut attempts = 0;
        let mut total_retry_delay = Duration::ZERO;
        loop {
            match self.inner.search_stream(query).await {
                Ok(stream) => return Ok(stream),
                Err(error) if attempts < self.max_retry_attempts && (self.retry_if)(&error) => {
                    attempts += 1;
                    let Some(sleep_delay) =
                        self.retry_sleep_delay_for_attempt(attempts, total_retry_delay)
                    else {
                        return Err(error).context(format!(
                            "search_stream failed after {} attempt(s)",
                            attempts
                        ));
                    };

                    total_retry_delay = total_retry_delay.saturating_add(sleep_delay);
                    tokio::time::sleep(sleep_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error).context(format!(
                        "search_stream failed after {} attempt(s)",
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
    use crate::networking::backend_client::MockBackendClient;
    use tokio_stream::StreamExt;

    #[test]
    fn exponential_backoff_delay_caps_at_max_retry_delay() {
        let client = MockBackendClient::new();
        let retrying = RetryingBackendClient::new_with_exponential_backoff(
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
        let client = MockBackendClient::new();
        let retrying = RetryingBackendClient::new_with_exponential_backoff_and_jitter(
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
        let client = MockBackendClient::new();
        let retrying = RetryingBackendClient::new_with_exponential_backoff(
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
    async fn search_retries_until_success() {
        let mut client = MockBackendClient::new();
        let mut calls = 0;

        client.expect_search().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient failure"))
            } else {
                Ok(vec![MediaItem {
                    id: "ok-1".to_string(),
                    title: "Recovered".to_string(),
                    ..Default::default()
                }])
            }
        });

        let retrying = RetryingBackendClient::new(Arc::new(client), 1, Duration::from_millis(1));
        let result = retrying.search("query").await.unwrap();

        assert_eq!(result.len(), 1);
        assert_eq!(result[0].id, "ok-1");
    }

    #[tokio::test]
    async fn browse_returns_last_error_when_retries_exhausted() {
        let mut client = MockBackendClient::new();
        client
            .expect_fetch_children()
            .times(3)
            .returning(|_| Err(anyhow::anyhow!("still failing")));

        let retrying = RetryingBackendClient::new(Arc::new(client), 2, Duration::from_millis(1));
        let error = retrying.fetch_children("root").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("fetch_children failed after 3 attempt(s)")
        );
    }

    #[tokio::test]
    async fn fetch_children_retries_until_success() {
        let mut client = MockBackendClient::new();
        let mut calls = 0;

        client.expect_fetch_children().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("temporary network error"))
            } else {
                Ok(vec![MediaItem {
                    id: "child-1".to_string(),
                    title: "Recovered Child".to_string(),
                    ..Default::default()
                }])
            }
        });

        let retrying = RetryingBackendClient::new(Arc::new(client), 1, Duration::from_millis(1));
        let result = retrying.fetch_children("root").await.unwrap();

        assert_eq!(result.len(), 1);
        assert_eq!(result[0].id, "child-1");
    }

    #[tokio::test]
    async fn search_with_zero_retries_fails_immediately() {
        let mut client = MockBackendClient::new();
        client
            .expect_search()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("no retry path")));

        let retrying = RetryingBackendClient::new(Arc::new(client), 0, Duration::from_millis(1));
        let error = retrying.search("query").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("search failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn search_non_retryable_error_fails_immediately_even_when_retries_configured() {
        let mut client = MockBackendClient::new();
        client
            .expect_search()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("fatal: invalid request")));

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let error = retrying.search("query").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("search failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn fetch_children_non_retryable_error_fails_immediately_even_when_retries_configured() {
        let mut client = MockBackendClient::new();
        client
            .expect_fetch_children()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("fatal: parent does not exist")));

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let error = retrying.fetch_children("root").await.unwrap_err();

        assert!(
            error
                .to_string()
                .contains("fetch_children failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn search_retryable_error_retries_and_succeeds_with_policy() {
        let mut client = MockBackendClient::new();
        let mut calls = 0;

        client.expect_search().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient: backend unavailable"))
            } else {
                Ok(vec![MediaItem {
                    id: "policy-ok-1".to_string(),
                    title: "Recovered by policy retry".to_string(),
                    ..Default::default()
                }])
            }
        });

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let result = retrying.search("query").await.unwrap();

        assert_eq!(result.len(), 1);
        assert_eq!(result[0].id, "policy-ok-1");
    }

    #[tokio::test]
    async fn jitter_strategy_can_be_combined_with_retry_policy() {
        let mut client = MockBackendClient::new();
        client
            .expect_search()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("fatal: invalid request")));

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_exponential_backoff_and_jitter(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            Duration::from_millis(4),
            20,
        )
        .with_retry_policy(retry_only_transient);

        let error = retrying.search("query").await.unwrap_err();
        assert!(
            error
                .to_string()
                .contains("search failed after 1 attempt(s)")
        );
    }

    #[tokio::test]
    async fn fetch_children_retryable_error_retries_and_succeeds_with_policy() {
        let mut client = MockBackendClient::new();
        let mut calls = 0;

        client.expect_fetch_children().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient: backend unavailable"))
            } else {
                Ok(vec![MediaItem {
                    id: "policy-child-1".to_string(),
                    title: "Recovered child by policy retry".to_string(),
                    ..Default::default()
                }])
            }
        });

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let result = retrying.fetch_children("root").await.unwrap();

        assert_eq!(result.len(), 1);
        assert_eq!(result[0].id, "policy-child-1");
    }

    #[tokio::test]
    async fn search_stream_retryable_error_retries_and_succeeds_with_policy() {
        let mut client = MockBackendClient::new();
        let mut calls = 0;

        client.expect_search_stream().times(2).returning(move |_| {
            calls += 1;
            if calls == 1 {
                Err(anyhow::anyhow!("transient: stream startup unavailable"))
            } else {
                Ok(Box::pin(tokio_stream::iter(vec![Ok(MediaItem {
                    id: "stream-policy-ok-1".to_string(),
                    title: "Recovered stream by policy retry".to_string(),
                    ..Default::default()
                })])))
            }
        });

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let mut stream = retrying.search_stream("query").await.unwrap();
        let first = stream.next().await.unwrap().unwrap();

        assert_eq!(first.id, "stream-policy-ok-1");
        assert!(stream.next().await.is_none());
    }

    #[tokio::test]
    async fn search_stream_non_retryable_error_fails_immediately_even_when_retries_configured() {
        let mut client = MockBackendClient::new();
        client
            .expect_search_stream()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("fatal: invalid stream query")));

        fn retry_only_transient(error: &anyhow::Error) -> bool {
            error.to_string().contains("transient")
        }

        let retrying = RetryingBackendClient::new_with_policy(
            Arc::new(client),
            3,
            Duration::from_millis(1),
            retry_only_transient,
        );
        let error = match retrying.search_stream("query").await {
            Ok(_) => panic!("expected search_stream to fail for non-retryable error"),
            Err(error) => error,
        };

        assert!(
            error
                .to_string()
                .contains("search_stream failed after 1 attempt(s)")
        );
    }
}
