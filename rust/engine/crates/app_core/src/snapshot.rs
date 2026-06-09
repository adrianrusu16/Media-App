use crate::playback::{PlaybackState, RestrictionState};
use crate::session::MediaSession;

/// Represents the state-of-the-art snapshot of the media engine at a specific point in time.
///
/// This structure is the "Single Source of Truth" for the UI and other platform components.
/// It is immutable and should only be updated through the engine's reducer.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct EngineSnapshot {
    /// The current playback status (e.g., Playing, Paused).
    pub playback_state: PlaybackState,
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
}

impl EngineSnapshot {
    /// Creates a new idle snapshot, typically used as the initial state.
    pub fn idle(now_epoch_millis: u64) -> Self {
        Self {
            playback_state: PlaybackState::Idle,
            updated_at_epoch_millis: now_epoch_millis,
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
}
