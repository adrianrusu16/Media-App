use serde::{Deserialize, Serialize};

/// Represents the configuration and profile settings for the engine.
///
/// This allows the engine to adapt to different vehicle environments or user preferences.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EngineConfig {
    /// The name of the vehicle or system running the engine.
    pub vehicle_name: String,
    /// Whether high-fidelity audio is enabled.
    pub hifi_enabled: bool,
    /// Maximum allowed volume percentage (0-100).
    pub max_volume: u8,
    /// Whether to automatically resume playback on startup.
    pub auto_resume: bool,
    /// The language for voice feedback and metadata.
    pub preferred_language: String,
}

impl Default for EngineConfig {
    fn default() -> Self {
        Self {
            vehicle_name: "Generic Car".to_string(),
            hifi_enabled: false,
            max_volume: 100,
            auto_resume: true,
            preferred_language: "en-US".to_string(),
        }
    }
}

impl EngineConfig {
    /// Creates a new configuration builder.
    pub fn new() -> Self {
        Self::default()
    }

    /// Functional update for the vehicle name.
    #[must_use]
    pub fn with_vehicle_name(mut self, name: String) -> Self {
        self.vehicle_name = name;
        self
    }

    /// Functional update for the HiFi setting.
    #[must_use]
    pub fn with_hifi(mut self, enabled: bool) -> Self {
        self.hifi_enabled = enabled;
        self
    }
}
