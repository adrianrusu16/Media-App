use std::sync::Arc;
use std::time::Duration;

use crate::data::repository::MediaItem;
use crate::networking::backend_client::BackendClient;
use anyhow::Context;

pub struct RetryingBackendClient {
    inner: Arc<dyn BackendClient>,
    max_retries: usize,
    retry_delay: Duration,
    retry_if: fn(&anyhow::Error) -> bool,
}

impl RetryingBackendClient {
    pub fn new(inner: Arc<dyn BackendClient>, max_retries: usize, retry_delay: Duration) -> Self {
        Self {
            inner,
            max_retries,
            retry_delay,
            retry_if: |_| true,
        }
    }

    pub fn new_with_policy(
        inner: Arc<dyn BackendClient>,
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
impl BackendClient for RetryingBackendClient {
    async fn fetch_children(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        let mut attempts = 0;
        loop {
            match self.inner.fetch_children(parent_id).await {
                Ok(items) => return Ok(items),
                Err(error) if attempts < self.max_retries && (self.retry_if)(&error) => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
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
        loop {
            match self.inner.search(query).await {
                Ok(items) => return Ok(items),
                Err(error) if attempts < self.max_retries && (self.retry_if)(&error) => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => {
                    return Err(error)
                        .context(format!("search failed after {} attempt(s)", attempts + 1));
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::backend_client::MockBackendClient;

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
}
