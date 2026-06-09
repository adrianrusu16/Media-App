use serde::{Deserialize, Serialize};

/// Represents the current playback status of the media engine.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum PlaybackState {
    /// No media is loaded or active.
    #[default]
    Idle,
    /// Media is currently playing.
    Playing,
    /// Media playback is temporarily suspended.
    Paused,
    /// The engine is preparing media for playback (e.g., buffering).
    Buffering,
    /// An error occurred during playback.
    Error,
}

impl PlaybackState {
    /// Wire value for Idle state.
    pub const IDLE_WIRE: &'static str = "idle";
    /// Wire value for Playing state.
    pub const PLAYING_WIRE: &'static str = "playing";
    /// Wire value for Paused state.
    pub const PAUSED_WIRE: &'static str = "paused";
    /// Wire value for Buffering state.
    pub const BUFFERING_WIRE: &'static str = "buffering";
    /// Wire value for Error state.
    pub const ERROR_WIRE: &'static str = "error";

    /// Returns the wire string representation of the playback state.
    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::Idle => Self::IDLE_WIRE,
            Self::Playing => Self::PLAYING_WIRE,
            Self::Paused => Self::PAUSED_WIRE,
            Self::Buffering => Self::BUFFERING_WIRE,
            Self::Error => Self::ERROR_WIRE,
        }
    }
}

/// Represents restrictions that can be applied to playback (e.g., driver distraction).
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum RestrictionState {
    /// No specific restriction information is available.
    #[default]
    Unknown,
}

impl RestrictionState {
    /// Wire value for Unknown restriction state.
    pub const UNKNOWN_WIRE: &'static str = "unknown";

    /// Returns the wire string representation of the restriction state.
    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::Unknown => Self::UNKNOWN_WIRE,
        }
    }
}

/// Represents the state of a specific player control (e.g., Play, Skip).
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct ControlState {
    /// Whether the control should be visible to the user.
    pub is_visible: bool,
    /// Whether the control is enabled and can be interacted with.
    pub is_enabled: bool,
    /// Whether the control is currently in an "active" or "toggled" state (e.g., Repeat mode).
    pub is_active: bool,
}

/// A collection of player controls that the UI should display.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub struct PlayerControls {
    /// State of the Play/Pause toggle control.
    pub play_pause: ControlState,
    /// State of the Skip to Next control.
    pub skip_next: ControlState,
    /// State of the Skip to Previous control.
    pub skip_prev: ControlState,
    /// Whether the "Play" icon should be shown (true) or "Pause" icon (false).
    pub show_play_icon: bool,
}
