use super::{MediaItem, MediaRepository};

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

#[async_trait::async_trait]
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
        Ok(self
            .items
            .iter()
            .filter(|i| i.parent_id.as_deref() == Some(parent_id))
            .cloned()
            .collect())
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        let query = query.to_lowercase();
        Ok(self
            .items
            .iter()
            .filter(|i| {
                i.title.to_lowercase().contains(&query) || i.artist.to_lowercase().contains(&query)
            })
            .cloned()
            .collect())
    }
}
