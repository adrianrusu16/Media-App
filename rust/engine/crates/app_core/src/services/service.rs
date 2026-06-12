use crate::engine::core::Engine;
use crate::model::command::EngineCommand;
use crate::model::playback::PlaybackState;
use std::sync::atomic::{AtomicU64, Ordering};

/// Trait for background services that need to perform periodic work.
pub trait EngineService: Send + Sync {
    /// Returns the unique name of the service.
    fn name(&self) -> &'static str;

    /// Returns the service as a [std::any::Any] for downcasting.
    fn as_any(&self) -> &dyn std::any::Any;

    /// Called periodically by the host to allow the service to perform work.
    fn on_tick(&self, engine: &Engine, now_epoch_millis: u64) -> Option<EngineCommand>;
}

/// A service that tracks and updates playback progress.
pub struct ProgressService;

impl EngineService for ProgressService {
    fn name(&self) -> &'static str {
        "ProgressService"
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn on_tick(&self, engine: &Engine, now_epoch_millis: u64) -> Option<EngineCommand> {
        let snapshot = engine.snapshot();

        // Only update progress if we are playing
        if snapshot.playback_state == PlaybackState::Playing {
            let elapsed = now_epoch_millis.saturating_sub(snapshot.last_progress_tick_epoch_millis);
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
            services: vec![
                Box::new(ProgressService),
                Box::new(SleepTimerService::default()),
            ],
        }
    }

    pub fn add_service(&mut self, service: Box<dyn EngineService>) {
        self.services.push(service);
    }

    /// Finds a service by its name.
    pub fn find_service<T: 'static + EngineService>(&self) -> Option<&T> {
        for s in &self.services {
            if let Some(service) = s.as_any().downcast_ref::<T>() {
                return Some(service);
            }
        }
        None
    }

    /// Performs a tick for all registered services, returning any commands they wish to dispatch.
    pub fn tick(&self, engine: &Engine, now_epoch_millis: u64) -> Vec<EngineCommand> {
        self.services
            .iter()
            .filter_map(|s| s.on_tick(engine, now_epoch_millis))
            .collect()
    }
}

/// A service that manages a sleep timer.
pub struct SleepTimerService {
    /// The absolute epoch time when the timer should fire. 0 means disabled.
    fire_at_epoch_millis: AtomicU64,
}

impl Default for SleepTimerService {
    fn default() -> Self {
        Self {
            fire_at_epoch_millis: AtomicU64::new(0),
        }
    }
}

impl EngineService for SleepTimerService {
    fn name(&self) -> &'static str {
        "SleepTimerService"
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn on_tick(&self, _engine: &Engine, now_epoch_millis: u64) -> Option<EngineCommand> {
        // First check if we have a SetSleepTimer command in the last dispatch
        // Actually, on_tick is for autonomous work.
        // The Service doesn't have easy access to the Command that was just dispatched to Engine,
        // but the Engine can notify services or we can just check the latest command in a real impl.
        // For this demo, the Engine's reducer will set the timer state in this service if it has access.

        let fire_at = self.fire_at_epoch_millis.load(Ordering::Relaxed);
        if fire_at > 0 && now_epoch_millis >= fire_at {
            // Timer fired!
            self.fire_at_epoch_millis.store(0, Ordering::Relaxed);
            return Some(EngineCommand::pause());
        }

        None
    }
}

impl SleepTimerService {
    pub fn set_timer(&self, fire_at: u64) {
        self.fire_at_epoch_millis.store(fire_at, Ordering::Relaxed);
    }
}
