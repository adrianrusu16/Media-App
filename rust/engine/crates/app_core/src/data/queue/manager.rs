use crate::data::repository::MediaItem;

use super::RepeatMode;

/// Manages the list of media items to be played.
#[derive(Clone, Debug, Default, serde::Serialize, serde::Deserialize)]
pub struct QueueManager {
    items: Vec<MediaItem>,
    current_index: Option<usize>,
    shuffle_enabled: bool,
    repeat_mode: RepeatMode,
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
    ///
    /// With repeat disabled, reaching either end is intentionally a no-op:
    /// callers receive `None` and the cursor remains unchanged. Treating an
    /// unavailable target as the current item prevents the engine from
    /// distinguishing a real selection from a boundary press.
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
                    return None;
                }
            }
        };

        self.current_index = Some(next);
        self.current_item()
    }

    /// Moves to the previous item in the queue.
    ///
    /// With repeat disabled, the first item has no previous queue target. A
    /// transport policy may still restart it, but that is not a queue mutation.
    pub fn previous_item(&mut self) -> Option<&MediaItem> {
        if self.items.is_empty() {
            return None;
        }

        let current = self.current_index.unwrap_or(0);
        let prev = if current == 0 {
            if self.repeat_mode == RepeatMode::All {
                self.items.len() - 1
            } else {
                return None;
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

    /// Returns true if there is a next item in the queue.
    pub fn has_next(&self) -> bool {
        if self.items.is_empty() {
            return false;
        }
        if self.repeat_mode != RepeatMode::None {
            return true;
        }
        let current = self.current_index.unwrap_or(0);
        current + 1 < self.items.len()
    }

    /// Returns true if there is a previous item in the queue.
    pub fn has_previous(&self) -> bool {
        if self.items.is_empty() {
            return false;
        }
        if self.repeat_mode != RepeatMode::None {
            return true;
        }
        let current = self.current_index.unwrap_or(0);
        current > 0
    }

    /// Returns the current index in the queue.
    pub fn current_index(&self) -> Option<usize> {
        self.current_index
    }

    /// Returns whether the queue has a current selection that can be restarted.
    pub fn has_current(&self) -> bool {
        self.current_item().is_some()
    }
}
