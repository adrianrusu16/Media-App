use crate::engine::core::{Engine, EngineOutcome};
use crate::engine::observability::EventBus;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::error::{EngineError, EngineErrorType};
use crate::model::event::EngineEvent;
use std::sync::Arc;
use tracing::{info, warn};

use crate::middleware::Middleware;

/// A simple middleware that logs engine actions.
pub struct LoggerMiddleware;
impl Middleware for LoggerMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        info!("Dispatching command: {:?}", command.command_type);
    }
}

/// A middleware that tracks engine performance and command usage.
pub struct TelemetryMiddleware {
    bus: Arc<EventBus>,
}

impl TelemetryMiddleware {
    pub fn new(bus: Arc<EventBus>) -> Self {
        Self { bus }
    }
}

impl Middleware for TelemetryMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        info!("[Telemetry] Starting command: {:?}", command.command_type);
    }

    fn after_dispatch(&self, _engine: &mut Engine, outcome: &mut EngineOutcome) {
        self.bus.notify_state_changed(&outcome.snapshot);
        self.bus.notify_event_emitted(&outcome.event);

        info!(
            "[Telemetry] Command completed. Event: {:?}, Effects: {}",
            outcome.event.event_type,
            outcome.effects.len()
        );
    }
}

/// A middleware that handles AAOS-specific focus logic or logging.
pub struct FocusMiddleware;
impl Middleware for FocusMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        if command.command_type == EngineCommandType::Play {
            info!("[FocusMiddleware] Requesting audio focus before Play...");
        }
    }
}

/// A middleware that automatically retries or recovers from certain errors.
pub struct RecoveryMiddleware;
impl Middleware for RecoveryMiddleware {
    fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        if let Some(error) = &outcome.snapshot.last_error
            && error.error_type == EngineErrorType::NetworkError
            && !error.is_fatal
        {
            warn!(
                "[Recovery] Non-fatal network error detected. Attempting to skip to next track..."
            );

            if let Some(next_media) = engine.queue().next_item() {
                let media = next_media.clone();
                outcome.snapshot = outcome.snapshot.clone().with_media(media);
            }

            outcome.snapshot.last_error = Some(EngineError::media_skipped(
                "Skipped track due to network error",
            ));
            outcome.event = EngineEvent::command_applied(Some(
                "recovered_from_network_error: skipped_to_next_track".to_string(),
            ));
        }
    }
}

/// A middleware that validates commands against business rules before they reach the reducer.
pub struct ValidationMiddleware;
impl Middleware for ValidationMiddleware {
    fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        let snapshot = engine.snapshot();

        if !snapshot.can_dispatch() {
            warn!(
                "[Validation] Rejecting command {:?} because the engine is currently busy (state: {:?}, is_busy: {})",
                command.command_type, snapshot.playback_state, snapshot.is_busy
            );
        }

        if command.command_type == EngineCommandType::Play && snapshot.session.is_none() {
            warn!(
                "[Validation] Play command received without an active session. This may be ignored by the reducer."
            );
        }
    }
}

/// A middleware that throttles commands to prevent rapid repeated executions (button mashing).
pub struct ThrottlingMiddleware {
    min_interval_ms: u64,
    last_command_at: std::sync::Mutex<std::collections::HashMap<String, u64>>,
}

impl ThrottlingMiddleware {
    pub fn new(min_interval_ms: u64) -> Self {
        Self {
            min_interval_ms,
            last_command_at: std::sync::Mutex::new(std::collections::HashMap::new()),
        }
    }

    pub(crate) fn should_throttle(&self, command: &EngineCommand, now: u64) -> bool {
        let key = format!("{:?}", command.command_type);
        let mut last_map = self.last_command_at.lock().unwrap();
        let last = last_map.get(&key).cloned().unwrap_or(0);

        if now < last + self.min_interval_ms {
            true
        } else {
            last_map.insert(key, now);
            false
        }
    }
}

impl Middleware for ThrottlingMiddleware {
    fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        let now = engine.snapshot().updated_at_epoch_millis;
        if self.should_throttle(command, now) {
            warn!(
                "[Throttling] Throttling command {:?} (too rapid)",
                command.command_type
            );
        }
    }
}