use std::sync::Arc;
use std::time::Duration;

use crate::data::repository::MediaItem;
use crate::networking::backend_client::BackendClient;

pub struct RetryingBackendClient {
    inner: Arc<dyn BackendClient>,
    max_retries: usize,
    retry_delay: Duration,
}

impl RetryingBackendClient {
    pub fn new(inner: Arc<dyn BackendClient>, max_retries: usize, retry_delay: Duration) -> Self {
        Self {
            inner,
            max_retries,
            retry_delay,
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
                Err(error) if attempts < self.max_retries => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => return Err(error),
            }
        }
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        let mut attempts = 0;
        loop {
            match self.inner.search(query).await {
                Ok(items) => return Ok(items),
                Err(error) if attempts < self.max_retries => {
                    attempts += 1;
                    tokio::time::sleep(self.retry_delay).await;
                    let _ = error;
                }
                Err(error) => return Err(error),
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

        assert_eq!(error.to_string(), "still failing");
    }
}
