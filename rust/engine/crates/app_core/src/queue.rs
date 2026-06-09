use serde::{Deserialize, Serialize};
use crate::repository::MediaItem;

/// Defines the playback mode for the queue.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum RepeatMode {
    /// No repeat, stop after the last item.
    #[default]
    None,
    /// Repeat the current track.
    One,
    /// Repeat the entire queue.
    All,
}

/// Manages the list of media items to be played.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct QueueManager {
    items: Vec<MediaItem>,
    current_index: Option<usize>,
    shuffle_enabled: bool,
    repeat_mode: RepeatMode,
}

impl Default for QueueManager {
    fn default() -> Self {
        Self {
            items: Vec::new(),
            current_index: None,
            shuffle_enabled: false,
            repeat_mode: RepeatMode::default(),
        }
    }
}

impl QueueManager {
    /// Creates a new [QueueManager] with the given items.
    pub fn new(items: Vec<MediaItem>) -> Self {
        Self {
            items,
            current_index: None,
            shuffle_enabled: false,
            repeat_mode: RepeatMode::None,
        }
    }

    /// Returns the current media item, if any.
    pub fn current_item(&self) -> Option<&MediaItem> {
        self.current_index.and_then(|idx| self.items.get(idx))
    }

    /// Sets the current index in the queue.
    pub fn set_current_index(&mut self, index: usize) {
        if index < self.items.len() {
            self.current_index = Some(index);
        }
    }

    /// Moves to the next item in the queue based on the repeat mode.
    pub fn next_item(&mut self) -> Option<&MediaItem> {
        if self.items.is_empty() {
            return None;
        }

        let current = self.current_index.unwrap_or(0);
        let next = match self.repeat_mode {
            RepeatMode::One => current,
            RepeatMode::All => (current + 1) % self.items.len(),
            RepeatMode::None => {
                if current + 1 < self.items.len() {
                    current + 1
                } else {
                    current // or None? For now, stay on last
                }
            }
        };

        self.current_index = Some(next);
        self.current_item()
    }

    /// Moves to the previous item in the queue.
    pub fn previous_item(&mut self) -> Option<&MediaItem> {
        if self.items.is_empty() {
            return None;
        }

        let current = self.current_index.unwrap_or(0);
        let prev = if current == 0 {
            if self.repeat_mode == RepeatMode::All {
                self.items.len() - 1
            } else {
                0
            }
        } else {
            current - 1
        };

        self.current_index = Some(prev);
        self.current_item()
    }

    /// Sets whether shuffle is enabled.
    pub fn set_shuffle(&mut self, enabled: bool) {
        self.shuffle_enabled = enabled;
        // In a real implementation, we might reshuffle the items here.
    }

    /// Sets the repeat mode.
    pub fn set_repeat_mode(&mut self, mode: RepeatMode) {
        self.repeat_mode = mode;
    }

    /// Replaces the entire queue.
    pub fn set_items(&mut self, items: Vec<MediaItem>) {
        self.items = items;
        self.current_index = if self.items.is_empty() { None } else { Some(0) };
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mock_items() -> Vec<MediaItem> {
        vec![
            MediaItem { id: "1".to_string(), title: "S1".to_string(), artist: "A1".to_string() },
            MediaItem { id: "2".to_string(), title: "S2".to_string(), artist: "A2".to_string() },
        ]
    }

    #[test]
    fn test_next_item_wraps_in_repeat_all() {
        let mut qm = QueueManager::new(mock_items());
        qm.set_repeat_mode(RepeatMode::All);
        qm.set_current_index(1);
        
        let next = qm.next_item().unwrap();
        assert_eq!(next.id, "1");
    }

    #[test]
    fn test_previous_item_wraps_in_repeat_all() {
        let mut qm = QueueManager::new(mock_items());
        qm.set_repeat_mode(RepeatMode::All);
        qm.set_current_index(0);

        let prev = qm.previous_item().unwrap();
        assert_eq!(prev.id, "2");
    }
}
