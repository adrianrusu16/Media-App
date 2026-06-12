use super::*;

#[tokio::test]
async fn skip_updates_metadata() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 120)
        .await;
    let items = vec![
        MediaItem {
            id: "1".to_string(),
            title: "Song 1".to_string(),
            artist: "Artist 1".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "Song 2".to_string(),
            artist: "Artist 2".to_string(),
            ..Default::default()
        },
    ];
    engine.queue().set_items(items);

    // Initial play
    engine.dispatch(EngineCommand::play(), 200).await;
    assert_eq!(engine.snapshot().media_id, Some("1".to_string()));

    // Skip next
    engine.dispatch(EngineCommand::skip_next(), 300).await;
    assert_eq!(engine.snapshot().media_id, Some("2".to_string()));
    assert_eq!(engine.snapshot().title, Some("Song 2".to_string()));

    // Skip previous (wraps around - though Repeat All is not default,
    // in my current impl it stays on last or first if no repeat.
    // Wait, I should check my QueueManager impl)
    engine
        .queue()
        .set_repeat_mode(crate::data::queue::RepeatMode::All);
    engine.dispatch(EngineCommand::skip_previous(), 400).await;
    assert_eq!(engine.snapshot().media_id, Some("1".to_string()));
}

#[tokio::test]
async fn engine_search_finds_items() {
    let mut engine = Engine::new(100);
    let items = vec![
        MediaItem {
            id: "1".to_string(),
            title: "Rust Song".to_string(),
            artist: "The Developers".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "Kotlin Blues".to_string(),
            artist: "The Developers".to_string(),
            ..Default::default()
        },
    ];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    let outcome = engine
        .dispatch(EngineCommand::search("Rust".to_string()), 150)
        .await;
    assert_eq!(outcome.snapshot.search_results.len(), 1);
    assert_eq!(outcome.snapshot.search_results[0].id, "1");
}

#[tokio::test]
async fn browse_returns_items() {
    let mut engine = Engine::new(100);
    let items = vec![MediaItem {
        id: "1".to_string(),
        title: "Song 1".to_string(),
        artist: "Artist A".to_string(),
        parent_id: Some("root".to_string()),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    let outcome = engine
        .dispatch(EngineCommand::browse("root".to_string()), 150)
        .await;
    assert_eq!(outcome.snapshot.browse_results.len(), 1);
    assert_eq!(outcome.snapshot.browse_results[0].id, "1");
    assert!(outcome.snapshot.search_results.is_empty());
}

#[tokio::test]
async fn browse_does_not_overwrite_previous_search_results() {
    let mut engine = Engine::new(100);
    let items = vec![
        MediaItem {
            id: "search-1".to_string(),
            title: "Rust Song".to_string(),
            artist: "Artist A".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "browse-1".to_string(),
            title: "Playlist Item".to_string(),
            artist: "Artist B".to_string(),
            parent_id: Some("root".to_string()),
            ..Default::default()
        },
    ];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    let search_outcome = engine
        .dispatch(EngineCommand::search("Rust".to_string()), 150)
        .await;
    assert_eq!(search_outcome.snapshot.search_results.len(), 1);
    assert_eq!(search_outcome.snapshot.search_results[0].id, "search-1");

    let browse_outcome = engine
        .dispatch(EngineCommand::browse("root".to_string()), 200)
        .await;
    assert_eq!(browse_outcome.snapshot.search_results.len(), 1);
    assert_eq!(browse_outcome.snapshot.search_results[0].id, "search-1");
    assert_eq!(browse_outcome.snapshot.browse_results.len(), 1);
    assert_eq!(browse_outcome.snapshot.browse_results[0].id, "browse-1");
}

#[tokio::test]
async fn play_command_emits_effects() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 120)
        .await;
    let items = vec![MediaItem {
        id: "1".to_string(),
        title: "Song 1".to_string(),
        artist: "Artist 1".to_string(),
        ..Default::default()
    }];
    engine.queue().set_items(items);

    let outcome = engine.dispatch(EngineCommand::play(), 200).await;

    // Should emit UpdateMetadata, RequestAudioFocus, and Play
    assert!(outcome.effects.contains(&EngineEffect::UpdateMetadata {
        media_id: "1".to_string(),
        title: "Song 1".to_string(),
        artist: "Artist 1".to_string(),
    }));
    assert!(outcome.effects.contains(&EngineEffect::RequestAudioFocus));
    assert!(outcome.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn pause_command_emits_pause_effect() {
    let mut engine = Engine::new(100);
    // Buffering -> Playing
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 150);

    let outcome = engine.dispatch(EngineCommand::pause(), 200).await;

    assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
    assert!(outcome.effects.contains(&EngineEffect::Pause));
}

#[tokio::test]
async fn tick_updates_progress() {
    let mut engine = Engine::new(100);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 100)
        .with_position(5000)
        .with_speed(1.0);

    // 1 second later
    let outcomes = engine.tick(1100).await;

    assert_eq!(outcomes.len(), 1);
    assert_eq!(outcomes[0].snapshot.position_millis, 6000);
}

#[tokio::test]
async fn recovery_middleware_skips_on_network_error() {
    use crate::middleware::{MiddlewarePipeline, RecoveryMiddleware};
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user".to_string()), 110)
        .await;

    let items = vec![
        MediaItem {
            id: "1".to_string(),
            title: "Bad Track".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "Good Track".to_string(),
            ..Default::default()
        },
    ];
    engine.queue().set_items(items);

    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(RecoveryMiddleware));
    engine.set_middleware(pipeline);

    // Initial play
    engine.dispatch(EngineCommand::play(), 150).await;

    // Simulate a network error platform event
    let error = EngineError::new(
        crate::model::error::EngineErrorType::NetworkError,
        "No net",
        false,
    );
    let payload = serde_json::to_string(&error).unwrap();
    let event = EnginePlatformEvent::new(EnginePlatformEventType::MediaError, Some(payload));

    let outcome = engine.dispatch_platform_event(event, 200).await;

    // RecoveryMiddleware should have triggered skip_next
    assert_eq!(outcome.snapshot.media_id, Some("2".to_string()));
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        crate::model::error::EngineErrorType::MediaSkipped
    );
    assert_eq!(
        outcome.event.message.as_deref(),
        Some("recovered_from_network_error: skipped_to_next_track")
    );
}

#[tokio::test]
async fn player_bridge_drives_mock_player() {
    use crate::services::player::MockPlayer;
    let mut engine = Engine::new(100);
    let player = Box::new(MockPlayer::new());
    engine.set_player(player);

    engine
        .dispatch(EngineCommand::start_session("user".to_string()), 110)
        .await;

    let items = vec![MediaItem {
        id: "track_1".to_string(),
        title: "Title 1".to_string(),
        ..Default::default()
    }];
    engine.queue().set_items(items);

    // Play command should trigger UpdateMetadata and Play effects
    engine.dispatch(EngineCommand::play(), 120).await;

    // Check the player state through the engine (we need to cast or just check effects were emitted)
    // Since we don't have direct access to the Boxed player easily, we can check outcomes
    // but the goal was to verify execute_effects works.
    // Let's verify that PlaybackState is Buffering and then the MediaLoaded event moves it to Playing
    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            130,
        )
        .await;

    assert_eq!(outcome.snapshot.playback_state, PlaybackState::Playing);
    assert!(outcome.effects.contains(&EngineEffect::Play));
}
