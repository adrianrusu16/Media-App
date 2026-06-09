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
    /// Audio focus has changed (e.g., gained, lost).
    AudioFocusChanged,
    /// A media button was pressed (e.g., play, pause, next, prev from steering wheel).
    MediaButtonPressed,
    /// Media has successfully loaded and is ready to play.
    MediaLoaded,
    /// An error occurred in the platform media player.
    MediaError,
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
    /// Wire value for AudioFocusChanged event.
    pub const AUDIO_FOCUS_CHANGED_WIRE: &'static str = "audio_focus_changed";
    /// Wire value for MediaButtonPressed event.
    pub const MEDIA_BUTTON_PRESSED_WIRE: &'static str = "media_button_pressed";
    /// Wire value for MediaLoaded event.
    pub const MEDIA_LOADED_WIRE: &'static str = "media_loaded";
    /// Wire value for MediaError event.
    pub const MEDIA_ERROR_WIRE: &'static str = "media_error";

    /// Maps a wire string value to its corresponding enum variant.
    pub fn from_wire(value: impl Into<String>) -> Self {
        let value = value.into();
        match value.as_str() {
            Self::APP_FOREGROUNDED_WIRE => Self::AppForegrounded,
            Self::APP_BACKGROUNDED_WIRE => Self::AppBackgrounded,
            Self::SUSPEND_TO_RAM_WIRE => Self::SuspendToRam,
            Self::RESUME_FROM_RAM_WIRE => Self::ResumeFromRam,
            Self::UX_RESTRICTIONS_CHANGED_WIRE => Self::UxRestrictionsChanged,
            Self::AUDIO_FOCUS_CHANGED_WIRE => Self::AudioFocusChanged,
            Self::MEDIA_BUTTON_PRESSED_WIRE => Self::MediaButtonPressed,
            Self::MEDIA_LOADED_WIRE => Self::MediaLoaded,
            Self::MEDIA_ERROR_WIRE => Self::MediaError,
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
            Self::AudioFocusChanged => Self::AUDIO_FOCUS_CHANGED_WIRE,
            Self::MediaButtonPressed => Self::MEDIA_BUTTON_PRESSED_WIRE,
            Self::MediaLoaded => Self::MEDIA_LOADED_WIRE,
            Self::MediaError => Self::MEDIA_ERROR_WIRE,
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
}
