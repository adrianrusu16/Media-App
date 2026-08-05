use serde::{Deserialize, Serialize};

use crate::EngineError;

/// Backend-neutral, credential-free profile projection.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineProfile {
    pub id: String,
    pub external_user_id: String,
    pub display_name: Option<String>,
    pub created_at_epoch_millis: Option<u64>,
    pub updated_at_epoch_millis: Option<u64>,
}

/// Typed patch for a profile. `None` leaves a field untouched; `Some(None)` clears it.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineProfileUpdate {
    pub display_name: Option<Option<String>>,
}

impl EngineProfileUpdate {
    pub fn display_name(display_name: Option<String>) -> Self {
        Self {
            display_name: Some(display_name),
        }
    }

    pub fn apply_to(&self, profile: &EngineProfile) -> EngineProfile {
        let mut updated = profile.clone();
        if let Some(display_name) = &self.display_name {
            updated.display_name = display_name.clone();
        }
        updated
    }

    pub fn field_mask_paths(&self) -> Vec<String> {
        self.display_name
            .as_ref()
            .map(|_| vec!["display_name".to_owned()])
            .unwrap_or_default()
    }
}

#[async_trait::async_trait]
pub trait ProfilePort: Send + Sync {
    async fn upsert(&self, display_name: Option<&str>) -> Result<EngineProfile, EngineError>;
    async fn get(&self) -> Result<EngineProfile, EngineError>;
    async fn update(&self, profile: EngineProfileUpdate) -> Result<EngineProfile, EngineError>;
    async fn delete(&self) -> Result<(), EngineError>;
    async fn get_preferences(
        &self,
    ) -> Result<serde_json::Map<String, serde_json::Value>, EngineError>;
    async fn update_preferences(
        &self,
        values: serde_json::Map<String, serde_json::Value>,
    ) -> Result<serde_json::Map<String, serde_json::Value>, EngineError>;
}
