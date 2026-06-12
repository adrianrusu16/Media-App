use serde::{Deserialize, Serialize};

/// Represents the different types of errors that can occur in the engine.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum EngineErrorType {
    /// A required resource (e.g., media file) was not found.
    NotFound,
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
}

impl EngineError {
    /// Creates a new EngineError.
    pub fn new(error_type: EngineErrorType, message: impl Into<String>, is_fatal: bool) -> Self {
        Self {
            error_type,
            message: message.into(),
            is_fatal,
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
}

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
