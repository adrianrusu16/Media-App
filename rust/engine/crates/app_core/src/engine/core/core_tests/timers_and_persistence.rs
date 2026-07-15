use super::*;

#[tokio::test]
async fn sleep_timer_pauses_playback() {
    let mut engine = Engine::new(100);
    let items = vec![MediaItem {
        id: "1".to_string(),
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
    engine.queue().set_items(items); // Manually set queue
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 50)
        .await;

    engine.dispatch(EngineCommand::play(), 100).await;
    // Buffering -> Loaded -> Playing
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            110,
        )
        .await;
    assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);

    // Set sleep timer for 500ms
    engine
        .dispatch(EngineCommand::set_sleep_timer(Some(500)), 150)
        .await;

    // Tick before it fires
    engine.tick(300).await;
    assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);

    // Tick after it fires (150 + 500 = 650)
    engine.tick(700).await;
    assert_eq!(engine.snapshot().playback_state, PlaybackState::Paused);
}

#[tokio::test]
async fn engine_save_and_restore_cycle() {
    use crate::data::repository::MediaItem;
    use crate::test_utils::MockPersistence;
    use std::sync::Arc;

    let persistence = Arc::new(MockPersistence::new());

    let mut engine = Engine::new(100);
    let items = vec![MediaItem {
        id: "1".to_string(),
        title: "T1".to_string(),
        artist: "A1".to_string(),
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
    engine.queue().set_items(items);
    engine.set_persistence(Box::new(persistence.clone()));

    engine
        .dispatch(EngineCommand::start_session("u1".to_string()), 150)
        .await;
    engine.dispatch(EngineCommand::play(), 200).await;
    engine
        .dispatch_platform_event(
            crate::model::platform_event::EnginePlatformEvent::new(
                crate::model::platform_event::EnginePlatformEventType::MediaLoaded,
                None,
            ),
            250,
        )
        .await;

    assert_eq!(engine.snapshot().playback_state, PlaybackState::Playing);
    engine.save().expect("Save failed");

    // New engine, restore
    let mut engine2 = Engine::new(1000);
    // Explicitly set config to match engine1
    let mut config2 = crate::model::config::EngineConfig::new();
    config2.auto_resume = false;
    engine2
        .dispatch(EngineCommand::update_config(config2), 1010)
        .await;

    engine2.set_persistence(Box::new(persistence));
    let restored = engine2.restore().expect("Restore failed");

    assert!(restored);
    // By default, restore moves Playing -> Paused (unless auto_resume is true)
    assert_eq!(engine2.snapshot().playback_state, PlaybackState::Paused);
    assert_eq!(engine2.snapshot().media_id, Some("1".to_string()));
}

#[tokio::test]
async fn engine_auto_resume_on_restore() {
    use crate::data::repository::MediaItem;
    use crate::test_utils::MockPersistence;
    use std::sync::Arc;

    let persistence = Arc::new(MockPersistence::new());

    let mut engine = Engine::new(100);
    engine.set_persistence(Box::new(persistence.clone()));
    // Enable auto_resume
    let mut config = crate::model::config::EngineConfig::new();
    config.auto_resume = true;
    engine
        .dispatch(EngineCommand::update_config(config), 110)
        .await;

    let items = vec![MediaItem {
        id: "1".to_string(),
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.queue().set_items(items);
    engine
        .dispatch(EngineCommand::start_session("u1".to_string()), 150)
        .await;
    engine.dispatch(EngineCommand::play(), 200).await;
    engine
        .dispatch_platform_event(
            crate::model::platform_event::EnginePlatformEvent::new(
                crate::model::platform_event::EnginePlatformEventType::MediaLoaded,
                None,
            ),
            250,
        )
        .await;

    engine.save().expect("Save failed");

    // Restore in new engine with auto_resume config
    let mut engine2 = Engine::new(1000);
    let mut config2 = crate::model::config::EngineConfig::new();
    config2.auto_resume = true;
    engine2
        .dispatch(EngineCommand::update_config(config2), 1010)
        .await;
    engine2.set_persistence(Box::new(persistence));

    let restored = engine2.restore().expect("Restore failed");
    assert!(restored);
    // Should be Buffering (initiating reload) if auto_resume is true
    assert_eq!(engine2.snapshot().playback_state, PlaybackState::Buffering);
}

#[tokio::test]
async fn persistence_integration_test() {
    use crate::data::repository::MediaItem;
    use crate::test_utils::MockPersistence;
    use std::sync::Arc;

    let persistence = Arc::new(MockPersistence::new());
    let mut engine = Engine::new(100);
    engine.set_persistence(Box::new(persistence.clone()));

    // 1. Setup state
    let items = vec![MediaItem {
        id: "1".to_string(),
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.queue().set_items(items);
    engine
        .dispatch(EngineCommand::start_session("u1".to_string()), 150)
        .await;

    // 2. Save
    engine.save().expect("First save");

    // 3. Verify persistence contains data
    let state = persistence.load().unwrap().expect("Should have data");
    assert_eq!(state.snapshot.media_id, None); // Haven't played yet

    // 4. Play and save again
    engine.dispatch(EngineCommand::play(), 200).await;
    engine.save().expect("Second save");

    let state2 = persistence.load().unwrap().expect("Should have data");
    assert_eq!(state2.snapshot.media_id, Some("1".to_string()));
}
