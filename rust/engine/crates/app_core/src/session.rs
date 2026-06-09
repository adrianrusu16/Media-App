use serde::{Deserialize, Serialize};

/// Represents a media session, which manages the lifecycle of a playback sequence.
/// 
/// In AAOS, a MediaSession is critical for system integration, allowing the OS
/// to control playback, display metadata on the cluster, and manage audio focus.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct MediaSession {
    /// Unique identifier for the session.
    pub session_id: String,
    /// The user ID associated with this session.
    pub user_id: String,
    /// Whether the session is currently active.
    pub is_active: bool,
    /// Timestamp when the session was created.
    pub created_at_epoch_millis: u64,
}

impl MediaSession {
    /// Creates a new media session for the given user.
    pub fn new(session_id: String, user_id: String, now: u64) -> Self {
        Self {
            session_id,
            user_id,
            is_active: true,
            created_at_epoch_millis: now,
        }
    }
}
