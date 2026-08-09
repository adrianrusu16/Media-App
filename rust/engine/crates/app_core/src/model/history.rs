use serde::{Deserialize, Serialize};

use crate::{EngineError, EngineErrorType, EnginePageRequest, EnginePagedResult, EngineTrack};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineHistoryIdentity {
    pub account_id: String,
    pub session_id: String,
}

impl EngineHistoryIdentity {
    pub fn new(
        account_id: impl Into<String>,
        session_id: impl Into<String>,
    ) -> Result<Self, EngineError> {
        let identity = Self {
            account_id: account_id.into(),
            session_id: session_id.into(),
        };
        if identity.account_id.trim().is_empty() || identity.session_id.trim().is_empty() {
            return Err(invalid_history_input(
                "history identity requires account and current session ids",
            ));
        }
        Ok(identity)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineHistorySettings {
    pub enabled: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineHistorySettingsUpdate {
    pub settings: EngineHistorySettings,
    pub deleted_count: u64,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EnginePlaybackRecord {
    pub track_id: String,
    pub duration_millis: u64,
    pub completion_ratio: f32,
}

impl EnginePlaybackRecord {
    pub fn new(
        track_id: impl Into<String>,
        duration_millis: u64,
        completion_ratio: f32,
    ) -> Result<Self, EngineError> {
        let track_id = track_id.into();
        if track_id.trim().is_empty() {
            return Err(invalid_history_input("playback track id is required"));
        }
        Ok(Self {
            track_id,
            duration_millis,
            completion_ratio: normalize_completion_ratio(completion_ratio)?,
        })
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EngineHistoryEntry {
    pub id: String,
    pub played_at_epoch_millis: Option<u64>,
    pub duration_millis: u64,
    pub completion_ratio: f32,
    pub track: Option<EngineTrack>,
}

pub fn normalize_completion_ratio(value: f32) -> Result<f32, EngineError> {
    if !value.is_finite() {
        return Err(invalid_history_input(
            "playback completion ratio must be finite",
        ));
    }
    Ok(value.clamp(0.0, 1.0))
}

fn invalid_history_input(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}

#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait HistoryPort: Send + Sync {
    async fn get_settings(
        &self,
        identity: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError>;
    async fn update_settings(
        &self,
        identity: &EngineHistoryIdentity,
        enabled: bool,
    ) -> Result<EngineHistorySettingsUpdate, EngineError>;
    async fn record(
        &self,
        identity: &EngineHistoryIdentity,
        event: EnginePlaybackRecord,
    ) -> Result<bool, EngineError>;
    async fn list(
        &self,
        identity: &EngineHistoryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError>;
    async fn delete_entry(
        &self,
        identity: &EngineHistoryIdentity,
        id: &str,
    ) -> Result<(), EngineError>;
    async fn clear(&self, identity: &EngineHistoryIdentity) -> Result<u64, EngineError>;
}
