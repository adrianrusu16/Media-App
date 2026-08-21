use super::*;

#[tokio::test]
async fn controls_are_derived_correctly() {
    let mut engine = Engine::new(100);
    let items = vec![
        MediaItem {
            id: "1".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            ..Default::default()
        },
    ];
    engine.queue().set_items(items);
    engine.refresh_controls();

    // Idle state
    let snapshot = engine.snapshot();
    assert!(snapshot.controls.show_play_icon);
    assert!(snapshot.controls.play_pause.is_enabled);
    assert!(snapshot.controls.skip_prev.is_enabled);
    assert!(snapshot.controls.skip_next.is_enabled);

    // Playing state
    engine
        .dispatch(EngineCommand::start_session("user".to_string()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await; // Moves to Buffering
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            130,
        )
        .await;

    let snapshot = engine.snapshot();
    assert_eq!(snapshot.playback_state, PlaybackState::Playing);
    assert!(!snapshot.controls.show_play_icon);
    assert!(snapshot.controls.play_pause.is_active);
    // engine.queue() is a mutable borrow, so we can't use snapshot (immutable borrow) after it.
    // We check the queue index before or after using the snapshot.
    let idx = engine.queue().current_index();
    assert_eq!(idx, Some(0));

    // Skip to end
    engine.dispatch(EngineCommand::skip_next(), 140).await;
    // Skip moves state to Buffering, then the player observation confirms it.
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            145,
        )
        .await;

    let idx = engine.queue().current_index();
    assert_eq!(idx, Some(1));
    let snapshot = engine.snapshot();
    // Since there are only 2 items, and we are at index 1, skip_next should be disabled
    // and skip_prev should be enabled.
    assert!(
        !snapshot.controls.skip_next.is_enabled,
        "Skip next should be disabled at the end of queue"
    );
    assert!(
        snapshot.controls.skip_prev.is_enabled,
        "Skip prev should be enabled when not at the start"
    );
}

#[tokio::test]
async fn update_config_updates_snapshot() {
    let mut engine = Engine::new(100);
    let new_config = crate::model::config::EngineConfig::new()
        .with_vehicle_name("Model S".to_string())
        .with_hifi(true);

    engine
        .dispatch(EngineCommand::update_config(new_config.clone()), 150)
        .await;

    let config = engine.config();
    assert_eq!(config.vehicle_name, "Model S");
    assert!(config.hifi_enabled);
}
