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

fn apply_jitter(base_delay: Duration, _attempt_number: usize, jitter_percent: u8) -> Duration {
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
    let jittered_ms = min_ms + if spread == 0 { 0 } else { fastrand::u128(..=spread) };

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
mod tests;
