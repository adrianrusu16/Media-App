use serde::{Deserialize, Serialize};

/// Represents the different types of errors that can occur in the engine.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum EngineErrorType {
    /// Client input failed domain validation.
    InvalidInput,
    /// A required resource (e.g., media file) was not found.
    NotFound,
    /// The operation requires a new interactive login.
    LoginRequired,
    /// The current access credential is no longer usable.
    AuthExpired,
    /// The authenticated principal is not allowed to perform the operation.
    Forbidden,
    /// A resource with the requested identity already exists.
    AlreadyExists,
    /// The operation cannot run in the resource's current state.
    FailedPrecondition,
    /// Concurrent state conflicts with the requested mutation.
    Conflict,
    /// The backend rejected the operation due to rate limiting.
    RateLimited,
    /// The backend is temporarily unavailable.
    ServiceUnavailable,
    /// The backend reported an internal fault.
    BackendFault,
    /// The client could not reach or negotiate with the backend.
    Transport,
    /// The configured transport violates deployment security rules.
    UnsafeTransport,
    /// A canonical backend response could not be mapped into the engine domain.
    MappingDefect,
    /// A network-related error occurred.
    NetworkError,
    /// An error occurred in the platform media player.
    PlayerError,
    /// An error related to user permissions or session.
    AuthenticationError,
    /// The media was skipped due to a non-fatal error.
    MediaSkipped,
    /// A command was rejected by middleware or policy checks before execution.
    CommandRejected,
    /// An unknown or unexpected error.
    Unknown,
}

/// A structured error emitted by the engine.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineError {
    /// The category of the error.
    pub error_type: EngineErrorType,
    /// A human-readable message describing the error.
    pub message: String,
    /// Whether the error is fatal (requires stopping playback).
    pub is_fatal: bool,
    /// Optional server-provided delay before a rate-limited operation may be retried.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub retry_after_millis: Option<u64>,
}

impl EngineError {
    /// Creates a new EngineError.
    pub fn new(error_type: EngineErrorType, message: impl Into<String>, is_fatal: bool) -> Self {
        Self {
            error_type,
            message: message.into(),
            is_fatal,
            retry_after_millis: None,
        }
    }

    /// Convenience for player errors.
    pub fn player_error(message: impl Into<String>) -> Self {
        Self::new(EngineErrorType::PlayerError, message, true)
    }

    /// Convenience for non-fatal media skip errors.
    pub fn media_skipped(message: impl Into<String>) -> Self {
        Self::new(EngineErrorType::MediaSkipped, message, false)
    }

    /// Typed unsafe-transport configuration error.
    pub fn unsafe_transport() -> Self {
        Self::new(
            EngineErrorType::UnsafeTransport,
            "unsafe backend transport configuration",
            false,
        )
    }

    /// Typed rate-limit error with an optional server retry hint.
    pub fn rate_limited(retry_after_millis: Option<u64>) -> Self {
        let mut error = Self::new(
            EngineErrorType::RateLimited,
            "backend rate limit exceeded",
            false,
        );
        error.retry_after_millis = retry_after_millis;
        error
    }
}

impl std::fmt::Display for EngineError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl std::error::Error for EngineError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn player_error_is_fatal_and_typed() {
        let error = EngineError::player_error("decoder crashed");
        assert_eq!(error.error_type, EngineErrorType::PlayerError);
        assert_eq!(error.message, "decoder crashed");
        assert!(error.is_fatal);
    }

    #[test]
    fn media_skipped_is_non_fatal_and_typed() {
        let error = EngineError::media_skipped("network hiccup");
        assert_eq!(error.error_type, EngineErrorType::MediaSkipped);
        assert_eq!(error.message, "network hiccup");
        assert!(!error.is_fatal);
    }
}
