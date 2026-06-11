use crate::model::snapshot::EngineSnapshot;

use super::MediaItem;

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
