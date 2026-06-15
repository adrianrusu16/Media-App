use super::*;

impl Engine {
    pub(super) async fn dispatch_command(
        &mut self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        info!(
            "Dispatching command: {:?} at {}",
            command.command_type, now_epoch_millis
        );
        let middleware = Arc::clone(&self.middleware);
        if let Err(error) = middleware.before_dispatch(self, &command) {
            let command_wire = command.command_type.as_wire().to_owned();
            self.snapshot = self
                .snapshot
                .clone()
                .with_error(Some(error.clone()))
                .with_busy(false);

            return EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::command_applied(Some(format!(
                    "{}:rejected_by_middleware",
                    command_wire
                ))),
                effects: Vec::new(),
            };
        }

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_command(prev_playback_state, &command.command_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis)
            .with_error(None)
            .with_busy(false);

        if prev_playback_state != PlaybackState::Playing
            && next_playback_state == PlaybackState::Playing
        {
            next_snapshot = next_snapshot.with_progress_tick(now_epoch_millis);
        }

        let mut effects = Vec::new();

        match &command.command_type {
            EngineCommandType::StartSession { user_id } => {
                let session_id = format!("session-{}", now_epoch_millis);
                let session =
                    MediaSession::new(session_id.clone(), user_id.clone(), now_epoch_millis);
                next_snapshot = next_snapshot.with_session(Some(session));
                effects.push(EngineEffect::SessionStarted { session_id });
            }
            EngineCommandType::EndSession => {
                next_snapshot = next_snapshot.with_session(None);
                effects.push(EngineEffect::SessionEnded);
            }
            EngineCommandType::SkipNext => {
                if let Some(next_media) = self.queue.next_item() {
                    next_snapshot =
                        Self::update_media_state(next_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::SkipPrevious => {
                if let Some(prev_media) = self.queue.previous_item() {
                    next_snapshot =
                        Self::update_media_state(prev_media, next_snapshot, &mut effects);
                }
            }
            EngineCommandType::Play => {
                if next_snapshot.session.is_some() {
                    if self.snapshot.media_id.is_none() {
                        if let Some(media) = self.queue.current_item() {
                            next_snapshot =
                                Self::update_media_state(media, next_snapshot, &mut effects);
                        } else if let Some(media) = self.queue.next_item() {
                            next_snapshot =
                                Self::update_media_state(media, next_snapshot, &mut effects);
                        }
                    }
                } else {
                    next_snapshot.playback_state = PlaybackState::Idle;
                }
            }
            EngineCommandType::Search { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query).await;
                next_snapshot = next_snapshot
                    .with_search_results(results.unwrap_or_default())
                    .with_busy(false);
            }
            EngineCommandType::Browse { parent_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.browse(parent_id).await;
                next_snapshot = next_snapshot
                    .with_browse_results(results.unwrap_or_default())
                    .with_busy(false);
            }
            EngineCommandType::SetSpeed { speed } => {
                next_snapshot = next_snapshot.with_speed(*speed);
                effects.push(EngineEffect::SetSpeed(*speed));
            }
            EngineCommandType::Seek { position_millis } => {
                next_snapshot = next_snapshot
                    .with_position(*position_millis)
                    .with_progress_tick(now_epoch_millis);
                effects.push(EngineEffect::Seek(*position_millis));
            }
            EngineCommandType::UpdateConfig { config } => {
                self.config = config.clone();
                info!("Engine configuration updated: {:?}", config);
            }
            EngineCommandType::StartVoiceInteraction => {
                if let Some(ve) = &mut self.voice_engine {
                    ve.reset();
                    let context = if let (Some(title), Some(artist)) =
                        (&self.snapshot.title, &self.snapshot.artist)
                    {
                        Some((title.clone(), artist.clone()))
                    } else {
                        None
                    };
                    ve.set_context(context);
                }
                effects.push(EngineEffect::DuckAudio);
                effects.push(EngineEffect::StartAudioCapture);
                info!("Voice interaction started (audio ducked)");
            }
            EngineCommandType::StopVoiceInteraction => {
                let mut resolved_cmd = None;
                if let Some(ve) = &mut self.voice_engine {
                    match ve.finish() {
                        Ok(VoiceInteractionResult::Command(cmd)) => {
                            info!("Voice command determined: {:?}", cmd);
                            resolved_cmd = Some(cmd);
                        }
                        Ok(VoiceInteractionResult::Error(err)) => {
                            warn!("Voice interaction error: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                        Ok(VoiceInteractionResult::NoMatch) => {
                            info!("No voice command match found");
                            effects.push(EngineEffect::NotifyUser {
                                message: "I didn't catch that. Could you repeat?".to_string(),
                            });
                        }
                        Err(err) => {
                            warn!("Voice engine failure: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                    }
                } else {
                    info!("Voice interaction stopped (no internal engine)");
                }

                if let Some(cmd) = resolved_cmd {
                    let outcome = Box::pin(self.dispatch(cmd, now_epoch_millis)).await;
                    effects.extend(outcome.effects);
                    next_snapshot = outcome.snapshot;
                }

                effects.push(EngineEffect::StopAudioCapture);
                effects.push(EngineEffect::UnduckAudio);
                next_snapshot = next_snapshot.with_busy(false).with_voice_hypothesis(None);
            }
            EngineCommandType::ProcessVoiceAudio { chunk } => {
                if let Some(ve) = &mut self.voice_engine {
                    let _ = ve.process_audio_chunk(chunk).map_err(|e| {
                        warn!("Failed to process voice audio chunk: {}", e);
                    });
                    let hypothesis = ve.get_partial_hypothesis();
                    next_snapshot = next_snapshot.with_voice_hypothesis(if hypothesis.is_empty() {
                        None
                    } else {
                        Some(hypothesis)
                    });
                }
            }
            EngineCommandType::VoicePlay { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query).await.unwrap_or_default();
                if let Some(first) = results.first() {
                    let media = first.clone();
                    next_snapshot = Self::update_media_state(&media, next_snapshot, &mut effects);
                    next_snapshot.playback_state = PlaybackState::Buffering;
                } else {
                    info!("Voice search found no results for: {}", query);
                    effects.push(EngineEffect::NotifyUser {
                        message: format!("No results found for {}", query),
                    });
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::PlayMediaById { media_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                if let Some(media) = self.repository.get_by_id(media_id) {
                    next_snapshot = Self::update_media_state(&media, next_snapshot, &mut effects);
                    next_snapshot.playback_state = PlaybackState::Buffering;
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::SetSleepTimer { duration_millis } => {
                use crate::services::service::SleepTimerService;
                if let Some(timer_service) =
                    self.service_manager.find_service::<SleepTimerService>()
                {
                    match duration_millis {
                        Some(duration) => {
                            let fire_at = now_epoch_millis + duration;
                            timer_service.set_timer(fire_at);
                            info!("Sleep timer set to fire in {}ms", duration);
                        }
                        None => {
                            timer_service.set_timer(0);
                            info!("Sleep timer cancelled");
                        }
                    }
                }
            }
            _ => {}
        }

        match (prev_playback_state, next_snapshot.playback_state) {
            (prev, next) if prev != next => match next {
                PlaybackState::Buffering => {
                    effects.push(EngineEffect::RequestAudioFocus);
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Playing => {
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Paused => {
                    effects.push(EngineEffect::Pause);
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                    effects.push(EngineEffect::AbandonAudioFocus);
                }
                _ => {}
            },
            _ => {}
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);

        if self.snapshot.playback_state != prev_playback_state {
            info!(
                "Playback state transition: {:?} -> {:?}",
                prev_playback_state, self.snapshot.playback_state
            );
        }

        let mut outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(Some(command.command_type.as_wire().to_owned())),
            effects,
        };

        let middleware = Arc::clone(&self.middleware);
        middleware.after_dispatch(self, &mut outcome);

        self.execute_effects(&outcome.effects);

        outcome
    }
}
