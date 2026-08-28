use crate::data::repository::MediaItem;
use crate::data::session::MediaSession;
use crate::model::auth::AuthState;
use crate::model::backend::{BackendAvailability, EngineBackendStatus};
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
    /// Current credential-free authentication projection.
    #[serde(default)]
    pub auth_state: AuthState,
    #[serde(default)]
    pub protected_account: Option<crate::Account>,
    #[serde(default)]
    pub device_sessions: Vec<crate::AuthSession>,
    #[serde(default)]
    pub device_sessions_next_page_token: Option<crate::EnginePageToken>,
    /// The current playback status (e.g., Playing, Paused).
    pub playback_state: PlaybackState,
    /// The last error that occurred, if any.
    pub last_error: Option<EngineError>,
    /// Latest successfully retrieved backend health projection.
    #[serde(default)]
    pub backend_status: Option<EngineBackendStatus>,
    /// Dynamic reachability of the configured backend. This stays separate
    /// from the last successful health payload so an outage never invalidates
    /// local engine state.
    #[serde(default)]
    pub backend_availability: BackendAvailability,
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
    /// Opaque Canopy artwork id for the current media item.
    #[serde(default)]
    pub artwork_id: Option<String>,
    /// Artwork content hash (version key) for the current media item.
    #[serde(default)]
    pub artwork_content_hash: Option<String>,
    /// URI for the current playable media source.
    pub source_uri: Option<String>,
    /// MIME type for the current playable media source.
    pub mime_type: Option<String>,
    /// Expiry of the current opaque playback capability, in Unix epoch milliseconds.
    #[serde(default)]
    pub playback_expires_at_epoch_millis: Option<u64>,
    /// The ID of the user currently interacting with the engine.
    pub user_id: Option<String>,
    /// Theme preference projected from local cache, local input, or an authenticated profile.
    #[serde(default)]
    pub theme_preference: ThemePreferenceState,
    /// Credential-free protected profile projection for the active account.
    #[serde(default)]
    pub profile: Option<crate::model::profile::EngineProfile>,
    /// Server-backed playback-history consent for the current authenticated identity.
    #[serde(default)]
    pub history_settings: Option<crate::EngineHistorySettings>,
    /// Lightweight playback-history state used to invalidate paged projections.
    #[serde(default)]
    pub history_state: crate::EngineHistoryState,
    /// Current engine-owned playback-history page projection.
    #[serde(default)]
    pub history_entries: Vec<crate::EngineHistoryEntry>,
    /// Opaque continuation token retained by PandaEngine.
    #[serde(default)]
    pub history_next_page_token: Option<crate::EnginePageToken>,
    /// Deleted count returned by the latest history purge operation.
    #[serde(default)]
    pub history_deleted_count: u64,
    /// Engine-authoritative saved relationships for the active identity.
    #[serde(default)]
    pub saved_tracks: Vec<crate::EngineLibraryTrack>,
    #[serde(default)]
    pub saved_tracks_next_page_token: Option<crate::EnginePageToken>,
    /// Engine-authoritative liked relationships for the active identity.
    #[serde(default)]
    pub liked_tracks: Vec<crate::EngineLibraryTrack>,
    #[serde(default)]
    pub liked_tracks_next_page_token: Option<crate::EnginePageToken>,
    /// Track ids with a protected mutation awaiting server acknowledgement.
    #[serde(default)]
    pub library_pending_track_ids: Vec<String>,
    /// Engine-authoritative playlist list for the active identity.
    #[serde(default)]
    pub playlists: Vec<crate::EnginePlaylist>,
    #[serde(default)]
    pub playlists_next_page_token: Option<crate::EnginePageToken>,
    /// Tracks of the currently selected playlist, retained only for the active identity.
    #[serde(default)]
    pub playlist_tracks: Vec<crate::EnginePlaylistTrack>,
    #[serde(default)]
    pub playlist_tracks_playlist_id: Option<String>,
    #[serde(default)]
    pub playlist_tracks_next_page_token: Option<crate::EnginePageToken>,
    /// A conflict proposal which must be explicitly confirmed by a new reorder command.
    #[serde(default)]
    pub playlist_reconciliation: Option<crate::PlaylistReconciliation>,
    /// Complete remote profile preference document, including unknown application keys.
    #[serde(default)]
    pub profile_preferences: serde_json::Map<String, serde_json::Value>,
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
    /// The results of the latest authenticated discovery-feed operation.
    #[serde(default)]
    pub discovery_results: Vec<MediaItem>,
    /// Opaque continuation token retained by PandaEngine for discovery pagination.
    #[serde(default)]
    pub discovery_next_page_token: Option<crate::EnginePageToken>,
    /// The results of the latest authenticated for-you feed operation.
    #[serde(default)]
    pub for_you_results: Vec<MediaItem>,
    /// The results of the latest authenticated recommendations operation.
    #[serde(default)]
    pub recommendations_results: Vec<MediaItem>,
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
    /// Authoritative PandaEngine playback-queue size. Never inferred from Media3.
    #[serde(default)]
    pub queue_size: usize,
    /// Current occurrence index in the active queue, if any.
    #[serde(default)]
    pub queue_current_index: Option<usize>,
    /// Generation that advances when QueueManager replaces its items.
    #[serde(default)]
    pub queue_generation: u64,
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
        // A source load is deliberately supersedable: transport commands must
        // remain valid while buffering so a newer selection can replace it.
        !self.is_busy
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

    /// Functional update for the latest backend status.
    #[must_use]
    pub fn with_backend_status(mut self, status: Option<EngineBackendStatus>) -> Self {
        self.backend_status = status;
        self
    }

    /// Functional update for backend reachability.
    #[must_use]
    pub fn with_backend_availability(mut self, availability: BackendAvailability) -> Self {
        self.backend_availability = availability;
        self
    }

    /// Functional update for the current opaque playback capability expiry.
    #[must_use]
    pub fn with_playback_expiry(mut self, expires_at_epoch_millis: Option<u64>) -> Self {
        if self.playback_expires_at_epoch_millis != expires_at_epoch_millis {
            self.metadata_revision = self.metadata_revision.saturating_add(1);
        }
        self.playback_expires_at_epoch_millis = expires_at_epoch_millis;
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
            || self.artwork_id.as_deref() != media.artwork_id.as_deref()
            || self.artwork_content_hash.as_deref() != media.artwork_content_hash.as_deref()
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
        self.artwork_id = media.artwork_id;
        self.artwork_content_hash = media.artwork_content_hash;
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

    /// Functional update for discovery-feed results.
    #[must_use]
    pub fn with_discovery_results(mut self, results: Vec<MediaItem>) -> Self {
        self.discovery_results = results;
        self
    }

    /// Functional update for the engine-owned discovery continuation token.
    #[must_use]
    pub fn with_discovery_next_page_token(mut self, token: Option<crate::EnginePageToken>) -> Self {
        self.discovery_next_page_token = token;
        self
    }

    #[must_use]
    pub fn with_for_you_results(mut self, results: Vec<MediaItem>) -> Self {
        self.for_you_results = results;
        self
    }

    #[must_use]
    pub fn with_recommendations_results(mut self, results: Vec<MediaItem>) -> Self {
        self.recommendations_results = results;
        self
    }

    #[must_use]
    pub fn with_feed_results(self, feed: crate::DiscoveryFeed, results: Vec<MediaItem>) -> Self {
        match feed {
            crate::DiscoveryFeed::Discovery => self.with_discovery_results(results),
            crate::DiscoveryFeed::ForYou => self.with_for_you_results(results),
            crate::DiscoveryFeed::Recommendations => self.with_recommendations_results(results),
        }
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

    /// Functional update for the playback duration, returning a new snapshot.
    ///
    /// Prefer the platform decoder duration over catalog ingest when the player
    /// reports a positive length. Interpolation clamps to this clock.
    #[must_use]
    pub fn with_duration(mut self, duration_millis: Option<u64>) -> Self {
        self.duration_millis = duration_millis;
        self
    }

    /// Functional update for the progress timing baseline, returning a new snapshot.
    ///
    /// Host interpolation must follow this clock, not `updated_at_epoch_millis`.
    /// Command/catalog snapshots move `updated_at` without rebasing playback.
    #[must_use]
    pub fn with_progress_tick(mut self, epoch_millis: u64) -> Self {
        self.last_progress_tick_epoch_millis = epoch_millis;
        self
    }

    /// Rebase the platform-owned position to the instant a transport command
    /// is handled. Position checkpoints arrive asynchronously from Media3,
    /// so a pause command can otherwise expose the last checkpoint and make
    /// the UI jump backwards by the time spent playing since that checkpoint.
    ///
    /// This is only a command-time baseline; subsequent platform checkpoints
    /// remain authoritative and can correct the estimate.
    #[must_use]
    pub fn with_position_rebased_at(mut self, now_epoch_millis: u64) -> Self {
        let elapsed_millis = now_epoch_millis.saturating_sub(self.last_progress_tick_epoch_millis);
        let speed = if self.playback_speed.is_finite() {
            self.playback_speed.max(0.0)
        } else {
            0.0
        };
        let elapsed_position_millis = (elapsed_millis as f64 * f64::from(speed)) as u64;
        let rebased_position_millis = self.position_millis.saturating_add(elapsed_position_millis);
        self.position_millis = self
            .duration_millis
            .map_or(rebased_position_millis, |duration| {
                rebased_position_millis.min(duration)
            });
        self.last_progress_tick_epoch_millis = now_epoch_millis;
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
    fn can_dispatch_is_blocked_when_busy_but_not_buffering() {
        let idle = EngineSnapshot::idle(1);
        assert!(idle.can_dispatch());

        let busy = idle.clone().with_busy(true);
        assert!(!busy.can_dispatch());

        let buffering = idle.with_playback_state(PlaybackState::Buffering, 2);
        assert!(buffering.can_dispatch());
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
