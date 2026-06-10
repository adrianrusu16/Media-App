use crate::data::repository::MediaItem;

/// Abstract, transport-agnostic client for talking to a remote media backend.
///
/// This is the single seam through which the engine reaches out to the network.
/// Concrete implementations (e.g., a tonic/gRPC client) live behind this trait,
/// so the engine and the data layer never depend on a specific transport.
///
/// In test builds, `mockall` auto-generates a `MockBackendClient` that can be
/// configured with custom return values, argument matchers, call-count
/// expectations, and (via `returning` closures) slow/error behavior. This makes
/// it easy to simulate backend latency, timeouts, and failures in unit tests.
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait BackendClient: Send + Sync {
    /// Fetches the children of the given parent node from the remote backend.
    async fn fetch_children(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>>;

    /// Executes a search query against the remote backend.
    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>>;
}
