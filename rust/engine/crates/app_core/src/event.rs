#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EngineEventType {
    CommandApplied,
    PlatformEventApplied,
    ListenerRegistered,
}

impl EngineEventType {
    pub const COMMAND_APPLIED_WIRE: &'static str = "command_applied";
    pub const PLATFORM_EVENT_APPLIED_WIRE: &'static str = "platform_event_applied";
    pub const LISTENER_REGISTERED_WIRE: &'static str = "listener_registered";

    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::CommandApplied => Self::COMMAND_APPLIED_WIRE,
            Self::PlatformEventApplied => Self::PLATFORM_EVENT_APPLIED_WIRE,
            Self::ListenerRegistered => Self::LISTENER_REGISTERED_WIRE,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineEvent {
    pub event_type: EngineEventType,
    pub message: Option<String>,
}

impl EngineEvent {
    pub fn command_applied(message: Option<String>) -> Self {
        Self {
            event_type: EngineEventType::CommandApplied,
            message,
        }
    }

    pub fn platform_event_applied(message: Option<String>) -> Self {
        Self {
            event_type: EngineEventType::PlatformEventApplied,
            message,
        }
    }
}
