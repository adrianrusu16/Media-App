use super::*;

#[test]
fn starts_idle() {
    let engine = Engine::new(100);

    assert_eq!(PlaybackState::Idle, engine.snapshot().playback_state);
    assert_eq!(100, engine.snapshot().updated_at_epoch_millis);
}

#[tokio::test]
async fn play_command_moves_idle_to_buffering() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 150)
        .await;
    let outcome = engine
        .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
        .await;

    assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
    assert_eq!(200, outcome.snapshot.updated_at_epoch_millis);
    assert_eq!(EngineEventType::CommandApplied, outcome.event.event_type);
}

#[tokio::test]
async fn unknown_command_preserves_playback_state() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 150)
        .await;
    // Idle -> Buffering
    engine
        .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
        .await;

    let outcome = engine
        .dispatch(EngineCommand::from_wire("future_command", None), 300)
        .await;

    assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
    assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
    assert_eq!(Some("future_command".to_owned()), outcome.event.message);
}

#[tokio::test]
async fn skip_command_from_playing_moves_to_buffering() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 150)
        .await;
    // Idle -> Buffering
    engine
        .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
        .await;
    // Force Playing state for test (in real app, this would happen via system event)
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 250);

    let outcome = engine
        .dispatch(EngineCommand::new(EngineCommandType::SkipNext, None), 300)
        .await;

    assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
    assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
    assert_eq!(Some("skip_next".to_owned()), outcome.event.message);
}

#[tokio::test]
async fn platform_event_can_drive_state_transitions() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 150)
        .await;
    // Idle -> Buffering
    engine
        .dispatch(EngineCommand::new(EngineCommandType::Play, None), 200)
        .await;
    assert_eq!(PlaybackState::Buffering, engine.snapshot().playback_state);

    // Buffering -> Playing via Platform Event
    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            300,
        )
        .await;

    assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
    assert_eq!(300, outcome.snapshot.updated_at_epoch_millis);
    assert_eq!(
        EngineEventType::PlatformEventApplied,
        outcome.event.event_type
    );
    assert_eq!(Some("media_loaded".to_owned()), outcome.event.message);
}

#[tokio::test]
async fn platform_error_moves_to_error_state() {
    let mut engine = Engine::new(100);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 150);

    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaError, None),
            200,
        )
        .await;

    assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
}

#[tokio::test]
async fn audio_focus_loss_pauses_playing_engine() {
    let mut engine = Engine::new(100);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 150);

    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::AudioFocusChanged, None),
            200,
        )
        .await;

    assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
}
