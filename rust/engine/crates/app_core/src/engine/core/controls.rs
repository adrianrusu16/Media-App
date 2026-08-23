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
        let is_playing = matches!(
            snapshot.playback_state,
            PlaybackState::Playing | PlaybackState::Recovering
        );
        let has_current_item = self.queue.has_current();

        PlayerControls {
            play_pause: ControlState {
                is_visible: true,
                is_enabled: can_dispatch,
                is_active: is_playing,
            },
            skip_next: ControlState {
                is_visible: true,
                is_enabled: can_dispatch && self.queue.has_next(),
                is_active: false,
            },
            skip_prev: ControlState {
                is_visible: true,
                // Previous may restart the current item even at the first
                // queue position, so policy—not just queue geometry—decides it.
                is_enabled: can_dispatch && has_current_item,
                is_active: false,
            },
            show_play_icon: !is_playing,
        }
    }
}
