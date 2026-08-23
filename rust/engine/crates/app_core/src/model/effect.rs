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
    /// Request the platform to notify the user (e.g., voice feedback).
    NotifyUser { message: String },
    /// Request the platform to start audio capture for voice interaction.
    StartAudioCapture,
    /// Request the platform to stop audio capture.
    StopAudioCapture,
    /// Request the platform to duck other audio sources (lower volume).
    DuckAudio,
    /// Request the platform to unduck audio sources.
    UnduckAudio,
    /// Signal the platform to consume the latest resolved playback projection
    /// for this media identifier and prepare it for playback.
    PreparePlaybackSource {
        media_id: String,
        /// Opaque generation which identifies this exact source load.
        playback_instance_id: u64,
    },
    /// Rebuild the platform player before loading the current, already-resolved
    /// source. This is reserved for a fatal local decoder failure; it must not
    /// trigger another backend capability resolution.
    RecreatePlayerAndLoad {
        media_id: String,
        playback_instance_id: u64,
        position_millis: u64,
    },
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
    /// Wire value for NotifyUser effect.
    pub const NOTIFY_USER_WIRE: &'static str = "notify_user";
    /// Wire value for StartAudioCapture effect.
    pub const START_AUDIO_CAPTURE_WIRE: &'static str = "start_audio_capture";
    /// Wire value for StopAudioCapture effect.
    pub const STOP_AUDIO_CAPTURE_WIRE: &'static str = "stop_audio_capture";
    /// Wire value for DuckAudio effect.
    pub const DUCK_AUDIO_WIRE: &'static str = "duck_audio";
    /// Wire value for UnduckAudio effect.
    pub const UNDUCK_AUDIO_WIRE: &'static str = "unduck_audio";
    /// Wire value for UpdateMetadata effect.
    pub const UPDATE_METADATA_WIRE: &'static str = "update_metadata";
    /// Wire value for PreparePlaybackSource effect.
    pub const PREPARE_PLAYBACK_SOURCE_WIRE: &'static str = "prepare_playback_source";
    /// Wire value for a local player recreation after a decoder failure.
    pub const RECREATE_PLAYER_AND_LOAD_WIRE: &'static str = "recreate_player_and_load";

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
            Self::NotifyUser { .. } => Self::NOTIFY_USER_WIRE,
            Self::StartAudioCapture => Self::START_AUDIO_CAPTURE_WIRE,
            Self::StopAudioCapture => Self::STOP_AUDIO_CAPTURE_WIRE,
            Self::DuckAudio => Self::DUCK_AUDIO_WIRE,
            Self::UnduckAudio => Self::UNDUCK_AUDIO_WIRE,
            Self::PreparePlaybackSource { .. } => Self::PREPARE_PLAYBACK_SOURCE_WIRE,
            Self::RecreatePlayerAndLoad { .. } => Self::RECREATE_PLAYER_AND_LOAD_WIRE,
            Self::UpdateMetadata { .. } => Self::UPDATE_METADATA_WIRE,
        }
    }
}
