/// Represents asynchronous effects that the engine requests the host to perform.
///
/// These effects typically represent side effects that cannot be executed
/// within the pure logic of the engine (e.g., controlling a hardware player).
#[derive(Clone, Debug, PartialEq)]
pub enum EngineEffect {
    /// Request the platform to start audio playback.
    Play,
    /// Request the platform to pause audio playback.
    Pause,
    /// Request the platform to stop audio playback.
    Stop,
    /// Request the platform to seek a specific position (in milliseconds).
    Seek(u64),
    /// Request the platform to set the playback speed.
    SetSpeed(f32),
    /// Request audio focus from the system.
    RequestAudioFocus,
    /// Abandon audio focus.
    AbandonAudioFocus,
    /// Request the platform to create a new MediaSession.
    SessionStarted { session_id: String },
    /// Request the platform to destroy the current MediaSession.
    SessionEnded,
    /// Update the system's media session metadata.
    UpdateMetadata {
        media_id: String,
        title: String,
        artist: String,
    },
}

impl EngineEffect {
    /// Wire value for Play effect.
    pub const PLAY_WIRE: &'static str = "play";
    /// Wire value for Pause effect.
    pub const PAUSE_WIRE: &'static str = "pause";
    /// Wire value for Stop effect.
    pub const STOP_WIRE: &'static str = "stop";
    /// Wire value for Seek effect.
    pub const SEEK_WIRE: &'static str = "seek";
    /// Wire value for RequestAudioFocus effect.
    pub const REQUEST_AUDIO_FOCUS_WIRE: &'static str = "request_audio_focus";
    /// Wire value for AbandonAudioFocus effect.
    pub const ABANDON_AUDIO_FOCUS_WIRE: &'static str = "abandon_audio_focus";
    /// Wire value for SetSpeed effect.
    pub const SET_SPEED_WIRE: &'static str = "set_speed";
    /// Wire value for SessionStarted effect.
    pub const SESSION_STARTED_WIRE: &'static str = "session_started";
    /// Wire value for SessionEnded effect.
    pub const SESSION_ENDED_WIRE: &'static str = "session_ended";
    /// Wire value for UpdateMetadata effect.
    pub const UPDATE_METADATA_WIRE: &'static str = "update_metadata";

    /// Returns the wire string representation of the effect type.
    pub fn as_wire(&self) -> &'static str {
        match self {
            Self::Play => Self::PLAY_WIRE,
            Self::Pause => Self::PAUSE_WIRE,
            Self::Stop => Self::STOP_WIRE,
            Self::Seek(_) => Self::SEEK_WIRE,
            Self::SetSpeed(_) => Self::SET_SPEED_WIRE,
            Self::RequestAudioFocus => Self::REQUEST_AUDIO_FOCUS_WIRE,
            Self::AbandonAudioFocus => Self::ABANDON_AUDIO_FOCUS_WIRE,
            Self::SessionStarted { .. } => Self::SESSION_STARTED_WIRE,
            Self::SessionEnded => Self::SESSION_ENDED_WIRE,
            Self::UpdateMetadata { .. } => Self::UPDATE_METADATA_WIRE,
        }
    }
}
