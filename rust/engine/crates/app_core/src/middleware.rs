use crate::command::EngineCommand;
use crate::reducer::{Engine, EngineOutcome};

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
        println!("[PandaEngine] Dispatching command: {:?}", command.command_type);
    }
}

/// A middleware that tracks engine performance and command usage (placeholder).
pub struct TelemetryMiddleware;
impl Middleware for TelemetryMiddleware {
    fn after_dispatch(&self, _engine: &Engine, outcome: &EngineOutcome) {
        // In a real app, this would send data to an analytics service
        println!(
            "[Telemetry] Command result: {:?}, New state: {:?}",
            outcome.event.event_type, outcome.snapshot.playback_state
        );
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
