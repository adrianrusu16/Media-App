use crate::data::repository::MediaItem;
use crate::data::session::MediaSession;
use crate::model::error::EngineError;
use crate::model::playback::{DrivingState, PlaybackState, PlayerControls, RestrictionState};
use crate::model::preferences::ThemePreferenceState;

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
    /// Displayable album name of the current media.
    pub album: Option<String>,
    /// Duration of the current media item in milliseconds.
    pub duration_millis: Option<u64>,
    /// Thumbnail/artwork URL for the current media item.
    pub thumbnail_url: Option<String>,
    /// URI for the current playable media source.
    pub source_uri: Option<String>,
    /// MIME type for the current playable media source.
    pub mime_type: Option<String>,
    /// The ID of the user currently interacting with the engine.
    pub user_id: Option<String>,
    /// Theme preference projected from local cache, local input, or an authenticated profile.
    #[serde(default)]
    pub theme_preference: ThemePreferenceState,
    /// Current restrictions applied to the media (e.g., UX restrictions).
    pub restriction_state: RestrictionState,
    /// Current vehicle motion state reported by the platform.
    #[serde(default)]
    pub driving_state: DrivingState,
    /// Unix timestamp in milliseconds when this snapshot was created/updated.
    pub updated_at_epoch_millis: u64,
    /// Unix timestamp in milliseconds for the last playback-progress baseline.
    ///
    /// Unlike `updated_at_epoch_millis`, this value is dedicated to progress tracking
    /// and should only move when playback timing is intentionally advanced/rebased.
    #[serde(default)]
    pub last_progress_tick_epoch_millis: u64,
    /// Monotonic revision for metadata queried across the native host boundary.
    ///
    /// This advances when session/user identity or current media metadata changes,
    /// allowing Android to cache string-heavy metadata separately from the compact
    /// numeric snapshot.
    #[serde(default)]
    pub metadata_revision: u64,
    /// The active media session, if any.
    pub session: Option<MediaSession>,
    /// The results of the last search operation.
    pub search_results: Vec<MediaItem>,
    /// The results of the last browse operation.
    pub browse_results: Vec<MediaItem>,
    /// The current playback speed (1.0 is normal).
    pub playback_speed: f32,
    /// The current playback position in milliseconds.
    pub position_millis: u64,
    /// Indicates if the engine is currently busy (e.g., buffering, searching) and might ignore new commands.
    pub is_busy: bool,
    /// The state of player controls to be displayed in the UI.
    pub controls: PlayerControls,
    /// The current partial hypothesis for voice interaction.
    pub voice_hypothesis: Option<String>,
}

impl EngineSnapshot {
    /// Creates a new idle snapshot, typically used as the initial state.
    pub fn idle(now_epoch_millis: u64) -> Self {
        let mut snapshot = Self {
            playback_state: PlaybackState::Idle,
            updated_at_epoch_millis: now_epoch_millis,
            last_progress_tick_epoch_millis: now_epoch_millis,
            playback_speed: 1.0,
            position_millis: 0,
            is_busy: false,
            ..Default::default()
        };
        // Initialize controls with default visible/enabled states for Idle
        snapshot.controls.show_play_icon = true;
        snapshot.controls.play_pause.is_visible = true;
        snapshot.controls.play_pause.is_enabled = true;
        // Skip controls depend on queue, but for a fresh idle snapshot, we don't know the queue.
        // The reducer's new() will call derive_controls after creating the idle snapshot.
        snapshot
    }

    /// Returns true if the snapshot indicates that the engine can currently accept and process user commands.
    pub fn can_dispatch(&self) -> bool {
        !self.is_busy && self.playback_state != PlaybackState::Buffering
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
        if self.session != session {
            self.metadata_revision = self.metadata_revision.saturating_add(1);
        }
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

    /// Functional update for media metadata, returning a new snapshot.
    #[must_use]
    pub fn with_media(mut self, media: MediaItem) -> Self {
        let metadata_changed = self.media_id.as_deref() != Some(media.id.as_str())
            || self.title.as_deref() != Some(media.title.as_str())
            || self.artist.as_deref() != Some(media.artist.as_str())
            || self.album.as_deref() != media.album.as_deref()
            || self.duration_millis != media.duration_millis
            || self.thumbnail_url.as_deref() != media.thumbnail_url.as_deref()
            || self.source_uri.as_deref() != media.source_uri.as_deref()
            || self.mime_type.as_deref() != media.mime_type.as_deref();

        if metadata_changed {
            self.metadata_revision = self.metadata_revision.saturating_add(1);
        }
        self.media_id = Some(media.id);
        self.title = Some(media.title);
        self.artist = Some(media.artist);
        self.album = media.album;
        self.duration_millis = media.duration_millis;
        self.thumbnail_url = media.thumbnail_url;
        self.source_uri = media.source_uri;
        self.mime_type = media.mime_type;
        self
    }

    /// Functional update for browse results, returning a new snapshot.
    #[must_use]
    pub fn with_browse_results(mut self, results: Vec<MediaItem>) -> Self {
        self.browse_results = results;
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

    /// Functional update for the progress timing baseline, returning a new snapshot.
    #[must_use]
    pub fn with_progress_tick(mut self, epoch_millis: u64) -> Self {
        self.last_progress_tick_epoch_millis = epoch_millis;
        self
    }

    /// Functional update for the busy state, returning a new snapshot.
    #[must_use]
    pub fn with_busy(mut self, is_busy: bool) -> Self {
        self.is_busy = is_busy;
        self
    }

    /// Functional update for the player controls, returning a new snapshot.
    #[must_use]
    pub fn with_controls(mut self, controls: PlayerControls) -> Self {
        self.controls = controls;
        self
    }

    /// Functional update for the voice hypothesis, returning a new snapshot.
    #[must_use]
    pub fn with_voice_hypothesis(mut self, hypothesis: Option<String>) -> Self {
        self.voice_hypothesis = hypothesis;
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::playback::PlaybackState;

    #[test]
    fn idle_snapshot_has_expected_defaults() {
        let snapshot = EngineSnapshot::idle(123);
        assert_eq!(snapshot.playback_state, PlaybackState::Idle);
        assert_eq!(snapshot.updated_at_epoch_millis, 123);
        assert_eq!(snapshot.last_progress_tick_epoch_millis, 123);
        assert_eq!(snapshot.metadata_revision, 0);
        assert_eq!(snapshot.playback_speed, 1.0);
        assert!(!snapshot.is_busy);
        assert!(snapshot.controls.show_play_icon);
        assert!(snapshot.controls.play_pause.is_visible);
        assert!(snapshot.controls.play_pause.is_enabled);
    }

    #[test]
    fn can_dispatch_is_blocked_when_busy_or_buffering() {
        let idle = EngineSnapshot::idle(1);
        assert!(idle.can_dispatch());

        let busy = idle.clone().with_busy(true);
        assert!(!busy.can_dispatch());

        let buffering = idle.with_playback_state(PlaybackState::Buffering, 2);
        assert!(!buffering.can_dispatch());
    }

    #[test]
    fn metadata_revision_advances_when_queried_metadata_changes() {
        use crate::data::repository::MediaItem;
        use crate::data::session::MediaSession;

        let session = MediaSession::new("session-1".to_string(), "user-1".to_string(), 10);
        let media = MediaItem {
            id: "media-1".to_string(),
            title: "Song".to_string(),
            artist: "Artist".to_string(),
            ..Default::default()
        };

        let snapshot = EngineSnapshot::idle(1);
        let snapshot = snapshot.with_playback_state(PlaybackState::Playing, 2);
        assert_eq!(snapshot.metadata_revision, 0);

        let snapshot = snapshot.with_session(Some(session));
        assert_eq!(snapshot.metadata_revision, 1);

        let snapshot = snapshot.with_media(media.clone());
        assert_eq!(snapshot.metadata_revision, 2);

        let snapshot = snapshot.with_media(media);
        assert_eq!(snapshot.metadata_revision, 2);
    }
}
