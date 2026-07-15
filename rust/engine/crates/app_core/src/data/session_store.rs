use std::sync::Mutex;

use crate::{AuthSessionEnvelope, EngineError, EngineErrorType};

/// Typed failures produced by authentication-session storage.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SessionStoreError {
    Unavailable(String),
    Corrupted(String),
}

impl std::fmt::Display for SessionStoreError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Unavailable(message) | Self::Corrupted(message) => formatter.write_str(message),
        }
    }
}

impl std::error::Error for SessionStoreError {}

impl From<SessionStoreError> for EngineError {
    fn from(error: SessionStoreError) -> Self {
        let message = match error {
            SessionStoreError::Unavailable(_) => "authentication session storage unavailable",
            SessionStoreError::Corrupted(_) => "authentication session storage is corrupted",
        };
        EngineError::new(EngineErrorType::SessionStorage, message, false)
    }
}

/// Atomic storage boundary for the complete authentication-session aggregate.
///
/// Implementations must commit `replace` and `clear` atomically: after a failed
/// operation, `read` must return the complete previous value or no value.
pub trait SessionStore: Send + Sync {
    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError>;
    fn replace(&self, envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError>;
    fn clear(&self) -> Result<(), SessionStoreError>;
}

/// Thread-safe ephemeral session storage for tests and development only.
///
/// This store is neither durable nor secure and must not be used for production
/// login. Production login requires a durable, secure `SessionStore` implementation.
#[derive(Default)]
pub struct InMemorySessionStore {
    envelope: Mutex<Option<AuthSessionEnvelope>>,
}

impl InMemorySessionStore {
    /// Always false: production login must use durable, secure storage.
    pub const PRODUCTION_READY: bool = false;

    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_session(envelope: AuthSessionEnvelope) -> Self {
        Self {
            envelope: Mutex::new(Some(envelope)),
        }
    }

    fn lock(
        &self,
    ) -> Result<std::sync::MutexGuard<'_, Option<AuthSessionEnvelope>>, SessionStoreError> {
        self.envelope.lock().map_err(|_| {
            SessionStoreError::Unavailable("in-memory session store lock poisoned".into())
        })
    }
}

impl SessionStore for InMemorySessionStore {
    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        Ok(self.lock()?.clone())
    }

    fn replace(&self, envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        *self.lock()? = Some(envelope);
        Ok(())
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        *self.lock()? = None;
        Ok(())
    }
}
