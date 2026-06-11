use super::MediaItem;

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
}
