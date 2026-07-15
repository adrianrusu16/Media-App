use super::MediaItem;
use crate::{EngineError, EnginePageRequest, EnginePagedResult};

/// Abstract definition for media data management.
///
/// This allows the engine to remain agnostic of the underlying data source
/// (e.g., SQLite, Network, Mock).
///
/// In test builds, `mockall` auto-generates a `MockMediaRepository` that can be
/// configured with custom return values, argument matchers, call-count
/// expectations, and (via `returning` closures) slow/async behavior.
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait MediaRepository: Send + Sync {
    /// Retrieves a media item by its unique identifier.
    fn get_by_id(&self, id: &str) -> Option<MediaItem>;

    /// Returns the next item in the current queue relative to the provided ID.
    fn get_next(&self, current_id: &str) -> Option<MediaItem>;

    /// Returns the previous item in the current queue relative to the provided ID.
    fn get_previous(&self, current_id: &str) -> Option<MediaItem>;

    /// Returns a list of media items that are children of the specified parent ID.
    ///
    /// This is used for hierarchical browsing (e.g., Artist -> Album -> Track).
    async fn browse(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>>;

    /// Searches for media items matching the provided query string.
    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>>;

    /// Browses a catalog page while keeping continuation tokens engine-owned.
    async fn browse_catalog<'a>(
        &'a self,
        parent_id: Option<&'a str>,
        _genres: &[String],
        _page: EnginePageRequest,
    ) -> Result<EnginePagedResult<MediaItem>, EngineError> {
        let items = self
            .browse(parent_id.unwrap_or("root"))
            .await
            .map_err(|error| {
                EngineError::new(
                    crate::EngineErrorType::NetworkError,
                    error.to_string(),
                    false,
                )
            })?;
        Ok(EnginePagedResult {
            items,
            next_page_token: None,
        })
    }

    /// Searches a catalog page while keeping continuation tokens engine-owned.
    async fn search_catalog(
        &self,
        query: &str,
        _page: EnginePageRequest,
    ) -> Result<EnginePagedResult<MediaItem>, EngineError> {
        let items = self.search(query).await.map_err(|error| {
            EngineError::new(
                crate::EngineErrorType::NetworkError,
                error.to_string(),
                false,
            )
        })?;
        Ok(EnginePagedResult {
            items,
            next_page_token: None,
        })
    }
}
