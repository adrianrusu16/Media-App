/// A trait representing a media player that can execute playback commands.
///
/// In a state-of-the-art AAOS middleware, the actual media playback is often
/// handled by a platform-specific component (like ExoPlayer on Android).
/// This trait allows the Rust engine to remain agnostic of the underlying
/// player implementation while still being able to control it and receive status updates.
pub trait MediaPlayer: Send + Sync {
    /// Starts playback of the specified media.
    fn prepare(&mut self, media_id: &str);

    /// Starts or resumes playback.
    fn play(&mut self);

    /// Pauses playback.
    fn pause(&mut self);

    /// Stops playback.
    fn stop(&mut self);

    /// Seeks to a specific position.
    fn seek(&mut self, position_millis: u64);

    /// Sets the playback speed.
    fn set_speed(&mut self, speed: f32);

    /// Gets the current playback position.
    fn get_position(&self) -> u64;

    /// Gets the duration of the current media.
    fn get_duration(&self) -> u64;
}

/// A mock implementation of `MediaPlayer` for testing purposes.
#[derive(Default)]
pub struct MockPlayer {
    pub current_media_id: Option<String>,
    pub is_playing: bool,
    pub position: u64,
    pub speed: f32,
}

impl MockPlayer {
    pub fn new() -> Self {
        Self {
            current_media_id: None,
            is_playing: false,
            position: 0,
            speed: 1.0,
        }
    }
}

impl MediaPlayer for MockPlayer {
    fn prepare(&mut self, media_id: &str) {
        self.current_media_id = Some(media_id.to_string());
        self.position = 0;
    }

    fn play(&mut self) {
        self.is_playing = true;
    }

    fn pause(&mut self) {
        self.is_playing = false;
    }

    fn stop(&mut self) {
        self.is_playing = false;
        self.current_media_id = None;
    }

    fn seek(&mut self, position_millis: u64) {
        self.position = position_millis;
    }

    fn set_speed(&mut self, speed: f32) {
        self.speed = speed;
    }

    fn get_position(&self) -> u64 {
        self.position
    }

    fn get_duration(&self) -> u64 {
        180_000 // Mock 3 minutes
    }
}
