use crate::snapshot::EngineSnapshot;

/// Represents a media item in the system.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct MediaItem {
    pub id: String,
    pub title: String,
    pub artist: String,
}

/// Abstract definition for media data management.
///
/// This allows the engine to remain agnostic of the underlying data source
/// (e.g., SQLite, Network, Mock).
pub trait MediaRepository: Send + Sync {
    /// Retrieves a media item by its unique identifier.
    fn get_by_id(&self, id: &str) -> Option<MediaItem>;

    /// Returns the next item in the current queue relative to the provided ID.
    fn get_next(&self, current_id: &str) -> Option<MediaItem>;

    /// Returns the previous item in the current queue relative to the provided ID.
    fn get_previous(&self, current_id: &str) -> Option<MediaItem>;
}

/// A simple in-memory implementation of [MediaRepository].
pub struct InMemoryRepository {
    items: Vec<MediaItem>,
}

impl InMemoryRepository {
    /// Creates a new repository with a predefined list of items.
    pub fn new(items: Vec<MediaItem>) -> Self {
        Self { items }
    }
}

impl MediaRepository for InMemoryRepository {
    fn get_by_id(&self, id: &str) -> Option<MediaItem> {
        self.items.iter().find(|i| i.id == id).cloned()
    }

    fn get_next(&self, current_id: &str) -> Option<MediaItem> {
        let index = self.items.iter().position(|i| i.id == current_id)?;
        let next_index = (index + 1) % self.items.len();
        Some(self.items[next_index].clone())
    }

    fn get_previous(&self, current_id: &str) -> Option<MediaItem> {
        let index = self.items.iter().position(|i| i.id == current_id)?;
        let prev_index = if index == 0 {
            self.items.len() - 1
        } else {
            index - 1
        };
        Some(self.items[prev_index].clone())
    }
}

impl EngineSnapshot {
    /// Functional update for media metadata, returning a new snapshot.
    #[must_use]
    pub fn with_media(mut self, media: MediaItem) -> Self {
        self.media_id = Some(media.id);
        self.title = Some(media.title);
        self.artist = Some(media.artist);
        self
    }
}
