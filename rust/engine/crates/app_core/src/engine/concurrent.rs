use crate::engine::core::{Engine, EngineOutcome};
use crate::model::command::EngineCommand;
use crate::model::platform_event::EnginePlatformEvent;
use std::sync::{Arc, Mutex};

/// A thread-safe wrapper around the Engine.
///
/// In an AAOS environment, the engine may be accessed from multiple threads
/// (e.g., UI thread, MediaSession callback threads, background service threads).
/// This wrapper ensures that all access to the engine is synchronized.
#[derive(Clone)]
pub struct ConcurrentEngine {
    inner: Arc<Mutex<Engine>>,
}

impl ConcurrentEngine {
    /// Creates a new concurrent engine wrapping the given engine.
    pub fn new(engine: Engine) -> Self {
        Self {
            inner: Arc::new(Mutex::new(engine)),
        }
    }

    /// Dispatches a command to the engine in a thread-safe manner.
    ///
    /// The lock is intentionally held across the `.await`: the wrapper serializes
    /// all engine access, so a single command must have exclusive access to the
    /// engine for the full duration of its (re)dispatch.
    #[allow(clippy::await_holding_lock)]
    pub async fn dispatch(&self, command: EngineCommand, now_epoch_millis: u64) -> EngineOutcome {
        let mut engine = self.inner.lock().unwrap();
        engine.dispatch(command, now_epoch_millis).await
    }

    /// Dispatches a platform event to the engine in a thread-safe manner.
    ///
    /// See [`ConcurrentEngine::dispatch`] for why the lock is held across the await.
    #[allow(clippy::await_holding_lock)]
    pub async fn dispatch_platform_event(
        &self,
        event: EnginePlatformEvent,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        let mut engine = self.inner.lock().unwrap();
        engine.dispatch_platform_event(event, now_epoch_millis).await
    }

    /// Advances the engine state in a thread-safe manner.
    ///
    /// See [`ConcurrentEngine::dispatch`] for why the lock is held across the await.
    #[allow(clippy::await_holding_lock)]
    pub async fn tick(&self, now_epoch_millis: u64) -> Vec<EngineOutcome> {
        let mut engine = self.inner.lock().unwrap();
        engine.tick(now_epoch_millis).await
    }

    /// Accesses the engine's snapshot in a thread-safe manner.
    ///
    /// Note: This returns a clone of the snapshot to avoid keeping the lock open.
    pub fn snapshot(&self) -> crate::model::snapshot::EngineSnapshot {
        let engine = self.inner.lock().unwrap();
        engine.snapshot().clone()
    }

    /// Accesses the engine's configuration in a thread-safe manner.
    pub fn config(&self) -> crate::model::config::EngineConfig {
        let engine = self.inner.lock().unwrap();
        engine.config().clone()
    }

    /// Provides access to the inner engine through a closure.
    ///
    /// This is useful for performing multiple operations while holding the lock.
    pub fn with_engine<F, R>(&self, f: F) -> R
    where
        F: FnOnce(&mut Engine) -> R,
    {
        let mut engine = self.inner.lock().unwrap();
        f(&mut engine)
    }
}
