use super::*;
use crate::model::playback::{DrivingState, RestrictionState};

impl Engine {
    pub(super) async fn dispatch_platform_event_impl(
        &mut self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
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
                .with_playback_state(self.snapshot.playback_state, now_epoch_millis)
                .with_error(None);
            self.record_playback_completion(event.payload.as_deref(), &mut next_snapshot)
                .await;
            self.snapshot = next_snapshot;
            let mut outcome = EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::platform_event_applied(Some(
                    EnginePlatformEventType::PLAYBACK_COMPLETED_WIRE.to_owned(),
                )),
                effects: Vec::new(),
            };
            let middleware = Arc::clone(&self.middleware);
            middleware.after_dispatch(self, &mut outcome);
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
