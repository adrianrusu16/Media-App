use crate::model::command::EngineCommand;
use crate::model::playback::PlaybackState;
use crate::engine::core::Engine;

/// Trait for background services that need to perform periodic work.
pub trait EngineService: Send + Sync {
    /// Returns the unique name of the service.
    fn name(&self) -> &'static str;

    /// Called periodically by the host to allow the service to perform work.
    fn on_tick(&self, engine: &Engine, now_epoch_millis: u64) -> Option<EngineCommand>;
}

/// A service that tracks and updates playback progress.
pub struct ProgressService;

impl EngineService for ProgressService {
    fn name(&self) -> &'static str {
        "ProgressService"
    }

    fn on_tick(&self, engine: &Engine, now_epoch_millis: u64) -> Option<EngineCommand> {
        let snapshot = engine.snapshot();

        // Only update progress if we are playing
        if snapshot.playback_state == PlaybackState::Playing {
            let elapsed = now_epoch_millis.saturating_sub(snapshot.updated_at_epoch_millis);
            if elapsed >= 1000 {
                // Tick every second
                let new_position =
                    snapshot.position_millis + (elapsed as f32 * snapshot.playback_speed) as u64;
                return Some(EngineCommand::seek(new_position));
            }
        }
        None
    }
}

/// Manages multiple engine services.
#[derive(Default)]
pub struct ServiceManager {
    services: Vec<Box<dyn EngineService>>,
}

impl ServiceManager {
    pub fn new() -> Self {
        Self {
            services: vec![Box::new(ProgressService)],
        }
    }

    pub fn add_service(&mut self, service: Box<dyn EngineService>) {
        self.services.push(service);
    }

    /// Performs a tick for all registered services, returning any commands they wish to dispatch.
    pub fn tick(&self, engine: &Engine, now_epoch_millis: u64) -> Vec<EngineCommand> {
        self.services
            .iter()
            .filter_map(|s| s.on_tick(engine, now_epoch_millis))
            .collect()
    }
}
