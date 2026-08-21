use serde::{Deserialize, Serialize};

/// Retry behavior selected from domain operation semantics, not RPC names.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum RetryClass {
    Read,
    IdempotentMutation,
    NonReplayableMutation,
    Refresh,
}

/// Forward-compatible status value that preserves the backend's exact text.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(transparent)]
pub struct EngineStatusValue(String);

impl EngineStatusValue {
    pub fn from_wire(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    pub fn as_wire(&self) -> &str {
        &self.0
    }
}

/// Health projection for one public backend dependency.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineDependencyStatus {
    pub name: String,
    pub status: EngineStatusValue,
    pub message: String,
}

/// Backend-neutral public service status.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineBackendStatus {
    pub healthy: bool,
    pub version: String,
    pub status: EngineStatusValue,
    pub dependencies: Vec<EngineDependencyStatus>,
    pub checked_at_epoch_millis: Option<u64>,
}

/// Runtime reachability of the configured backend. Configuration validity is
/// deliberately not represented here: an engine with a valid configuration
/// remains usable while its backend is temporarily unreachable.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum BackendAvailability {
    #[default]
    Connecting,
    Available,
    Unavailable(BackendUnavailableReason),
}

/// A safe, display-independent classification of a transient backend outage.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum BackendUnavailableReason {
    NetworkUnavailable,
    ConnectionFailed,
    Timeout,
    ServiceUnavailable,
}
