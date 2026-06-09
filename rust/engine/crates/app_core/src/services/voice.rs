use crate::model::command::EngineCommand;
use std::fmt::Debug;

/// Result of a voice interaction.
#[derive(Debug, Clone, PartialEq)]
pub enum VoiceInteractionResult {
    /// The voice command was successfully parsed into an engine command.
    Command(EngineCommand),
    /// The voice interaction failed with a specific error.
    Error(String),
    /// No command could be determined from the input.
    NoMatch,
}

/// Abstract definition for a Voice Engine (ASR + NLU).
/// 
/// This allows the middleware to remain agnostic of the speech recognition
/// implementation (e.g., Vosk, Whisper, or Platform-provided).
pub trait VoiceEngine: Send + Sync + Debug {
    /// Processes a chunk of raw audio data (PCM 16-bit, 16kHz mono).
    fn process_audio_chunk(&mut self, chunk: &[i16]) -> Result<(), String>;

    /// Signals the end of audio input and retrieves the final result.
    fn finish(&mut self) -> Result<VoiceInteractionResult, String>;

    /// Resets the engine state for a new interaction.
    fn reset(&mut self);
}

/// A simple mock implementation of [VoiceEngine] for testing.
#[derive(Debug, Default)]
pub struct MockVoiceEngine {
    last_query: String,
    should_fail: bool,
}

impl MockVoiceEngine {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_fail(&mut self, fail: bool) {
        self.should_fail = fail;
    }
}

impl VoiceEngine for MockVoiceEngine {
    fn process_audio_chunk(&mut self, _chunk: &[i16]) -> Result<(), String> {
        if self.should_fail {
            return Err("Mock ASR failure".to_string());
        }
        // In a real mock, we might accumulate data or look for patterns
        self.last_query = "play jazz".to_string();
        Ok(())
    }

    fn finish(&mut self) -> Result<VoiceInteractionResult, String> {
        if self.should_fail {
            return Ok(VoiceInteractionResult::Error("Failed to recognize speech".to_string()));
        }
        
        if self.last_query.is_empty() {
            Ok(VoiceInteractionResult::NoMatch)
        } else {
            // Simplified NLU: map "play X" to VoicePlay(X)
            if self.last_query.starts_with("play ") {
                let query = self.last_query.replace("play ", "");
                Ok(VoiceInteractionResult::Command(EngineCommand::voice_play(query)))
            } else {
                Ok(VoiceInteractionResult::NoMatch)
            }
        }
    }

    fn reset(&mut self) {
        self.last_query.clear();
        self.should_fail = false;
    }
}
