use serde::{Deserialize, Serialize};

use super::error::{EngineError, EngineErrorType};

/// Opaque continuation value owned by a backend adapter.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(transparent)]
pub struct EnginePageToken(String);

impl EnginePageToken {
    /// Wraps a non-empty token without inspecting or normalizing its contents.
    pub fn new(value: String) -> Result<Self, EngineError> {
        if value.is_empty() {
            Err(EngineError::new(
                EngineErrorType::InvalidInput,
                "empty page token",
                false,
            ))
        } else {
            Ok(Self(value))
        }
    }

    /// Returns the exact token supplied by the adapter.
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// Service-neutral pagination input.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct EnginePageRequest {
    pub page_size: u32,
    pub page_token: Option<EnginePageToken>,
}

/// Service-neutral page result.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EnginePagedResult<T> {
    pub items: Vec<T>,
    pub next_page_token: Option<EnginePageToken>,
}
