use crate::engine::core::{Engine, EngineOutcome};
use crate::model::command::EngineCommand;
use crate::model::error::EngineError;

use crate::middleware::Middleware;

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

    pub fn before_dispatch(
        &self,
        engine: &Engine,
        command: &EngineCommand,
    ) -> Result<(), EngineError> {
        for mw in &self.middlewares {
            mw.before_dispatch(engine, command)?;
        }

        Ok(())
    }

    pub fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        for mw in &self.middlewares {
            mw.after_dispatch(engine, outcome);
        }
    }
}
