use super::*;

impl Engine {
    /// Executes any side effects by driving the player and other components.
    pub(super) fn execute_effects(&mut self, effects: &[EngineEffect]) {
        if let Some(player) = &mut self.player {
            for effect in effects {
                match effect {
                    EngineEffect::Play => player.play(),
                    EngineEffect::Pause => player.pause(),
                    EngineEffect::Stop => player.stop(),
                    EngineEffect::UpdateMetadata { media_id, .. } => player.prepare(media_id),
                    EngineEffect::Seek(position_millis) => player.seek(*position_millis),
                    EngineEffect::SetSpeed(speed) => player.set_speed(*speed),
                    _ => {}
                }
            }
        }
    }

    /// Helper to update the snapshot with new media and emit metadata effects.
    pub(super) fn update_media_state(
        media: &crate::data::repository::MediaItem,
        snapshot: EngineSnapshot,
        effects: &mut Vec<EngineEffect>,
    ) -> EngineSnapshot {
        let next_snapshot = snapshot.with_media(media.clone());
        effects.push(EngineEffect::UpdateMetadata {
            media_id: media.id.clone(),
            title: media.title.clone(),
            artist: media.artist.clone(),
        });
        next_snapshot
    }
}
