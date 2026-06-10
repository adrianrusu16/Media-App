use crate::model::snapshot::EngineSnapshot;

use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum MediaItemType {
    #[default]
    Track,
    Artist,
    Album,
    Folder,
    Playlist,
    RadioStation,
}

/// Represents a media item in the system.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct MediaItem {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub item_type: MediaItemType,
    pub parent_id: Option<String>,
}

/// Abstract definition for media data management.
///
/// This allows the engine to remain agnostic of the underlying data source
/// (e.g., SQLite, Network, Mock).
#[tonic::async_trait]
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

#[tonic::async_trait]
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

    async fn browse(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(self.items
            .iter()
            .filter(|i| i.parent_id.as_deref() == Some(parent_id))
            .cloned()
            .collect())
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        let query = query.to_lowercase();
        Ok(self.items
            .iter()
            .filter(|i| {
                i.title.to_lowercase().contains(&query) || i.artist.to_lowercase().contains(&query)
            })
            .cloned()
            .collect())
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

#[cfg(test)]
mod tests {
    use super::*;

    fn mock_items() -> Vec<MediaItem> {
        vec![
            MediaItem {
                id: "1".to_string(),
                title: "Song A".to_string(),
                artist: "Artist X".to_string(),
                item_type: MediaItemType::Track,
                parent_id: Some("album1".to_string()),
            },
            MediaItem {
                id: "2".to_string(),
                title: "Song B".to_string(),
                artist: "Artist Y".to_string(),
                item_type: MediaItemType::Track,
                parent_id: Some("album1".to_string()),
            },
            MediaItem {
                id: "3".to_string(),
                title: "Different".to_string(),
                artist: "Artist X".to_string(),
                item_type: MediaItemType::Track,
                parent_id: Some("album2".to_string()),
            },
        ]
    }

    #[test]
    fn test_get_by_id() {
        let repo = InMemoryRepository::new(mock_items());
        assert_eq!(repo.get_by_id("1").unwrap().title, "Song A");
        assert!(repo.get_by_id("99").is_none());
    }

    #[test]
    fn test_get_next_prev_wrap() {
        let repo = InMemoryRepository::new(mock_items());
        assert_eq!(repo.get_next("3").unwrap().id, "1");
        assert_eq!(repo.get_previous("1").unwrap().id, "3");
    }

    #[tokio::test]
    async fn test_browse() {
        let repo = InMemoryRepository::new(mock_items());
        let album1_items = repo.browse("album1").await.unwrap();
        assert_eq!(album1_items.len(), 2);
        assert!(album1_items.iter().all(|i| i.parent_id.as_deref() == Some("album1")));
        
        assert!(repo.browse("nonexistent").await.unwrap().is_empty());
    }

    #[tokio::test]
    async fn test_search() {
        let repo = InMemoryRepository::new(mock_items());
        
        // Search by title
        let results = repo.search("Song").await.unwrap();
        assert_eq!(results.len(), 2);
        
        // Search by artist
        let results = repo.search("Artist X").await.unwrap();
        assert_eq!(results.len(), 2);
        assert!(results.iter().any(|i| i.id == "1"));
        assert!(results.iter().any(|i| i.id == "3"));
        
        // Case-insensitive
        let results = repo.search("song").await.unwrap();
        assert_eq!(results.len(), 2);
        
        assert!(repo.search("xyz").await.unwrap().is_empty());
    }

    #[test]
    fn test_with_media_snapshot() {
        use crate::model::snapshot::EngineSnapshot;
        let snapshot = EngineSnapshot::default();
        let item = MediaItem {
            id: "1".to_string(),
            title: "T".to_string(),
            artist: "A".to_string(),
            ..Default::default()
        };
        let updated = snapshot.with_media(item);
        assert_eq!(updated.media_id, Some("1".to_string()));
        assert_eq!(updated.title, Some("T".to_string()));
        assert_eq!(updated.artist, Some("A".to_string()));
    }
}
