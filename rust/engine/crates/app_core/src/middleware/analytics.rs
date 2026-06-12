use crate::engine::core::{Engine, EngineOutcome};
use crate::engine::observability::EventBus;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::error::EngineError;
use crate::model::event::{EngineEvent, EngineEventType};
use crate::model::playback::PlaybackState;
use std::sync::Arc;

use crate::middleware::Middleware;

/// A middleware that tracks playback analytics and heartbeats.
pub struct AnalyticsMiddleware {
    bus: Arc<EventBus>,
    last_heartbeat_at: std::sync::atomic::AtomicU64,
}

impl AnalyticsMiddleware {
    pub fn new(bus: Arc<EventBus>) -> Self {
        Self {
            bus,
            last_heartbeat_at: std::sync::atomic::AtomicU64::new(0),
        }
    }

    fn report(&self, event_name: &str, media_id: Option<&str>, properties: &str) {
        let payload = format!(
            "{{\"event\": \"{}\", \"media_id\": {:?}, \"properties\": {}}}",
            event_name, media_id, properties
        );
        self.bus
            .notify_event_emitted(&EngineEvent::analytics_reported(payload));
    }
}

impl Middleware for AnalyticsMiddleware {
    fn before_dispatch(
        &self,
        _engine: &Engine,
        command: &EngineCommand,
    ) -> Result<(), EngineError> {
        match command.command_type {
            EngineCommandType::Play => self.report("play_requested", None, "{}"),
            EngineCommandType::Pause => self.report("pause_requested", None, "{}"),
            EngineCommandType::SkipNext => self.report("skip_next_requested", None, "{}"),
            EngineCommandType::SkipPrevious => self.report("skip_prev_requested", None, "{}"),
            _ => {}
        }

        Ok(())
    }

    fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        let snapshot = engine.snapshot();

        if outcome.event.event_type == EngineEventType::CommandApplied {
            self.report(
                "state_transition",
                snapshot.media_id.as_deref(),
                &format!("{{\"to_state\": \"{:?}\"}}", snapshot.playback_state),
            );
        }

        if snapshot.playback_state == PlaybackState::Playing {
            let now = snapshot.updated_at_epoch_millis;
            let last = self
                .last_heartbeat_at
                .load(std::sync::atomic::Ordering::Relaxed);

            if now >= last + 10000 {
                self.report(
                    "playback_heartbeat",
                    snapshot.media_id.as_deref(),
                    &format!("{{\"position_ms\": {}}}", snapshot.position_millis),
                );
                self.last_heartbeat_at
                    .store(now, std::sync::atomic::Ordering::Relaxed);
            }
        }
    }
}
