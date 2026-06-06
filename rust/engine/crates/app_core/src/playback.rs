#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PlaybackState {
    Idle,
    Playing,
    Paused,
}

impl PlaybackState {
    pub const IDLE_WIRE: &'static str = "idle";
    pub const PLAYING_WIRE: &'static str = "playing";
    pub const PAUSED_WIRE: &'static str = "paused";

    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::Idle => Self::IDLE_WIRE,
            Self::Playing => Self::PLAYING_WIRE,
            Self::Paused => Self::PAUSED_WIRE,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RestrictionState {
    Unknown,
}

impl RestrictionState {
    pub const UNKNOWN_WIRE: &'static str = "unknown";

    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::Unknown => Self::UNKNOWN_WIRE,
        }
    }
}
