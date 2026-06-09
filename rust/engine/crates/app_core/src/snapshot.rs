use crate::playback::{PlaybackState, RestrictionState};
use crate::session::MediaSession;
use crate::error::EngineError;
use crate::repository::MediaItem;

use serde::{Deserialize, Serialize};

/// Represents the state-of-the-art snapshot of the media engine at a specific point in time.
///
/// This structure is the "Single Source of Truth" for the UI and other platform components.
/// It is immutable and should only be updated through the engine's reducer.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct EngineSnapshot {
    /// The current playback status (e.g., Playing, Paused).
    pub playback_state: PlaybackState,
    /// The last error that occurred, if any.
    pub last_error: Option<EngineError>,
    /// Unique identifier for the current media item.
    pub media_id: Option<String>,
    /// Displayable title of the current media.
    pub title: Option<String>,
    /// Displayable artist name of the current media.
    pub artist: Option<String>,
    /// The ID of the user currently interacting with the engine.
    pub user_id: Option<String>,
    /// Current restrictions applied to the media (e.g., UX restrictions).
    pub restriction_state: RestrictionState,
    /// Unix timestamp in milliseconds when this snapshot was created/updated.
    pub updated_at_epoch_millis: u64,
    /// The active media session, if any.
    pub session: Option<MediaSession>,
    /// The results of the last search or browse operation.
    pub search_results: Vec<MediaItem>,
    /// The current playback speed (1.0 is normal).
    pub playback_speed: f32,
    /// The current playback position in milliseconds.
    pub position_millis: u64,
}

impl EngineSnapshot {
    /// Creates a new idle snapshot, typically used as the initial state.
    pub fn idle(now_epoch_millis: u64) -> Self {
        Self {
            playback_state: PlaybackState::Idle,
            updated_at_epoch_millis: now_epoch_millis,
            playback_speed: 1.0,
            position_millis: 0,
            ..Default::default()
        }
    }

    /// Functional update for the playback state, returning a new snapshot.
    #[must_use]
    pub fn with_playback_state(
        mut self,
        playback_state: PlaybackState,
        now_epoch_millis: u64,
    ) -> Self {
        self.playback_state = playback_state;
        self.updated_at_epoch_millis = now_epoch_millis;
        self
    }

    /// Functional update for the session, returning a new snapshot.
    #[must_use]
    pub fn with_session(mut self, session: Option<MediaSession>) -> Self {
        self.session = session;
        self
    }

    /// Functional update for the error state, returning a new snapshot.
    #[must_use]
    pub fn with_error(mut self, error: Option<EngineError>) -> Self {
        self.last_error = error;
        self
    }

    /// Functional update for search results, returning a new snapshot.
    #[must_use]
    pub fn with_search_results(mut self, results: Vec<MediaItem>) -> Self {
        self.search_results = results;
        self
    }

    /// Functional update for the playback speed, returning a new snapshot.
    #[must_use]
    pub fn with_speed(mut self, speed: f32) -> Self {
        self.playback_speed = speed;
        self
    }

    /// Functional update for the playback position, returning a new snapshot.
    #[must_use]
    pub fn with_position(mut self, position_millis: u64) -> Self {
        self.position_millis = position_millis;
        self
    }
}
