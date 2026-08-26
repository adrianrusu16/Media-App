use super::*;
use crate::model::playback::{DrivingState, RestrictionState};

impl Engine {
    pub(super) async fn dispatch_platform_event_impl(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        let observation = event.payload.as_deref().and_then(|payload| {
            serde_json::from_str::<crate::model::platform_event::PlaybackObservationPayload>(
                payload,
            )
            .ok()
        });
        let audio_focus_change = (event.event_type == EnginePlatformEventType::AudioFocusChanged)
            .then(|| {
                event.payload.as_deref().and_then(|payload| {
                    serde_json::from_str::<crate::model::platform_event::AudioFocusChangedPayload>(
                        payload,
                    )
                    .ok()
                    .filter(|value| value.version == 1)
                    .map(|value| value.focus_change)
                })
            })
            .flatten();
        let audio_focus_request_result = (event.event_type
            == EnginePlatformEventType::AudioFocusRequestResult)
            .then(|| {
                event.payload.as_deref().and_then(|payload| {
                    serde_json::from_str::<
                        crate::model::platform_event::AudioFocusRequestResultPayload,
                    >(payload)
                    .ok()
                    .filter(|value| value.version == 1)
                })
            })
            .flatten();
        if matches!(
            event.event_type,
            EnginePlatformEventType::MediaLoaded
                | EnginePlatformEventType::MediaError
                | EnginePlatformEventType::PlaybackCompleted
                | EnginePlatformEventType::PlaybackPositionCheckpoint
        ) && let Some(observation) = observation.as_ref()
            && (observation.version != 1
                || Some(observation.playback_instance_id) != self.current_playback_instance_id)
        {
            // A superseded item may still finish or fail. Its observation is
            // useful to the platform for logging but never authoritative here.
            debug!(
                platform_event = event.event_type.as_wire(),
                observed_playback_instance_id = observation.playback_instance_id,
                current_playback_instance_id = ?self.current_playback_instance_id,
                "Ignoring stale platform playback observation"
            );
            return EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    event.event_type.as_wire().to_owned(),
                )),
                effects: Vec::new(),
            };
        }

        if event.event_type == EnginePlatformEventType::MediaLoaded {
            self.pending_seek_target_millis = None;
        }

        if event.event_type == EnginePlatformEventType::PlaybackPositionCheckpoint {
            let Some(observation) = observation.as_ref() else {
                warn!("Ignoring malformed playback position checkpoint");
                return EngineOutcome {
                    snapshot: self.snapshot.clone(),
                    event: EngineEvent::platform_event_applied(Some(
                        EnginePlatformEventType::PLAYBACK_POSITION_CHECKPOINT_WIRE.to_owned(),
                    )),
                    effects: Vec::new(),
                };
            };
            let Some(position_millis) = observation.position_ms else {
                warn!(
                    playback_instance_id = observation.playback_instance_id,
                    "Ignoring playback position checkpoint without a position"
                );
                return EngineOutcome {
                    snapshot: self.snapshot.clone(),
                    event: EngineEvent::platform_event_applied(Some(
                        EnginePlatformEventType::PLAYBACK_POSITION_CHECKPOINT_WIRE.to_owned(),
                    )),
                    effects: Vec::new(),
                };
            };
            let reported_duration_millis = observation.duration_ms.filter(|duration| *duration > 0);
            let mut next_snapshot = self.snapshot.clone();
            if let Some(duration_millis) = reported_duration_millis {
                next_snapshot = next_snapshot.with_duration(Some(duration_millis));
            }
            // Clamp to the decoder duration when known. Do not clamp to catalog
            // ingest length: a shorter catalog label makes the UI hit 100% while
            // the player is still in the middle of the file.
            let safe_position_millis = next_snapshot
                .duration_millis
                .map_or(position_millis, |duration| position_millis.min(duration));
            if let Some(target) = self.pending_seek_target_millis {
                const SEEK_SETTLE_TOLERANCE_MILLIS: u64 = 1_500;
                if safe_position_millis.abs_diff(target) > SEEK_SETTLE_TOLERANCE_MILLIS {
                    debug!(
                        playback_instance_id = observation.playback_instance_id,
                        observed_position_millis = safe_position_millis,
                        seek_target_millis = target,
                        "Ignoring playback position checkpoint until seek settles"
                    );
                    return EngineOutcome {
                        snapshot: self.snapshot.clone(),
                        event: EngineEvent::platform_event_applied(Some(
                            EnginePlatformEventType::PLAYBACK_POSITION_CHECKPOINT_WIRE.to_owned(),
                        )),
                        effects: Vec::new(),
                    };
                }
                self.pending_seek_target_millis = None;
            }
            let previous_position_millis = self.snapshot.position_millis;
            let previous_tick = self.snapshot.last_progress_tick_epoch_millis;
            self.snapshot = next_snapshot
                .with_position(safe_position_millis)
                .with_progress_tick(now_epoch_millis);
            let jump_millis = safe_position_millis.abs_diff(previous_position_millis);
            let elapsed_millis = now_epoch_millis.saturating_sub(previous_tick);
            if jump_millis > elapsed_millis.saturating_add(1_500) && jump_millis > 1_000 {
                warn!(
                    playback_instance_id = observation.playback_instance_id,
                    previous_position_millis,
                    position_millis = safe_position_millis,
                    jump_millis,
                    elapsed_millis,
                    last_progress_tick_epoch_millis = now_epoch_millis,
                    "engine.playback.position_jump"
                );
            }
            debug!(
                playback_instance_id = observation.playback_instance_id,
                position_millis = safe_position_millis,
                last_progress_tick_epoch_millis = now_epoch_millis,
                "Playback position checkpoint accepted"
            );
            let mut snapshot = self.snapshot.clone();
            self.maybe_auto_record_history(now_epoch_millis, &mut snapshot)
                .await;
            self.snapshot = snapshot;
            return EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    EnginePlatformEventType::PLAYBACK_POSITION_CHECKPOINT_WIRE.to_owned(),
                )),
                effects: Vec::new(),
            };
        }

        if event.event_type == EnginePlatformEventType::MediaError
            && observation
                .as_ref()
                .is_some_and(|value| value.kind.as_deref() == Some("source_rejected"))
        {
            let instance_id = observation
                .as_ref()
                .expect("checked above")
                .playback_instance_id;
            if self.recovery.source_refresh_attempted_for == Some(instance_id) {
                self.snapshot = self
                    .snapshot
                    .clone()
                    .with_playback_state(PlaybackState::Error, now_epoch_millis)
                    .with_error(Some(EngineError::player_error(
                        "resolved source was rejected after refresh",
                    )));
            } else if let Some(media) = self.queue.current_item().cloned() {
                self.recovery.source_refresh_attempted_for = Some(instance_id);
                match self.resolve_playback_source(&media).await {
                    Ok(resolved) => {
                        let desired_play_when_ready = self.recovery.desired_play_when_ready;
                        let decoder_recovery_was_used =
                            self.recovery.decoder_attempted_for.is_some();
                        let mut effects = Vec::new();
                        let snapshot = self.snapshot.clone().with_playback_state(
                            if desired_play_when_ready {
                                PlaybackState::Buffering
                            } else {
                                PlaybackState::Paused
                            },
                            now_epoch_millis,
                        );
                        self.snapshot = self.update_media_state(&resolved, snapshot, &mut effects);
                        self.recovery.desired_play_when_ready = desired_play_when_ready;
                        if decoder_recovery_was_used {
                            // A refreshed Canopy capability starts a new source
                            // instance, but not a new logical-track recovery budget.
                            self.recovery.decoder_attempted_for = self.current_playback_instance_id;
                        }
                        self.recovery.source_refresh_attempted_for =
                            self.current_playback_instance_id;
                        self.snapshot.controls = self.derive_controls(&self.snapshot);
                        if self.recovery.desired_play_when_ready {
                            effects.push(EngineEffect::RequestAudioFocus);
                            effects.push(EngineEffect::Play);
                        }
                        let outcome = EngineOutcome {
                            snapshot: self.snapshot.clone(),
                            event: EngineEvent::platform_event_applied(Some(
                                event.event_type.as_wire().to_owned(),
                            )),
                            effects,
                        };
                        self.execute_effects(&outcome.effects);
                        return outcome;
                    }
                    Err(error) => {
                        self.snapshot = self
                            .snapshot
                            .clone()
                            .with_playback_state(PlaybackState::Error, now_epoch_millis)
                            .with_error(Some(error))
                    }
                }
            }
        }
        if event.event_type == EnginePlatformEventType::MediaError
            && observation
                .as_ref()
                .is_some_and(|value| value.kind.as_deref() == Some("decoder_failed"))
        {
            let instance_id = observation
                .as_ref()
                .expect("checked above")
                .playback_instance_id;
            let observation = observation.as_ref().expect("checked above");
            self.recovery.desired_play_when_ready = observation
                .play_when_ready
                .unwrap_or(self.recovery.desired_play_when_ready);
            warn!(
                playback_instance_id = instance_id,
                failure_position_ms = ?observation.position_ms,
                decoder = ?observation.decoder,
                error_code = ?observation.error_code,
                phase = ?observation.phase,
                "Media3 decoder failure observed"
            );
            if self.recovery.decoder_attempted_for != Some(instance_id)
                && let Some(media_id) = self.snapshot.media_id.clone()
            {
                // A decoder failure is local to the platform player. Keep the
                // resolved capability and give a fresh player one bounded retry.
                self.next_playback_instance_id = self.next_playback_instance_id.saturating_add(1);
                let replacement_instance_id = self.next_playback_instance_id;
                self.current_playback_instance_id = Some(replacement_instance_id);
                self.recovery.decoder_attempted_for = Some(replacement_instance_id);
                self.pending_seek_target_millis = Some(self.snapshot.position_millis);

                self.snapshot = self
                    .snapshot
                    .clone()
                    .with_playback_state(PlaybackState::Recovering, now_epoch_millis)
                    .with_error(None)
                    .with_busy(true);
                self.snapshot.controls = self.derive_controls(&self.snapshot);
                let mut effects = vec![EngineEffect::RecreatePlayerAndLoad {
                    media_id,
                    playback_instance_id: replacement_instance_id,
                    // Do not seek into the fatal sample. This is the engine's
                    // most recently confirmed safe position, not position_ms.
                    position_millis: self.snapshot.position_millis,
                }];
                if self.recovery.desired_play_when_ready {
                    effects.push(EngineEffect::RequestAudioFocus);
                    effects.push(EngineEffect::Play);
                }
                let outcome = EngineOutcome {
                    snapshot: self.snapshot.clone(),
                    event: EngineEvent::platform_event_applied(Some(
                        event.event_type.as_wire().to_owned(),
                    )),
                    effects,
                };
                self.execute_effects(&outcome.effects);
                return outcome;
            }

            self.snapshot = self
                .snapshot
                .clone()
                .with_playback_state(PlaybackState::Error, now_epoch_millis)
                .with_busy(false)
                .with_error(Some(EngineError::player_error(
                    "Playback could not continue because the device audio decoder failed.",
                )));
            self.snapshot.controls = self.derive_controls(&self.snapshot);
            let effects = vec![EngineEffect::NotifyUser {
                message: "Playback could not continue because the device audio decoder failed."
                    .to_owned(),
            }];
            let outcome = EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    event.event_type.as_wire().to_owned(),
                )),
                effects,
            };
            self.execute_effects(&outcome.effects);
            return outcome;
        }
        if event.event_type == EnginePlatformEventType::MediaButtonPressed
            && let Some(payload) = &event.payload
        {
            let command_type = EngineCommandType::from_wire(payload.clone());
            return self
                .dispatch(EngineCommand::new(command_type, None), now_epoch_millis)
                .await;
        }

        if event.event_type == EnginePlatformEventType::PlaybackCompleted {
            self.sync_auth_state_projection();
            let mut next_snapshot = self
                .snapshot
                .clone()
                .with_playback_state(PlaybackState::Ended, now_epoch_millis)
                .with_error(None);
            self.record_playback_completion(event.payload.as_deref(), &mut next_snapshot)
                .await;
            self.snapshot = next_snapshot;
            let mut outcome = EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    EnginePlatformEventType::PLAYBACK_COMPLETED_WIRE.to_owned(),
                )),
                // Completion does not advance the queue.  The player has already
                // stopped at its terminal position; stopping explicitly releases
                // platform resources and focus while exposing a stable Ended state.
                effects: vec![EngineEffect::Stop, EngineEffect::AbandonAudioFocus],
            };
            let middleware = Arc::clone(&self.middleware);
            middleware.after_dispatch(self, &mut outcome);
            self.execute_effects(&outcome.effects);
            return outcome;
        }

        if event.event_type == EnginePlatformEventType::AudioFocusRequestResult {
            let Some(request_result) = audio_focus_request_result else {
                warn!("Ignoring malformed audio focus request-result payload");
                return EngineOutcome {
                    snapshot: self.snapshot.clone(),
                    event: EngineEvent::platform_event_applied(Some(
                        EnginePlatformEventType::AUDIO_FOCUS_REQUEST_RESULT_WIRE.to_owned(),
                    )),
                    effects: Vec::new(),
                };
            };
            if request_result
                .playback_instance_id
                .is_some_and(|id| Some(id) != self.current_playback_instance_id)
            {
                debug!(
                    observed_playback_instance_id = ?request_result.playback_instance_id,
                    current_playback_instance_id = ?self.current_playback_instance_id,
                    "Ignoring stale audio focus request result"
                );
                return EngineOutcome {
                    snapshot: self.snapshot.clone(),
                    event: EngineEvent::platform_event_applied(Some(
                        EnginePlatformEventType::AUDIO_FOCUS_REQUEST_RESULT_WIRE.to_owned(),
                    )),
                    effects: Vec::new(),
                };
            }
            let prev_playback_state = self.snapshot.playback_state;
            use crate::model::platform_event::AudioFocusRequestResult;
            let next_playback_state = match request_result.result {
                AudioFocusRequestResult::Failed => {
                    self.recovery.desired_play_when_ready = false;
                    if matches!(
                        prev_playback_state,
                        PlaybackState::Playing
                            | PlaybackState::Buffering
                            | PlaybackState::Recovering
                    ) {
                        PlaybackState::Paused
                    } else {
                        prev_playback_state
                    }
                }
                AudioFocusRequestResult::Delayed => {
                    if matches!(
                        prev_playback_state,
                        PlaybackState::Playing
                            | PlaybackState::Buffering
                            | PlaybackState::Recovering
                    ) {
                        PlaybackState::Paused
                    } else {
                        prev_playback_state
                    }
                }
                AudioFocusRequestResult::Granted | AudioFocusRequestResult::Unknown => {
                    prev_playback_state
                }
            };
            info!(
                ?request_result.result,
                ?prev_playback_state,
                ?next_playback_state,
                desired_play_when_ready = self.recovery.desired_play_when_ready,
                "Audio focus request result applied"
            );
            let mut next_snapshot = self
                .snapshot
                .clone()
                .with_playback_state(next_playback_state, now_epoch_millis)
                .with_error(None);
            let effects = if prev_playback_state != PlaybackState::Paused
                && next_playback_state == PlaybackState::Paused
            {
                vec![EngineEffect::Pause]
            } else {
                Vec::new()
            };
            next_snapshot.controls = self.derive_controls(&next_snapshot);
            self.snapshot = next_snapshot;
            self.sync_auth_state_projection();
            let outcome = EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    EnginePlatformEventType::AUDIO_FOCUS_REQUEST_RESULT_WIRE.to_owned(),
                )),
                effects,
            };
            self.execute_effects(&outcome.effects);
            return outcome;
        }

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state = if event.event_type == EnginePlatformEventType::AudioFocusChanged
        {
            use crate::model::platform_event::AudioFocusChange;
            match audio_focus_change {
                Some(AudioFocusChange::Gain) if self.recovery.desired_play_when_ready => {
                    PlaybackState::Playing
                }
                Some(AudioFocusChange::Loss | AudioFocusChange::LossTransient)
                    if matches!(
                        prev_playback_state,
                        PlaybackState::Playing
                            | PlaybackState::Buffering
                            | PlaybackState::Recovering
                    ) =>
                {
                    PlaybackState::Paused
                }
                _ => prev_playback_state,
            }
        } else if prev_playback_state == PlaybackState::Recovering
            && event.event_type == EnginePlatformEventType::MediaLoaded
            && !self.recovery.desired_play_when_ready
        {
            PlaybackState::Paused
        } else {
            StateMachine::next_state_from_platform_event(prev_playback_state, &event.event_type)
        };

        if event.event_type == EnginePlatformEventType::AudioFocusChanged {
            match audio_focus_change {
                Some(crate::model::platform_event::AudioFocusChange::Loss) => {
                    self.recovery.desired_play_when_ready = false;
                    info!(
                        ?prev_playback_state,
                        ?next_playback_state,
                        "Permanent audio focus loss cleared playback intent"
                    );
                }
                Some(focus_change) => info!(
                    ?focus_change,
                    ?prev_playback_state,
                    ?next_playback_state,
                    desired_play_when_ready = self.recovery.desired_play_when_ready,
                    "Audio focus change applied"
                ),
                None => warn!("Ignoring malformed audio focus change payload"),
            }
        }

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis);

        match event.event_type {
            EnginePlatformEventType::VehicleDrivingStateChanged => {
                next_snapshot.driving_state = event
                    .payload
                    .as_deref()
                    .map(DrivingState::from_wire)
                    .unwrap_or_default();
            }
            EnginePlatformEventType::UxRestrictionsChanged => {
                next_snapshot.restriction_state = event
                    .payload
                    .as_deref()
                    .map(RestrictionState::from_wire)
                    .unwrap_or_default();
            }
            _ => {}
        }

        if prev_playback_state != PlaybackState::Playing
            && next_playback_state == PlaybackState::Playing
        {
            next_snapshot = next_snapshot.with_progress_tick(now_epoch_millis);
        }

        if event.event_type == EnginePlatformEventType::MediaLoaded {
            if let Some(duration_millis) = observation
                .as_ref()
                .and_then(|value| value.duration_ms)
                .filter(|duration| *duration > 0)
            {
                next_snapshot = next_snapshot.with_duration(Some(duration_millis));
            }
        }

        if next_playback_state == PlaybackState::Error {
            if let Some(payload) = &event.payload {
                let error = serde_json::from_str::<EngineError>(payload)
                    .unwrap_or_else(|_| EngineError::player_error(payload.clone()));
                next_snapshot = next_snapshot.with_error(Some(error));
            } else {
                next_snapshot = next_snapshot
                    .with_error(Some(EngineError::player_error("Unknown platform error")));
            }
        } else {
            next_snapshot = next_snapshot.with_error(None);
        }

        let mut effects = Vec::new();
        match (prev_playback_state, next_playback_state) {
            (prev, next) if prev != next => match next {
                PlaybackState::Paused => {
                    effects.push(EngineEffect::Pause);
                }
                PlaybackState::Playing => {
                    // Buffering already issued Play (playWhenReady). A second
                    // Play on MediaLoaded restarts the decoder mid-buffer.
                    if prev != PlaybackState::Buffering {
                        effects.push(EngineEffect::Play);
                    }
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                }
                _ => {}
            },
            _ => {}
        }
        if matches!(
            audio_focus_change,
            Some(crate::model::platform_event::AudioFocusChange::Gain)
        ) && self.recovery.desired_play_when_ready
            && !effects.contains(&EngineEffect::Play)
        {
            // A delayed focus grant can arrive after the engine already projected
            // Playing. Reassert the platform play effect without toggling intent.
            effects.push(EngineEffect::Play);
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);
        self.sync_auth_state_projection();
        let mut snapshot = self.snapshot.clone();
        self.maybe_auto_record_history(now_epoch_millis, &mut snapshot)
            .await;
        self.snapshot = snapshot;

        let middleware = Arc::clone(&self.middleware);
        let outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
            effects,
        };

        let mut outcome = outcome;
        middleware.after_dispatch(self, &mut outcome);
        self.execute_effects(&outcome.effects);

        outcome
    }
}
