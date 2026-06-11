use super::*;

impl Engine {
    /// Forces a refresh of the player controls based on the current state.
    pub fn refresh_controls(&mut self) {
        self.snapshot.controls = self.derive_controls(&self.snapshot);
    }

    /// Derives player controls from the current engine state.
    pub(super) fn derive_controls(
        &self,
        snapshot: &EngineSnapshot,
    ) -> crate::model::playback::PlayerControls {
        use crate::model::playback::{ControlState, PlayerControls};

        let can_dispatch = snapshot.can_dispatch();
        let is_playing = snapshot.playback_state == PlaybackState::Playing;
        let is_buffering = snapshot.playback_state == PlaybackState::Buffering;

        PlayerControls {
            play_pause: ControlState {
                is_visible: true,
                is_enabled: can_dispatch || is_buffering,
                is_active: is_playing,
            },
            skip_next: ControlState {
                is_visible: true,
                is_enabled: can_dispatch && self.queue.has_next(),
                is_active: false,
            },
            skip_prev: ControlState {
                is_visible: true,
                is_enabled: can_dispatch && self.queue.has_previous(),
                is_active: false,
            },
            show_play_icon: !is_playing,
        }
    }
}
