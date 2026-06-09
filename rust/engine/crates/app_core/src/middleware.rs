use crate::command::EngineCommand;
use crate::observability::EventBus;
use crate::reducer::{Engine, EngineOutcome};
use std::sync::Arc;

/// Middleware trait for processing engine operations.
///
/// Middleware can be used to inject logic before or after a command is dispatched,
/// such as logging, telemetry, or modifying the command/outcome.
pub trait Middleware: Send + Sync {
    /// Called before the command is dispatched to the engine.
    fn before_dispatch(&self, _engine: &Engine, _command: &EngineCommand) {}

    /// Called after the command has been dispatched and the outcome generated.
    fn after_dispatch(&self, _engine: &Engine, _outcome: &EngineOutcome) {}
}

/// A simple middleware that logs engine actions.
pub struct LoggerMiddleware;
impl Middleware for LoggerMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        println!(
            "[PandaEngine] Dispatching command: {:?}",
            command.command_type
        );
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
        println!("[Telemetry] Starting command: {:?}", command.command_type);
    }

    fn after_dispatch(&self, _engine: &Engine, outcome: &EngineOutcome) {
        // Notify the bus about the outcome and event
        self.bus.notify_state_changed(&outcome.snapshot);
        self.bus.notify_event_emitted(&outcome.event);

        println!(
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
            println!("[FocusMiddleware] Requesting audio focus before Play...");
            // In a real app, this might trigger a platform call or internal check
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

    pub fn after_dispatch(&self, engine: &Engine, outcome: &EngineOutcome) {
        for mw in &self.middlewares {
            mw.after_dispatch(engine, outcome);
        }
    }
}
