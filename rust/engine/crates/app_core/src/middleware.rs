use crate::command::EngineCommand;
use crate::error::{EngineError, EngineErrorType};
use crate::observability::EventBus;
use crate::reducer::{Engine, EngineOutcome};
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
        if command.command_type == crate::command::EngineCommandType::Play {
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
            warn!("[Recovery] Non-fatal network error detected. Attempting to skip to next track...");

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
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        if command.command_type == crate::command::EngineCommandType::Play && _engine.snapshot().session.is_none() {
             warn!("[Validation] Play command received without an active session. This may be ignored by the reducer.");
        }
    }
}

/// A composite middleware that runs a list of middlewares in order.
#[derive(Default)]
pub struct MiddlewarePipeline {
    middlewares: Vec<Box<dyn Middleware>>,
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
