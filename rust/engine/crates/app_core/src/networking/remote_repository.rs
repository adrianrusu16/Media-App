use std::sync::Arc;

use crate::data::repository::{MediaItem, MediaRepository};
use crate::networking::backend_client::BackendClient;

/// A [`MediaRepository`] backed by a remote [`BackendClient`].
///
/// This is the layer that wires the engine's data-access abstraction to the
/// networking boundary. The repository stays transport-agnostic: it only knows
/// about the [`BackendClient`] trait and delegates the async, network-bound
/// operations (`browse`, `search`) to it. The concrete transport (tonic/gRPC,
/// etc.) is hidden inside whichever `BackendClient` is injected.
pub struct RemoteRepository<C> {
    client: Arc<C>,
}

impl<C> RemoteRepository<C>
where
    C: BackendClient,
{
    /// Creates a new repository that delegates remote calls to `client`.
    pub fn new(client: Arc<C>) -> Self {
        Self { client }
    }
}

#[async_trait::async_trait]
impl<C> MediaRepository for RemoteRepository<C>
where
    C: BackendClient,
{
    fn get_by_id(&self, _id: &str) -> Option<MediaItem> {
        // Synchronous lookups are not yet supported by the remote backend.
        // A future revision may add a local cache populated by browse/search.
        None
    }

    fn get_next(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    fn get_previous(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    async fn browse(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        self.client.fetch_children(parent_id).await
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        self.client.search(query).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::backend_client::MockBackendClient;

    #[tokio::test]
    async fn search_delegates_to_backend_client() {
        let mut client = MockBackendClient::new();
        client
            .expect_search()
            .withf(|query| query == "jazz")
            .times(1)
            .returning(|_| {
                Ok(vec![MediaItem {
                    id: "track-1".to_string(),
                    title: "Blue Train".to_string(),
                    ..Default::default()
                }])
            });

        let repo = RemoteRepository::new(Arc::new(client));
        let results = repo.search("jazz").await.unwrap();

        assert_eq!(results.len(), 1);
        assert_eq!(results[0].id, "track-1");
    }

    #[tokio::test]
    async fn browse_delegates_to_backend_client() {
        let mut client = MockBackendClient::new();
        client
            .expect_fetch_children()
            .withf(|parent| parent == "album-1")
            .times(1)
            .returning(|_| Ok(vec![MediaItem::default()]));

        let repo = RemoteRepository::new(Arc::new(client));
        let results = repo.browse("album-1").await.unwrap();

        assert_eq!(results.len(), 1);
    }

    #[tokio::test]
    async fn search_propagates_backend_error() {
        let mut client = MockBackendClient::new();
        client
            .expect_search()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("backend unavailable")));

        let repo = RemoteRepository::new(Arc::new(client));
        assert!(repo.search("anything").await.is_err());
    }

    #[tokio::test]
    async fn browse_propagates_backend_error() {
        let mut client = MockBackendClient::new();
        client
            .expect_fetch_children()
            .times(1)
            .returning(|_| Err(anyhow::anyhow!("upstream timeout")));

        let repo = RemoteRepository::new(Arc::new(client));
        assert!(repo.browse("root").await.is_err());
    }

    #[test]
    fn sync_navigation_methods_return_none_without_cache() {
        let client = MockBackendClient::new();
        let repo = RemoteRepository::new(Arc::new(client));

        assert!(repo.get_by_id("track-1").is_none());
        assert!(repo.get_next("track-1").is_none());
        assert!(repo.get_previous("track-1").is_none());
    }
}
