/// Represents the types of events that the engine can emit.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EngineEventType {
    /// A command was successfully processed and applied to the state.
    CommandApplied,
    /// A platform-level event was processed.
    PlatformEventApplied,
    /// A new listener has been registered with the engine.
    ListenerRegistered,
    /// An analytics event occurred (e.g., playback started, track finished).
    AnalyticsReported,
}

impl EngineEventType {
    /// Wire value for CommandApplied event.
    pub const COMMAND_APPLIED_WIRE: &'static str = "command_applied";
    /// Wire value for PlatformEventApplied event.
    pub const PLATFORM_EVENT_APPLIED_WIRE: &'static str = "platform_event_applied";
    /// Wire value for ListenerRegistered event.
    pub const LISTENER_REGISTERED_WIRE: &'static str = "listener_registered";
    /// Wire value for AnalyticsReported event.
    pub const ANALYTICS_REPORTED_WIRE: &'static str = "analytics_reported";

    /// Returns the wire string representation of the event type.
    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::CommandApplied => Self::COMMAND_APPLIED_WIRE,
            Self::PlatformEventApplied => Self::PLATFORM_EVENT_APPLIED_WIRE,
            Self::ListenerRegistered => Self::LISTENER_REGISTERED_WIRE,
            Self::AnalyticsReported => Self::ANALYTICS_REPORTED_WIRE,
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

    /// Creates a new AnalyticsReported event.
    pub fn analytics_reported(payload: String) -> Self {
        Self {
            event_type: EngineEventType::AnalyticsReported,
            message: Some(payload),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn command_applied_sets_expected_type_and_optional_message() {
        let no_message = EngineEvent::command_applied(None);
        assert_eq!(no_message.event_type, EngineEventType::CommandApplied);
        assert!(no_message.message.is_none());

        let with_message = EngineEvent::command_applied(Some("ok".to_string()));
        assert_eq!(with_message.event_type, EngineEventType::CommandApplied);
        assert_eq!(with_message.message.as_deref(), Some("ok"));
    }

    #[test]
    fn analytics_reported_always_sets_payload_message() {
        let event = EngineEvent::analytics_reported("{\"metric\":1}".to_string());
        assert_eq!(event.event_type, EngineEventType::AnalyticsReported);
        assert_eq!(event.message.as_deref(), Some("{\"metric\":1}"));
    }
}
