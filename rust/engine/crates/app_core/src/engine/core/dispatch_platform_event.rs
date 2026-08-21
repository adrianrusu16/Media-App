use super::*;
use crate::model::playback::{DrivingState, RestrictionState};

impl Engine {
    pub(super) async fn dispatch_platform_event_impl(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        let observation = event.payload.as_deref().and_then(|payload| {
            serde_json::from_str::<crate::model::platform_event::PlaybackObservationPayload>(payload).ok()
        });
        if matches!(
            event.event_type,
            EnginePlatformEventType::MediaLoaded
                | EnginePlatformEventType::MediaError
                | EnginePlatformEventType::PlaybackCompleted
        ) && let Some(observation) = observation.as_ref()
            && (observation.version != 1
                || Some(observation.playback_instance_id) != self.current_playback_instance_id)
        {
            // A superseded item may still finish or fail. Its observation is
            // useful to the platform for logging but never authoritative here.
            return EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
                effects: Vec::new(),
            };
        }

        if event.event_type == EnginePlatformEventType::MediaError
            && observation.as_ref().is_some_and(|value| value.kind.as_deref() == Some("source_rejected"))
        {
            let instance_id = observation.expect("checked above").playback_instance_id;
            if self.source_retry_attempted_for == Some(instance_id) {
                self.snapshot = self.snapshot.clone()
                    .with_playback_state(PlaybackState::Error, now_epoch_millis)
                    .with_error(Some(EngineError::player_error("resolved source was rejected after refresh")));
            } else if let Some(media) = self.queue.current_item().cloned() {
                self.source_retry_attempted_for = Some(instance_id);
                match self.resolve_playback_source(&media).await {
                    Ok(resolved) => {
                        let mut effects = Vec::new();
                        let snapshot = self.snapshot.clone().with_playback_state(PlaybackState::Buffering, now_epoch_millis);
                        self.snapshot = self.update_media_state(&resolved, snapshot, &mut effects);
                        self.source_retry_attempted_for = self.current_playback_instance_id;
                        self.snapshot.controls = self.derive_controls(&self.snapshot);
                        effects.push(EngineEffect::RequestAudioFocus);
                        effects.push(EngineEffect::Play);
                        let outcome = EngineOutcome {
                            snapshot: self.snapshot.clone(),
                            event: EngineEvent::platform_event_applied(Some(event.event_type.as_wire().to_owned())),
                            effects,
                        };
                        self.execute_effects(&outcome.effects);
                        return outcome;
                    }
                    Err(error) => self.snapshot = self.snapshot.clone()
                        .with_playback_state(PlaybackState::Error, now_epoch_millis)
                        .with_error(Some(error)),
                }
            }
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

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_platform_event(prev_playback_state, &event.event_type);

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
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                }
                _ => {}
            },
            _ => {}
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);
        self.sync_auth_state_projection();

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
