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
}
