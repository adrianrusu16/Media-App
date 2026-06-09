use crate::data::queue::QueueManager;
use crate::model::snapshot::EngineSnapshot;

/// Represents the persistent state of the engine.
#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub struct EnginePersistentState {
    pub snapshot: EngineSnapshot,
    pub queue: QueueManager,
}

/// Trait for engine state persistence.
pub trait Persistence: Send + Sync {
    /// Saves the engine state.
    fn save(&self, state: &EnginePersistentState) -> Result<(), String>;

    /// Loads the engine state.
    fn load(&self) -> Result<Option<EnginePersistentState>, String>;
}

/// A simple no-op persistence implementation.
pub struct NoopPersistence;

impl Persistence for NoopPersistence {
    fn save(&self, _state: &EnginePersistentState) -> Result<(), String> {
        Ok(())
    }

    fn load(&self) -> Result<Option<EnginePersistentState>, String> {
        Ok(None)
    }
}
