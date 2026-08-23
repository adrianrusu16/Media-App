use super::*;

pub(super) struct ResolvedPlaybackMedia {
    pub(super) media: crate::data::repository::MediaItem,
    pub(super) expires_at_epoch_millis: Option<u64>,
}

impl Engine {
    /// Executes any side effects by driving the player and other components.
    pub(super) fn execute_effects(&mut self, effects: &[EngineEffect]) {
        if let Some(player) = &mut self.player {
            for effect in effects {
                match effect {
                    EngineEffect::Play => player.play(),
                    EngineEffect::Pause => player.pause(),
                    EngineEffect::Stop => player.stop(),
                    EngineEffect::PreparePlaybackSource { media_id, .. } => {
                        player.prepare(media_id)
                    }
                    EngineEffect::Seek(position_millis) => player.seek(*position_millis),
                    EngineEffect::SetSpeed(speed) => player.set_speed(*speed),
                    _ => {}
                }
            }
        }
    }

    /// Helper to update the snapshot with new media and emit metadata effects.
    pub(super) fn update_media_state(
        &mut self,
        resolved: &ResolvedPlaybackMedia,
        snapshot: EngineSnapshot,
        effects: &mut Vec<EngineEffect>,
    ) -> EngineSnapshot {
        let media = &resolved.media;
        let next_snapshot = snapshot.with_media(media.clone());
        self.next_playback_instance_id = self.next_playback_instance_id.saturating_add(1);
        self.current_playback_instance_id = Some(self.next_playback_instance_id);
        self.recovery = PlaybackRecoveryState {
            desired_play_when_ready: true,
            ..Default::default()
        };
        effects.push(EngineEffect::PreparePlaybackSource {
            media_id: media.id.clone(),
            playback_instance_id: self.next_playback_instance_id,
        });
        effects.push(EngineEffect::UpdateMetadata {
            media_id: media.id.clone(),
            title: media.title.clone(),
            artist: media.artist.clone(),
        });
        next_snapshot.with_playback_expiry(resolved.expires_at_epoch_millis)
    }

    /// Resolves a selected media item into a playable source before projection.
    pub(super) async fn resolve_playback_source(
        &self,
        media: &crate::data::repository::MediaItem,
    ) -> Result<ResolvedPlaybackMedia, crate::model::error::EngineError> {
        if let Some(playback_port) = &self.playback_port {
            let source = playback_port.resolve_playback(&media.id).await?;
            let mut resolved_media = media.clone();
            resolved_media.source_uri = Some(source.url);
            resolved_media.mime_type = Some(source.content_type);
            resolved_media.duration_millis = Some(source.duration_millis);
            return Ok(ResolvedPlaybackMedia {
                media: resolved_media,
                expires_at_epoch_millis: Some(source.expires_at_epoch_millis),
            });
        }

        if media
            .source_uri
            .as_deref()
            .is_some_and(|uri| !uri.trim().is_empty())
        {
            return Ok(ResolvedPlaybackMedia {
                media: media.clone(),
                expires_at_epoch_millis: None,
            });
        }

        let Some(audio_source_client) = &self.audio_source_client else {
            return Err(crate::model::error::EngineError::new(
                crate::model::error::EngineErrorType::ServiceUnavailable,
                "playback resolver is not configured",
                false,
            ));
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

        Ok(ResolvedPlaybackMedia {
            media: resolved_media,
            expires_at_epoch_millis: None,
        })
    }
}
