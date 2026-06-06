use crate::playback::{PlaybackState, RestrictionState};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineSnapshot {
    pub playback_state: PlaybackState,
    pub media_id: Option<String>,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub user_id: Option<String>,
    pub restriction_state: RestrictionState,
    pub updated_at_epoch_millis: u64,
}

impl EngineSnapshot {
    pub fn idle(now_epoch_millis: u64) -> Self {
        Self {
            playback_state: PlaybackState::Idle,
            media_id: None,
            title: None,
            artist: None,
            user_id: None,
            restriction_state: RestrictionState::Unknown,
            updated_at_epoch_millis: now_epoch_millis,
        }
    }

    pub fn with_playback_state(
        mut self,
        playback_state: PlaybackState,
        now_epoch_millis: u64,
    ) -> Self {
        self.playback_state = playback_state;
        self.updated_at_epoch_millis = now_epoch_millis;
        self
    }
}
