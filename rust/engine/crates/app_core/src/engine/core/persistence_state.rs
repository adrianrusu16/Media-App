use super::*;

impl Engine {
    /// Tries to restore the engine state from persistence.
    pub fn restore(&mut self) -> Result<bool, String> {
        if let Some(state) = self.persistence.load()? {
            self.snapshot = state.snapshot;
            self.queue = state.queue;

            if self.snapshot.playback_state == PlaybackState::Playing {
                if self.config.auto_resume {
                    self.snapshot.playback_state = PlaybackState::Buffering;

                    if let Some(media_id) = &self.snapshot.media_id {
                        let effects = vec![
                            EngineEffect::PreparePlaybackSource {
                                media_id: media_id.clone(),
                                playback_instance_id: self.current_playback_instance_id.unwrap_or_default(),
                            },
                            EngineEffect::UpdateMetadata {
                                media_id: media_id.clone(),
                                title: self.snapshot.title.clone().unwrap_or_default(),
                                artist: self.snapshot.artist.clone().unwrap_or_default(),
                            },
                            EngineEffect::Seek(self.snapshot.position_millis),
                            EngineEffect::Play,
                        ];
                        self.execute_effects(&effects);
                    }
                } else {
                    self.snapshot.playback_state = PlaybackState::Paused;
                    if let Some(media_id) = &self.snapshot.media_id {
                        let effects = vec![
                            EngineEffect::PreparePlaybackSource {
                                media_id: media_id.clone(),
                                playback_instance_id: self.current_playback_instance_id.unwrap_or_default(),
                            },
                            EngineEffect::UpdateMetadata {
                                media_id: media_id.clone(),
                                title: self.snapshot.title.clone().unwrap_or_default(),
                                artist: self.snapshot.artist.clone().unwrap_or_default(),
                            },
                            EngineEffect::Seek(self.snapshot.position_millis),
                        ];
                        self.execute_effects(&effects);
                    }
                }
            } else if let Some(media_id) = &self.snapshot.media_id {
                let effects = vec![
                    EngineEffect::PreparePlaybackSource {
                        media_id: media_id.clone(),
                        playback_instance_id: self.current_playback_instance_id.unwrap_or_default(),
                    },
                    EngineEffect::UpdateMetadata {
                        media_id: media_id.clone(),
                        title: self.snapshot.title.clone().unwrap_or_default(),
                        artist: self.snapshot.artist.clone().unwrap_or_default(),
                    },
                    EngineEffect::Seek(self.snapshot.position_millis),
                ];
                self.execute_effects(&effects);
            }

            self.refresh_controls();
            Ok(true)
        } else {
            Ok(false)
        }
    }

    /// Saves the current engine state to persistence.
    pub fn save(&self) -> Result<(), String> {
        let state = EnginePersistentState {
            snapshot: self.snapshot.clone(),
            queue: self.queue.clone(),
        };
        self.persistence.save(&state)
    }
}
