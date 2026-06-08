#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EnginePlatformEventType {
    AppForegrounded,
    AppBackgrounded,
    SuspendToRam,
    ResumeFromRam,
    UxRestrictionsChanged,
    Unknown(String),
}

impl EnginePlatformEventType {
    pub const APP_FOREGROUNDED_WIRE: &'static str = "app_foregrounded";
    pub const APP_BACKGROUNDED_WIRE: &'static str = "app_backgrounded";
    pub const SUSPEND_TO_RAM_WIRE: &'static str = "suspend_to_ram";
    pub const RESUME_FROM_RAM_WIRE: &'static str = "resume_from_ram";
    pub const UX_RESTRICTIONS_CHANGED_WIRE: &'static str = "ux_restrictions_changed";

    pub fn from_wire(value: impl Into<String>) -> Self {
        let value = value.into();
        match value.as_str() {
            Self::APP_FOREGROUNDED_WIRE => Self::AppForegrounded,
            Self::APP_BACKGROUNDED_WIRE => Self::AppBackgrounded,
            Self::SUSPEND_TO_RAM_WIRE => Self::SuspendToRam,
            Self::RESUME_FROM_RAM_WIRE => Self::ResumeFromRam,
            Self::UX_RESTRICTIONS_CHANGED_WIRE => Self::UxRestrictionsChanged,
            _ => Self::Unknown(value),
        }
    }

    pub fn as_wire(&self) -> &str {
        match self {
            Self::AppForegrounded => Self::APP_FOREGROUNDED_WIRE,
            Self::AppBackgrounded => Self::APP_BACKGROUNDED_WIRE,
            Self::SuspendToRam => Self::SUSPEND_TO_RAM_WIRE,
            Self::ResumeFromRam => Self::RESUME_FROM_RAM_WIRE,
            Self::UxRestrictionsChanged => Self::UX_RESTRICTIONS_CHANGED_WIRE,
            Self::Unknown(value) => value.as_str(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EnginePlatformEvent {
    pub event_type: EnginePlatformEventType,
    pub payload: Option<String>,
}

impl EnginePlatformEvent {
    pub fn new(event_type: EnginePlatformEventType, payload: Option<String>) -> Self {
        Self {
            event_type,
            payload,
        }
    }

    pub fn from_wire(event_type: impl Into<String>, payload: Option<String>) -> Self {
        Self::new(EnginePlatformEventType::from_wire(event_type), payload)
    }
}
