#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EngineCommandType {
    Bootstrap,
    Play,
    Pause,
    Unknown(String),
}

impl EngineCommandType {
    pub const BOOTSTRAP_WIRE: &'static str = "bootstrap";
    pub const PLAY_WIRE: &'static str = "play";
    pub const PAUSE_WIRE: &'static str = "pause";

    pub fn from_wire(value: impl Into<String>) -> Self {
        let value = value.into();
        match value.as_str() {
            Self::BOOTSTRAP_WIRE => Self::Bootstrap,
            Self::PLAY_WIRE => Self::Play,
            Self::PAUSE_WIRE => Self::Pause,
            _ => Self::Unknown(value),
        }
    }

    pub fn as_wire(&self) -> &str {
        match self {
            Self::Bootstrap => Self::BOOTSTRAP_WIRE,
            Self::Play => Self::PLAY_WIRE,
            Self::Pause => Self::PAUSE_WIRE,
            Self::Unknown(value) => value.as_str(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineCommand {
    pub command_type: EngineCommandType,
    pub payload: Option<String>,
}

impl EngineCommand {
    pub fn new(command_type: EngineCommandType, payload: Option<String>) -> Self {
        Self {
            command_type,
            payload,
        }
    }

    pub fn from_wire(command_type: impl Into<String>, payload: Option<String>) -> Self {
        Self::new(EngineCommandType::from_wire(command_type), payload)
    }
}
