use std::sync::Mutex;

use crate::{AuthSessionEnvelope, EngineError, EngineErrorType};

/// Typed failures produced by authentication-session storage.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SessionStoreError {
    Unavailable(String),
    Corrupted(String),
    InsecureForProduction,
}

impl std::fmt::Display for SessionStoreError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Unavailable(message) | Self::Corrupted(message) => formatter.write_str(message),
            Self::InsecureForProduction => {
                formatter.write_str("production login requires durable secure session storage")
            }
        }
    }
}

impl std::error::Error for SessionStoreError {}

impl From<SessionStoreError> for EngineError {
    fn from(error: SessionStoreError) -> Self {
        let message = match error {
            SessionStoreError::Unavailable(_) => "authentication session storage unavailable",
            SessionStoreError::Corrupted(_) => "authentication session storage is corrupted",
            SessionStoreError::InsecureForProduction => {
                "production login requires durable secure session storage"
            }
        };
        EngineError::new(EngineErrorType::SessionStorage, message, false)
    }
}

/// Security capability declared by a session-store implementation.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionStoreSecurity {
    /// Process-local or otherwise non-durable storage suitable only for development and tests.
    Ephemeral,
    /// Durable storage that protects credentials at rest.
    DurableSecure,
}

/// Atomic storage boundary for the complete authentication-session aggregate.
///
/// Implementations must commit `replace` and `clear` atomically: after a failed
/// operation, `read` must return the complete previous value or no value.
pub trait SessionStore: Send + Sync {
    fn security_level(&self) -> SessionStoreSecurity;
    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError>;
    fn replace(&self, envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError>;
    fn clear(&self) -> Result<(), SessionStoreError>;
}

/// Rejects unsafe session storage before production login is enabled.
pub fn validate_production_session_store(
    store: &dyn SessionStore,
) -> Result<(), SessionStoreError> {
    match store.security_level() {
        SessionStoreSecurity::DurableSecure => Ok(()),
        SessionStoreSecurity::Ephemeral => Err(SessionStoreError::InsecureForProduction),
    }
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
    fn security_level(&self) -> SessionStoreSecurity {
        SessionStoreSecurity::Ephemeral
    }

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
