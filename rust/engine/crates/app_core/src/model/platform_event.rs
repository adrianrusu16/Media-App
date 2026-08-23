use serde::Deserialize;

/// Represents the types of platform-level events the engine can handle.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EnginePlatformEventType {
    /// The application has moved to the foreground.
    AppForegrounded,
    /// The application has moved to the background.
    AppBackgrounded,
    /// The system is suspending to RAM.
    SuspendToRam,
    /// The system is resuming from RAM.
    ResumeFromRam,
    /// User experience restrictions (e.g., driver distraction) have changed.
    UxRestrictionsChanged,
    /// The vehicle driving state has changed.
    VehicleDrivingStateChanged,
    /// Audio focus has changed (e.g., gained, lost).
    AudioFocusChanged,
    /// A media button was pressed (e.g., play, pause, next, prev from steering wheel).
    MediaButtonPressed,
    /// Media has successfully loaded and is ready to play.
    MediaLoaded,
    /// An error occurred in the platform media player.
    MediaError,
    /// The platform player reached a terminal playback position.
    PlaybackCompleted,
    /// An event not recognized by this version of the engine.
    Unknown(String),
}

impl EnginePlatformEventType {
    /// Wire value for AppForegrounded event.
    pub const APP_FOREGROUNDED_WIRE: &'static str = "app_foregrounded";
    /// Wire value for AppBackgrounded event.
    pub const APP_BACKGROUNDED_WIRE: &'static str = "app_backgrounded";
    /// Wire value for SuspendToRam event.
    pub const SUSPEND_TO_RAM_WIRE: &'static str = "suspend_to_ram";
    /// Wire value for ResumeFromRam event.
    pub const RESUME_FROM_RAM_WIRE: &'static str = "resume_from_ram";
    /// Wire value for UxRestrictionsChanged event.
    pub const UX_RESTRICTIONS_CHANGED_WIRE: &'static str = "ux_restrictions_changed";
    /// Wire value for VehicleDrivingStateChanged event.
    pub const VEHICLE_DRIVING_STATE_CHANGED_WIRE: &'static str = "vehicle_driving_state_changed";
    /// Wire value for AudioFocusChanged event.
    pub const AUDIO_FOCUS_CHANGED_WIRE: &'static str = "audio_focus_changed";
    /// Wire value for MediaButtonPressed event.
    pub const MEDIA_BUTTON_PRESSED_WIRE: &'static str = "media_button_pressed";
    /// Wire value for MediaLoaded event.
    pub const MEDIA_LOADED_WIRE: &'static str = "media_loaded";
    /// Wire value for MediaError event.
    pub const MEDIA_ERROR_WIRE: &'static str = "media_error";
    pub const PLAYBACK_COMPLETED_WIRE: &'static str = "playback_completed";

    /// Maps a wire string value to its corresponding enum variant.
    pub fn from_wire(value: impl Into<String>) -> Self {
        let value = value.into();
        match value.as_str() {
            Self::APP_FOREGROUNDED_WIRE => Self::AppForegrounded,
            Self::APP_BACKGROUNDED_WIRE => Self::AppBackgrounded,
            Self::SUSPEND_TO_RAM_WIRE => Self::SuspendToRam,
            Self::RESUME_FROM_RAM_WIRE => Self::ResumeFromRam,
            Self::UX_RESTRICTIONS_CHANGED_WIRE => Self::UxRestrictionsChanged,
            Self::VEHICLE_DRIVING_STATE_CHANGED_WIRE => Self::VehicleDrivingStateChanged,
            Self::AUDIO_FOCUS_CHANGED_WIRE => Self::AudioFocusChanged,
            Self::MEDIA_BUTTON_PRESSED_WIRE => Self::MediaButtonPressed,
            Self::MEDIA_LOADED_WIRE => Self::MediaLoaded,
            Self::MEDIA_ERROR_WIRE => Self::MediaError,
            Self::PLAYBACK_COMPLETED_WIRE => Self::PlaybackCompleted,
            _ => Self::Unknown(value),
        }
    }

    /// Returns the wire string representation of the platform event type.
    pub fn as_wire(&self) -> &str {
        match self {
            Self::AppForegrounded => Self::APP_FOREGROUNDED_WIRE,
            Self::AppBackgrounded => Self::APP_BACKGROUNDED_WIRE,
            Self::SuspendToRam => Self::SUSPEND_TO_RAM_WIRE,
            Self::ResumeFromRam => Self::RESUME_FROM_RAM_WIRE,
            Self::UxRestrictionsChanged => Self::UX_RESTRICTIONS_CHANGED_WIRE,
            Self::VehicleDrivingStateChanged => Self::VEHICLE_DRIVING_STATE_CHANGED_WIRE,
            Self::AudioFocusChanged => Self::AUDIO_FOCUS_CHANGED_WIRE,
            Self::MediaButtonPressed => Self::MEDIA_BUTTON_PRESSED_WIRE,
            Self::MediaLoaded => Self::MEDIA_LOADED_WIRE,
            Self::MediaError => Self::MEDIA_ERROR_WIRE,
            Self::PlaybackCompleted => Self::PLAYBACK_COMPLETED_WIRE,
            Self::Unknown(value) => value.as_str(),
        }
    }
}

/// A platform-level event received from the Android system.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EnginePlatformEvent {
    /// The type of the platform event.
    pub event_type: EnginePlatformEventType,
    /// Optional JSON-encoded or raw string payload for the event.
    pub payload: Option<String>,
}

/// Versioned identity envelope emitted by PandaWave for player observations.
/// It deliberately carries no capability or source URL.
#[derive(Deserialize)]
pub(crate) struct PlaybackObservationPayload {
    pub version: u8,
    pub playback_instance_id: u64,
    #[serde(default)]
    pub kind: Option<String>,
    /// Position reported at the failure boundary. It is diagnostic only: decoder
    /// recovery deliberately seeks to the engine's earlier safe position.
    #[serde(default)]
    pub position_ms: Option<u64>,
    /// Android's decoder identifier, when Media3 exposes one.
    #[serde(default)]
    pub decoder: Option<String>,
    /// Media3's stable playback error code.
    #[serde(default)]
    pub error_code: Option<i32>,
    /// Decoder lifecycle phase, such as `initialization` or `decoding`.
    #[serde(default)]
    pub phase: Option<String>,
    /// Playback intent captured from the failed player before it is released.
    #[serde(default)]
    pub play_when_ready: Option<bool>,
}

impl EnginePlatformEvent {
    /// Creates a new engine platform event.
    pub fn new(event_type: EnginePlatformEventType, payload: Option<String>) -> Self {
        Self {
            event_type,
            payload,
        }
    }

    /// Convenience method to create a platform event from wire values.
    pub fn from_wire(event_type: impl Into<String>, payload: Option<String>) -> Self {
        Self::new(EnginePlatformEventType::from_wire(event_type), payload)
    }

    pub fn playback_completed(
        track_id: impl Into<String>,
        duration_millis: u64,
        completion_ratio: f32,
    ) -> Self {
        let payload = serde_json::json!({
            "version": 1,
            "track_id": track_id.into(),
            "duration_ms": duration_millis,
            "completion_ratio": completion_ratio,
        });
        Self::new(
            EnginePlatformEventType::PlaybackCompleted,
            Some(payload.to_string()),
        )
    }
}
