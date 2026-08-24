use std::sync::{Arc, Mutex};

use panda_engine_core::engine::actor::{
    ActorClockConfig, ActorConfig, ActorFailure, ActorOutcome, ActorOutcomeStatus, ActorStartError,
    ActorTask, EngineActor, EngineActorHandle, SubmissionError,
};
use panda_engine_core::model::config::EngineConfig;
use panda_engine_core::networking::canopy::CanopyConnectionConfig;
use panda_engine_core::{
    Engine, EngineCommand, EngineEffect, EngineEvent, EngineObserver, EngineOutcome,
    EnginePlatformEvent, EngineSnapshot, InMemorySessionStore, LoggerMiddleware,
    MiddlewarePipeline, SessionCoordinator, SessionStore, TelemetryMiddleware,
};
use tracing::info;

use crate::FfiEngineSnapshot;
use crate::mappings::event_to_ffi;

/// Opaque handle to the Rust Engine.
pub struct PandaEngine {
    pub(crate) engine: ActorEngineBridge,
    pub(crate) last_effects: Arc<Mutex<Vec<EngineEffect>>>,
    pub(crate) last_event: Arc<Mutex<Option<EngineEvent>>>,
    pub(crate) observer: Option<Arc<FfiObserver>>,
    pub(crate) runtime: tokio::runtime::Runtime,
    pub(crate) runtime_worker_threads: usize,
    pub(crate) backend_configuration: Mutex<BackendConfigurationState>,
    pub(crate) session_store: Mutex<Arc<dyn SessionStore>>,
    pub(crate) auth_runtime: Mutex<Option<EngineAuthRuntime>>,
}

pub(crate) struct ActorEngineBridge {
    handle: EngineActorHandle,
    runtime: tokio::runtime::Handle,
    task: Mutex<Option<ActorTask>>,
}

impl ActorEngineBridge {
    fn spawn(
        engine: Engine,
        runtime: &tokio::runtime::Runtime,
        config: ActorConfig,
    ) -> Result<Self, ActorStartError> {
        let _entered = runtime.enter();
        let actor = EngineActor::spawn(engine, config)?;
        let (handle, _events, task) = actor.into_parts();
        Ok(Self {
            handle,
            runtime: runtime.handle().clone(),
            task: Mutex::new(Some(task)),
        })
    }

    pub(crate) async fn dispatch(
        &self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> Result<EngineOutcome, ActorBridgeError> {
        self.handle
            .submit_and_wait(command, now_epoch_millis)
            .await
            .map_err(ActorBridgeError::from)
            .and_then(actor_outcome_to_engine_outcome)
    }

    pub(crate) async fn dispatch_platform_event(
        &self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> Result<EngineOutcome, ActorBridgeError> {
        self.handle
            .submit_platform_event_and_wait(event, now_epoch_millis)
            .await
            .map_err(ActorBridgeError::from)
            .and_then(actor_outcome_to_engine_outcome)
    }

    pub(crate) async fn tick(
        &self,
        now_epoch_millis: u64,
    ) -> Result<Vec<EngineOutcome>, ActorBridgeError> {
        self.handle
            .tick_now(now_epoch_millis)
            .await
            .map_err(ActorBridgeError::from)
    }

    pub(crate) fn snapshot(&self) -> EngineSnapshot {
        self.handle.latest_snapshot().snapshot.as_ref().clone()
    }

    pub(crate) fn config(&self) -> EngineConfig {
        self.with_engine(|engine| engine.config().clone())
    }

    pub(crate) fn with_engine<F, R>(&self, mutation: F) -> R
    where
        F: FnOnce(&mut Engine) -> R + Send + 'static,
        R: Send + 'static,
    {
        self.runtime
            .block_on(self.handle.mutate_engine(mutation))
            .expect("actor engine mutation failed")
    }
}

impl Drop for ActorEngineBridge {
    fn drop(&mut self) {
        let _ = self.handle.request_shutdown();
        if let Some(task) = self.task.lock().unwrap().take() {
            let _ = self.runtime.block_on(task.wait());
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum ActorBridgeError {
    Submission(SubmissionError),
    Cancelled,
    Failed(ActorFailure),
}

impl From<SubmissionError> for ActorBridgeError {
    fn from(error: SubmissionError) -> Self {
        Self::Submission(error)
    }
}

fn actor_outcome_to_engine_outcome(
    outcome: ActorOutcome,
) -> Result<EngineOutcome, ActorBridgeError> {
    match outcome.status {
        ActorOutcomeStatus::Completed => Ok(EngineOutcome {
            snapshot: outcome.snapshot.as_ref().clone(),
            event: outcome
                .event
                .unwrap_or_else(|| EngineEvent::command_applied(None)),
            effects: outcome.effects,
        }),
        ActorOutcomeStatus::Cancelled(_) => Err(ActorBridgeError::Cancelled),
        ActorOutcomeStatus::Failed(failure) => Err(ActorBridgeError::Failed(failure)),
    }
}

#[derive(Clone)]
pub(crate) struct EngineAuthRuntime {
    pub(crate) coordinator: Arc<SessionCoordinator>,
    pub(crate) store: Arc<dyn SessionStore>,
    pub(crate) production: bool,
}

#[derive(Debug)]
pub(crate) enum BackendConfigurationState {
    Unconfigured,
    Configuring,
    Ready(Box<CanopyConnectionConfig>),
    Failed,
}

impl PandaEngine {
    #[cfg(test)]
    pub(crate) fn backend_is_configured(&self) -> bool {
        matches!(
            *self.backend_configuration.lock().unwrap(),
            BackendConfigurationState::Ready(_)
        )
    }

    pub(crate) fn install_session_store(&self, store: Arc<dyn SessionStore>) -> bool {
        let configuration = self.backend_configuration.lock().unwrap();
        if !matches!(*configuration, BackendConfigurationState::Unconfigured) {
            return false;
        }
        *self.session_store.lock().unwrap() = store;
        true
    }
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

const DEFAULT_RUNTIME_WORKER_THREADS: usize = 2;
const MAX_RUNTIME_WORKER_THREADS: usize = 8;
const RUNTIME_WORKER_THREADS_ENV: &str = "PANDA_ENGINE_TOKIO_WORKERS";

pub(crate) fn build_engine(now_epoch_millis: u64) -> PandaEngine {
    build_engine_with_worker_threads(now_epoch_millis, configured_runtime_worker_threads())
}

pub(crate) fn build_engine_with_worker_threads(
    now_epoch_millis: u64,
    worker_threads: usize,
) -> PandaEngine {
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

    let worker_threads = sanitize_runtime_worker_threads(worker_threads);
    crate::perfetto_trace::counter("PW.Native.runtimeWorkers", worker_threads as i64);
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(worker_threads)
        .thread_name("panda-engine")
        .enable_all()
        .build()
        .expect("failed to build tokio runtime");

    let panda_engine = PandaEngine {
        engine: ActorEngineBridge::spawn(
            engine,
            &runtime,
            ActorConfig {
                clock: ActorClockConfig {
                    start_epoch_millis: now_epoch_millis,
                    ..ActorClockConfig::default()
                },
                split_remote_operations: false,
                ..ActorConfig::default()
            },
        )
        .expect("failed to spawn PandaEngine actor"),
        last_effects: Arc::new(Mutex::new(Vec::new())),
        last_event: Arc::new(Mutex::new(None)),
        observer: None,
        runtime,
        runtime_worker_threads: worker_threads,
        backend_configuration: Mutex::new(BackendConfigurationState::Unconfigured),
        session_store: Mutex::new(Arc::new(InMemorySessionStore::new())),
        auth_runtime: Mutex::new(None),
    };
    info!(
        worker_threads = panda_engine.runtime_worker_threads,
        "PandaEngine Tokio runtime initialized"
    );
    panda_engine
}

fn configured_runtime_worker_threads() -> usize {
    parse_runtime_worker_threads(std::env::var(RUNTIME_WORKER_THREADS_ENV).ok().as_deref())
}

fn parse_runtime_worker_threads(value: Option<&str>) -> usize {
    value
        .and_then(|value| value.trim().parse::<usize>().ok())
        .map(sanitize_runtime_worker_threads)
        .unwrap_or(DEFAULT_RUNTIME_WORKER_THREADS)
}

fn sanitize_runtime_worker_threads(worker_threads: usize) -> usize {
    worker_threads.clamp(1, MAX_RUNTIME_WORKER_THREADS)
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

#[cfg(test)]
mod runtime_tests {
    use super::*;

    #[test]
    fn runtime_worker_count_defaults_and_clamps_for_benchmark_experiments() {
        assert_eq!(
            DEFAULT_RUNTIME_WORKER_THREADS,
            parse_runtime_worker_threads(None)
        );
        assert_eq!(
            DEFAULT_RUNTIME_WORKER_THREADS,
            parse_runtime_worker_threads(Some("not-a-number"))
        );
        assert_eq!(1, parse_runtime_worker_threads(Some("0")));
        assert_eq!(2, parse_runtime_worker_threads(Some("2")));
        assert_eq!(4, parse_runtime_worker_threads(Some("4")));
        assert_eq!(
            MAX_RUNTIME_WORKER_THREADS,
            parse_runtime_worker_threads(Some("99"))
        );
    }

    #[test]
    fn build_engine_records_the_selected_worker_count() {
        let engine = build_engine_with_worker_threads(0, 4);
        assert_eq!(4, engine.runtime_worker_threads);
    }
}
