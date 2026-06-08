/// Represents the types of events that the engine can emit.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EngineEventType {
    /// A command was successfully processed and applied to the state.
    CommandApplied,
    /// A platform-level event was processed.
    PlatformEventApplied,
    /// A new listener has been registered with the engine.
    ListenerRegistered,
}

impl EngineEventType {
    /// Wire value for CommandApplied event.
    pub const COMMAND_APPLIED_WIRE: &'static str = "command_applied";
    /// Wire value for PlatformEventApplied event.
    pub const PLATFORM_EVENT_APPLIED_WIRE: &'static str = "platform_event_applied";
    /// Wire value for ListenerRegistered event.
    pub const LISTENER_REGISTERED_WIRE: &'static str = "listener_registered";

    /// Returns the wire string representation of the event type.
    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::CommandApplied => Self::COMMAND_APPLIED_WIRE,
            Self::PlatformEventApplied => Self::PLATFORM_EVENT_APPLIED_WIRE,
            Self::ListenerRegistered => Self::LISTENER_REGISTERED_WIRE,
        }
    }
}

/// An event emitted by the engine to notify observers of changes or actions.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineEvent {
    /// The type of the event.
    pub event_type: EngineEventType,
    /// Optional message or JSON-encoded payload providing more detail about the event.
    pub message: Option<String>,
}

impl EngineEvent {
    /// Creates a new CommandApplied event.
    pub fn command_applied(message: Option<String>) -> Self {
        Self {
            event_type: EngineEventType::CommandApplied,
            message,
        }
    }

    /// Creates a new PlatformEventApplied event.
    pub fn platform_event_applied(message: Option<String>) -> Self {
        Self {
            event_type: EngineEventType::PlatformEventApplied,
            message,
        }
    }
}
