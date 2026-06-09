use crate::engine::core::{Engine, EngineOutcome};
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::error::{EngineError, EngineErrorType};
use crate::model::event::{EngineEvent, EngineEventType};
use crate::model::playback::PlaybackState;
use crate::engine::observability::EventBus;
use std::sync::Arc;
use tracing::{info, warn};

/// Middleware trait for processing engine operations.
///
/// Middleware can be used to inject logic before or after a command is dispatched,
/// such as logging, telemetry, or modifying the command/outcome.
pub trait Middleware: Send + Sync {
    /// Called before the command is dispatched to the engine.
    fn before_dispatch(&self, _engine: &Engine, _command: &EngineCommand) {}

    /// Called after the command has been dispatched and the outcome generated.
    fn after_dispatch(&self, _engine: &mut Engine, _outcome: &mut EngineOutcome) {}
}

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
        // Notify the bus about the outcome and event
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
            // In a real app, this might trigger a platform call or internal check
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

            // Dispatch SkipNext to recover
            let recovery_outcome = engine.dispatch(
                EngineCommand::skip_next(),
                outcome.snapshot.updated_at_epoch_millis,
            );

            // Update the current outcome with the recovery result
            *outcome = recovery_outcome;

            // Add a notification that we recovered
            outcome.snapshot.last_error = Some(EngineError::media_skipped(
                "Skipped track due to network error",
            ));
        }
    }
}

/// A middleware that validates commands against business rules before they reach the reducer.
pub struct ValidationMiddleware;
impl Middleware for ValidationMiddleware {
    fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        let snapshot = engine.snapshot();

        // Block commands if the engine is busy/buffering
        if !snapshot.can_dispatch() {
            warn!(
                "[Validation] Rejecting command {:?} because the engine is currently busy (state: {:?}, is_busy: {})",
                command.command_type, snapshot.playback_state, snapshot.is_busy
            );
            // In a future version, we could inject a cancellation flag into the EngineOutcome
            // but for now, the warning serves as an audit log for the middleware decision.
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

    fn should_throttle(&self, command: &EngineCommand, now: u64) -> bool {
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
            // In a more advanced implementation, we could set a flag in the command
            // to mark it as rejected/ignored by the middleware.
        }
    }
}

/// A composite middleware that runs a list of middlewares in order.
#[derive(Default)]
pub struct MiddlewarePipeline {
    middlewares: Vec<Box<dyn Middleware>>,
}

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
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        match command.command_type {
            EngineCommandType::Play => {
                self.report("play_requested", None, "{}");
            }
            EngineCommandType::Pause => {
                self.report("pause_requested", None, "{}");
            }
            EngineCommandType::SkipNext => {
                self.report("skip_next_requested", None, "{}");
            }
            EngineCommandType::SkipPrevious => {
                self.report("skip_prev_requested", None, "{}");
            }
            _ => {}
        }
    }

    fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        let snapshot = engine.snapshot();

        // Report state transitions
        if outcome.event.event_type == EngineEventType::CommandApplied {
            self.report(
                "state_transition",
                snapshot.media_id.as_deref(),
                &format!("{{\"to_state\": \"{:?}\"}}", snapshot.playback_state),
            );
        }

        // Heartbeat logic during playback
        if snapshot.playback_state == PlaybackState::Playing {
            let now = snapshot.updated_at_epoch_millis;
            let last = self
                .last_heartbeat_at
                .load(std::sync::atomic::Ordering::Relaxed);

            // Send heartbeat every 10 seconds (10000ms)
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

impl MiddlewarePipeline {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn add(&mut self, middleware: Box<dyn Middleware>) {
        self.middlewares.push(middleware);
    }

    pub fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        for mw in &self.middlewares {
            mw.before_dispatch(engine, command);
        }
    }

    pub fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        for mw in &self.middlewares {
            mw.after_dispatch(engine, outcome);
        }
    }
}
