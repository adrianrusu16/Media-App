use super::*;

#[tokio::test]
async fn voice_play_starts_playback() {
    let mut engine = Engine::new(100);
    // Set up repository with some items
    let items = vec![MediaItem {
        id: "1".to_string(),
        title: "Song 1".to_string(),
        artist: "Artist 1".to_string(),
        album: Some("Album 1".to_string()),
        duration_millis: Some(123_000),
        thumbnail_url: Some("https://example.com/song1.jpg".to_string()),
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    // "Song 1" matches ID "1" in InMemoryRepository
    engine
        .dispatch(EngineCommand::voice_play("Song 1".to_string()), 150)
        .await;

    let snapshot = engine.snapshot();
    assert_eq!(snapshot.playback_state, PlaybackState::Buffering);
    assert_eq!(snapshot.media_id, Some("1".to_string()));
    assert_eq!(snapshot.album.as_deref(), Some("Album 1"));
    assert_eq!(snapshot.duration_millis, Some(123_000));
    assert_eq!(
        snapshot.thumbnail_url.as_deref(),
        Some("https://example.com/song1.jpg")
    );
}

#[tokio::test]
async fn voice_play_no_results_emits_notify() {
    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(InMemoryRepository::new(vec![])));

    let outcome = engine
        .dispatch(EngineCommand::voice_play("NonExistent".to_string()), 150)
        .await;

    assert!(outcome.effects.iter().any(|e| match e {
        EngineEffect::NotifyUser { message } => message.contains("No results found"),
        _ => false,
    }));
    // State should remain Idle (default)
    assert_eq!(engine.snapshot().playback_state, PlaybackState::Idle);
}

#[tokio::test]
async fn voice_interaction_lifecycle() {
    use crate::data::repository::MediaItem;
    use crate::services::voice::MockVoiceEngine;

    let mut engine = Engine::new(100);
    engine.set_voice_engine(Box::new(MockVoiceEngine::new()));
    engine.set_repository(Box::new(InMemoryRepository::new(vec![MediaItem {
        id: "jazz_1".to_string(),
        title: "Jazz Song".to_string(),
        artist: "Jazz Artist".to_string(),
        source_uri: Some("https://media.test/jazz_1".into()),
        ..Default::default()
    }])));

    // 1. Start interaction
    let outcome = engine
        .dispatch(EngineCommand::start_voice_interaction(), 110)
        .await;
    // Busy state is cleared at the end of dispatch, so we check if it was set
    // Actually, StartVoiceInteraction does NOT set busy=true in its current implementation,
    // it only pushes effects. Let's verify what it does.
    assert!(outcome.effects.contains(&EngineEffect::DuckAudio));
    assert!(outcome.effects.contains(&EngineEffect::StartAudioCapture));

    // 2. Process audio
    engine
        .dispatch(EngineCommand::process_voice_audio(vec![0; 100]), 120)
        .await;

    // 3. Stop interaction (triggers finish -> VoicePlay("jazz"))
    let outcome = engine
        .dispatch(EngineCommand::stop_voice_interaction(), 130)
        .await;
    assert!(!outcome.snapshot.is_busy);
    assert!(outcome.effects.contains(&EngineEffect::StopAudioCapture));
    assert!(outcome.effects.contains(&EngineEffect::UnduckAudio));

    // Verify it resolved to a play command
    assert_eq!(outcome.snapshot.playback_state, PlaybackState::Buffering);
    assert_eq!(outcome.snapshot.media_id, Some("jazz_1".to_string()));
}

#[tokio::test]
async fn voice_interaction_error_handling() {
    use crate::services::voice::MockVoiceEngine;

    let mut engine = Engine::new(100);
    let mut ve = MockVoiceEngine::new();
    ve.set_fail(true);
    engine.set_voice_engine(Box::new(ve));

    engine
        .dispatch(EngineCommand::start_voice_interaction(), 110)
        .await;
    let _outcome = engine
        .dispatch(EngineCommand::process_voice_audio(vec![0; 100]), 120)
        .await;

    // Process audio itself might not fail yet, but stop_voice_interaction will call finish()
    let outcome = engine
        .dispatch(EngineCommand::stop_voice_interaction(), 130)
        .await;

    assert!(outcome.effects.iter().any(|e| match e {
        EngineEffect::NotifyUser { message } => message.contains("Failed to recognize speech"),
        _ => false,
    }));
    assert_eq!(engine.snapshot().playback_state, PlaybackState::Idle);
}

#[tokio::test]
async fn stop_voice_interaction_without_engine_still_cleans_up_audio_effects() {
    let mut engine = Engine::new(100);

    let outcome = engine
        .dispatch(EngineCommand::stop_voice_interaction(), 130)
        .await;

    assert!(outcome.effects.contains(&EngineEffect::StopAudioCapture));
    assert!(outcome.effects.contains(&EngineEffect::UnduckAudio));
    assert!(!outcome.snapshot.is_busy);
    assert!(outcome.snapshot.voice_hypothesis.is_none());
}

#[tokio::test]
async fn stop_voice_interaction_no_match_notifies_user_and_cleans_up() {
    use crate::services::voice::MockVoiceEngine;

    let mut engine = Engine::new(100);
    engine.set_voice_engine(Box::new(MockVoiceEngine::new()));

    let outcome = engine
        .dispatch(EngineCommand::stop_voice_interaction(), 130)
        .await;

    assert!(outcome.effects.iter().any(|e| match e {
        EngineEffect::NotifyUser { message } => {
            message == "I didn't catch that. Could you repeat?"
        }
        _ => false,
    }));
    assert!(outcome.effects.contains(&EngineEffect::StopAudioCapture));
    assert!(outcome.effects.contains(&EngineEffect::UnduckAudio));
    assert!(!outcome.snapshot.is_busy);
}
