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

    /// Resolves a selected media item into a playable source before projection.
    pub(super) async fn resolve_playback_source(
        &self,
        media: &crate::data::repository::MediaItem,
    ) -> Result<crate::data::repository::MediaItem, crate::model::error::EngineError> {
        if media.source_uri.is_some() {
            return Ok(media.clone());
        }

        let Some(audio_source_client) = &self.audio_source_client else {
            return Ok(media.clone());
        };

        let source = audio_source_client
            .resolve_track(&media.id)
            .await
            .map_err(|error| {
                crate::model::error::EngineError::new(
                    crate::model::error::EngineErrorType::NetworkError,
                    format!(
                        "Failed to resolve playback source for media_id={}: {}",
                        media.id, error
                    ),
                    true,
                )
            })?;

        let mut resolved_media = media.clone();
        resolved_media.source_uri = Some(source.uri);
        resolved_media.mime_type = source.mime_type.or(resolved_media.mime_type);
        if resolved_media.duration_millis.is_none() {
            resolved_media.duration_millis = source.expected_duration_ms;
        }

        Ok(resolved_media)
    }
}
