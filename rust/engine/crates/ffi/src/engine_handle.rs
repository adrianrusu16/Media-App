use std::sync::{Arc, Mutex};

use panda_engine_core::{
    ConcurrentEngine, Engine, EngineEffect, EngineEvent, EngineObserver, EngineOutcome,
    EngineSnapshot, LoggerMiddleware, MiddlewarePipeline, TelemetryMiddleware,
};
use tracing::info;

use crate::FfiEngineSnapshot;
use crate::mappings::event_to_ffi;

/// Opaque handle to the Rust Engine.
pub struct PandaEngine {
    pub(crate) engine: ConcurrentEngine,
    pub(crate) last_effects: Arc<Mutex<Vec<EngineEffect>>>,
    pub(crate) last_event: Arc<Mutex<Option<EngineEvent>>>,
    pub(crate) observer: Option<Arc<FfiObserver>>,
    pub(crate) runtime: tokio::runtime::Runtime,
}

pub(crate) struct FfiObserver {
    pub(crate) on_state_changed: unsafe extern "C" fn(FfiEngineSnapshot),
    pub(crate) on_event_emitted: unsafe extern "C" fn(i32),
    pub(crate) last_event: Arc<Mutex<Option<EngineEvent>>>,
}

unsafe impl Send for FfiObserver {}
unsafe impl Sync for FfiObserver {}

impl EngineObserver for FfiObserver {
    fn on_state_changed(&self, snapshot: &EngineSnapshot) {
        info!("FFI: Notifying observer of state change");
        let ffi_snapshot = FfiEngineSnapshot::from(snapshot);
        unsafe { (self.on_state_changed)(ffi_snapshot) };
    }

    fn on_event_emitted(&self, event: &EngineEvent) {
        info!("FFI: Notifying observer of event {:?}", event.event_type);
        {
            let mut last = self.last_event.lock().unwrap();
            *last = Some(event.clone());
        }
        let event_type = event_to_ffi(&event.event_type);
        unsafe { (self.on_event_emitted)(event_type) };
    }
}

pub(crate) fn build_engine(now_epoch_millis: u64) -> PandaEngine {
    let mut engine = Engine::new(now_epoch_millis);

    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(LoggerMiddleware));
    let bus = engine.event_bus();
    pipeline.add(Box::new(TelemetryMiddleware::new(bus.clone())));
    pipeline.add(Box::new(panda_engine_core::AnalyticsMiddleware::new(
        bus.clone(),
    )));
    pipeline.add(Box::new(panda_engine_core::ThrottlingMiddleware::new(300)));
    pipeline.add(Box::new(panda_engine_core::FocusMiddleware));
    engine.set_middleware(pipeline);

    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("failed to build tokio runtime");

    PandaEngine {
        engine: ConcurrentEngine::new(engine),
        last_effects: Arc::new(Mutex::new(Vec::new())),
        last_event: Arc::new(Mutex::new(None)),
        observer: None,
        runtime,
    }
}

pub(crate) fn remember_outcome(engine: &PandaEngine, outcome: &EngineOutcome) {
    {
        let mut effects = engine.last_effects.lock().unwrap();
        *effects = outcome.effects.clone();
    }
    {
        let mut event = engine.last_event.lock().unwrap();
        *event = Some(outcome.event.clone());
    }
}
