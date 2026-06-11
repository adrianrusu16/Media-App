use serde::{Deserialize, Serialize};

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
