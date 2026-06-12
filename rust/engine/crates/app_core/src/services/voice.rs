use crate::model::command::EngineCommand;
use std::fmt::Debug;
#[cfg(feature = "vosk-engine")]
use vosk::{Model, Recognizer};

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

    /// Retrieves the current partial hypothesis from the engine.
    ///
    /// This allows the UI to show real-time feedback of what is being recognized.
    fn get_partial_hypothesis(&self) -> String {
        String::new()
    }

    /// Resets the engine state for a new interaction.
    fn reset(&mut self);

    /// Updates the vocabulary or grammar for the voice engine.
    ///
    /// This is useful for engines like Vosk that support dynamic grammar
    /// to improve accuracy by limiting the expected vocabulary.
    fn set_vocabulary(&mut self, _vocabulary: Vec<String>) -> Result<(), String> {
        Ok(())
    }

    /// Provides contextual metadata to the voice engine.
    ///
    /// This can include the current track title, artist, or recently played items
    /// to help the NLU resolve ambiguous commands (e.g., "play this artist again").
    fn set_context(&mut self, _current_track: Option<(String, String)>) {
        // Default: do nothing
    }
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
            return Ok(VoiceInteractionResult::Error(
                "Failed to recognize speech".to_string(),
            ));
        }

        if self.last_query.is_empty() {
            Ok(VoiceInteractionResult::NoMatch)
        } else {
            // Simplified NLU: map "play X" to VoicePlay(X)
            if self.last_query.starts_with("play ") {
                let query = self.last_query.replace("play ", "");
                Ok(VoiceInteractionResult::Command(EngineCommand::voice_play(
                    query,
                )))
            } else {
                Ok(VoiceInteractionResult::NoMatch)
            }
        }
    }

    fn get_partial_hypothesis(&self) -> String {
        self.last_query.clone()
    }

    fn reset(&mut self) {
        self.last_query.clear();
        // Keep should_fail if we want to test multiple failures,
        // but core.rs calls ve.reset() in StartVoiceInteraction
    }
}

/// A [VoiceEngine] implementation powered by Vosk.
#[cfg(feature = "vosk-engine")]
pub struct VoskVoiceEngine {
    recognizer: Recognizer,
    model: Model,
    last_partial: String,
}

#[cfg(feature = "vosk-engine")]
impl VoskVoiceEngine {
    /// Creates a new Vosk engine with the specified model path.
    pub fn new(model_path: &str) -> Result<Self, String> {
        let model =
            Model::new(model_path).ok_or_else(|| "Failed to load Vosk model".to_string())?;
        let recognizer = Recognizer::new(&model, 16000.0)
            .ok_or_else(|| "Failed to create Vosk recognizer".to_string())?;

        Ok(Self {
            recognizer,
            model,
            last_partial: String::new(),
        })
    }
}

#[cfg(feature = "vosk-engine")]
impl Debug for VoskVoiceEngine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("VoskVoiceEngine")
            .field("last_partial", &self.last_partial)
            .finish()
    }
}

#[cfg(feature = "vosk-engine")]
impl VoiceEngine for VoskVoiceEngine {
    fn process_audio_chunk(&mut self, chunk: &[i16]) -> Result<(), String> {
        let _ = self.recognizer.accept_waveform(chunk);
        let partial = self.recognizer.partial_result();
        self.last_partial = partial.partial.to_string();
        Ok(())
    }

    fn finish(&mut self) -> Result<VoiceInteractionResult, String> {
        let result = self.recognizer.final_result();
        let query = result
            .single()
            .map(|r| r.text.to_string())
            .unwrap_or_default();

        if query.is_empty() {
            Ok(VoiceInteractionResult::NoMatch)
        } else if query.starts_with("play ") {
            let media_query = query.replace("play ", "");
            Ok(VoiceInteractionResult::Command(EngineCommand::voice_play(
                media_query,
            )))
        } else {
            // Fallback for general queries if needed
            Ok(VoiceInteractionResult::Command(EngineCommand::voice_play(
                query,
            )))
        }
    }

    fn get_partial_hypothesis(&self) -> String {
        self.last_partial.clone()
    }

    fn reset(&mut self) {
        self.recognizer.reset();
        self.last_partial.clear();
    }

    fn set_vocabulary(&mut self, vocabulary: Vec<String>) -> Result<(), String> {
        // In Vosk-rs, if set_grm is not available on Recognizer,
        // we may need to re-create the recognizer with the grammar.
        if !vocabulary.is_empty() {
            if let Some(new_rec) = Recognizer::new_with_grammar(&self.model, 16000.0, &vocabulary) {
                self.recognizer = new_rec;
            } else {
                return Err("Failed to create Vosk recognizer with grammar".to_string());
            }
        }
        Ok(())
    }
}

/// A fallback [VoiceEngine] for environments where the Vosk library is not available.
#[cfg(not(feature = "vosk-engine"))]
pub struct VoskVoiceEngine {
    last_partial: String,
}

#[cfg(not(feature = "vosk-engine"))]
impl VoskVoiceEngine {
    pub fn new(_model_path: &str) -> Result<Self, String> {
        Ok(Self {
            last_partial: String::new(),
        })
    }
}

#[cfg(not(feature = "vosk-engine"))]
impl Debug for VoskVoiceEngine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("VoskVoiceEngine (Fallback)")
            .field("last_partial", &self.last_partial)
            .finish()
    }
}

#[cfg(not(feature = "vosk-engine"))]
impl VoiceEngine for VoskVoiceEngine {
    fn process_audio_chunk(&mut self, _chunk: &[i16]) -> Result<(), String> {
        self.last_partial = "fallback hypothesis".to_string();
        Ok(())
    }

    fn finish(&mut self) -> Result<VoiceInteractionResult, String> {
        Ok(VoiceInteractionResult::NoMatch)
    }

    fn get_partial_hypothesis(&self) -> String {
        self.last_partial.clone()
    }

    fn reset(&mut self) {
        self.last_partial.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fallback_vosk_engine() {
        let mut ve = VoskVoiceEngine::new("any_path").unwrap();
        assert_eq!(ve.get_partial_hypothesis(), "");

        ve.process_audio_chunk(&[0; 100]).unwrap();
        assert_eq!(ve.get_partial_hypothesis(), "fallback hypothesis");

        let result = ve.finish().unwrap();
        assert!(matches!(result, VoiceInteractionResult::NoMatch));

        ve.reset();
        assert_eq!(ve.get_partial_hypothesis(), "");
    }

    #[test]
    fn test_mock_voice_engine_no_match() {
        let mut ve = MockVoiceEngine::new();
        // MockVoiceEngine returns "jazz" by default unless set_fail(true)
        // But let's verify reset works
        ve.reset();
        assert_eq!(ve.get_partial_hypothesis(), "");
    }

    #[test]
    fn test_mock_voice_engine_success_maps_to_voice_play() {
        let mut ve = MockVoiceEngine::new();
        ve.process_audio_chunk(&[0; 10]).unwrap();
        // The mock accumulates "play jazz" and the partial hypothesis reflects it.
        assert_eq!(ve.get_partial_hypothesis(), "play jazz");

        let result = ve.finish().unwrap();
        match result {
            VoiceInteractionResult::Command(cmd) => {
                if let crate::model::command::EngineCommandType::VoicePlay { query } =
                    cmd.command_type
                {
                    assert_eq!(query, "jazz");
                } else {
                    panic!("Expected VoicePlay command");
                }
            }
            other => panic!("Expected a command, got {other:?}"),
        }
    }

    #[test]
    fn test_mock_voice_engine_finish_without_audio_is_no_match() {
        let mut ve = MockVoiceEngine::new();
        // No audio processed, so the accumulated query is empty.
        assert!(matches!(
            ve.finish().unwrap(),
            VoiceInteractionResult::NoMatch
        ));
    }

    #[test]
    fn test_mock_voice_engine_process_audio_fails_when_set_to_fail() {
        let mut ve = MockVoiceEngine::new();
        ve.set_fail(true);
        let err = ve.process_audio_chunk(&[0; 10]).unwrap_err();
        assert_eq!(err, "Mock ASR failure");
    }

    #[test]
    fn test_mock_voice_engine_finish_returns_error_when_set_to_fail() {
        let mut ve = MockVoiceEngine::new();
        ve.set_fail(true);
        match ve.finish().unwrap() {
            VoiceInteractionResult::Error(msg) => {
                assert_eq!(msg, "Failed to recognize speech");
            }
            other => panic!("Expected an error result, got {other:?}"),
        }
    }

    #[test]
    fn test_mock_voice_engine_reset_clears_accumulated_query() {
        let mut ve = MockVoiceEngine::new();
        ve.process_audio_chunk(&[0; 10]).unwrap();
        assert_eq!(ve.get_partial_hypothesis(), "play jazz");

        ve.reset();
        assert_eq!(ve.get_partial_hypothesis(), "");
        // After reset, finishing with no audio yields NoMatch.
        assert!(matches!(
            ve.finish().unwrap(),
            VoiceInteractionResult::NoMatch
        ));
    }

    #[test]
    fn test_voice_engine_default_trait_methods() {
        // `set_vocabulary` and `set_context` have default no-op implementations
        // that should succeed without altering behavior.
        let mut ve = MockVoiceEngine::new();
        assert!(
            ve.set_vocabulary(vec!["play".to_string(), "jazz".to_string()])
                .is_ok()
        );
        ve.set_context(Some(("Song A".to_string(), "Artist X".to_string())));
        ve.set_context(None);
    }

    #[test]
    fn test_fallback_vosk_engine_set_vocabulary_is_ok() {
        let mut ve = VoskVoiceEngine::new("any_path").unwrap();
        // The fallback engine accepts vocabulary updates without error.
        assert!(ve.set_vocabulary(vec!["play".to_string()]).is_ok());
        assert!(ve.set_vocabulary(vec![]).is_ok());
    }
}
