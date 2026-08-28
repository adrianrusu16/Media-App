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
async fn pause_rebases_position_from_the_last_progress_tick() {
    let mut engine = Engine::new(1_000);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 1_000)
        .with_progress_tick(1_000)
        .with_position(10_000)
        .with_duration(Some(40_000))
        .with_speed(1.5);

    let outcome = engine.dispatch(EngineCommand::pause(), 3_000).await;

    assert_eq!(PlaybackState::Paused, outcome.snapshot.playback_state);
    assert_eq!(13_000, outcome.snapshot.position_millis);
    assert_eq!(3_000, outcome.snapshot.last_progress_tick_epoch_millis);
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
async fn unavailable_skip_next_from_playing_is_a_true_no_op() {
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

    assert_eq!(PlaybackState::Playing, outcome.snapshot.playback_state);
    assert_eq!(250, outcome.snapshot.updated_at_epoch_millis);
    assert_eq!(Some("skip_next".to_owned()), outcome.event.message);
    assert!(outcome.effects.is_empty());
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
async fn playback_completion_ends_current_item_without_advancing_the_queue() {
    let mut engine = Engine::new(100);
    let items = vec![
        MediaItem {
            id: "first".to_owned(),
            source_uri: Some("https://media.test/first".to_owned()),
            ..Default::default()
        },
        MediaItem {
            id: "second".to_owned(),
            source_uri: Some("https://media.test/second".to_owned()),
            ..Default::default()
        },
    ];
    engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
    engine.queue().set_items(items);
    engine
        .dispatch(EngineCommand::start_session("user1".to_owned()), 50)
        .await;
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_media(engine.queue().current_item().cloned().unwrap())
        .with_playback_state(PlaybackState::Playing, 100);

    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::playback_completed("first", 1_000, 1.0),
            200,
        )
        .await;

    assert_eq!(PlaybackState::Ended, outcome.snapshot.playback_state);
    assert_eq!(Some("first"), outcome.snapshot.media_id.as_deref());
    assert_eq!(1_000, outcome.snapshot.position_millis);
    assert_eq!(Some(1_000), outcome.snapshot.duration_millis);
    assert_eq!(
        Some("first"),
        engine.queue().current_item().map(|item| item.id.as_str())
    );
    assert_eq!(
        vec![EngineEffect::Pause, EngineEffect::AbandonAudioFocus],
        outcome.effects
    );

    let restarted = engine.dispatch(EngineCommand::play(), 300).await;
    assert_eq!(PlaybackState::Buffering, restarted.snapshot.playback_state);
    assert_eq!(0, restarted.snapshot.position_millis);
    assert!(restarted.effects.contains(&EngineEffect::Seek(0)));
    assert!(restarted.effects.contains(&EngineEffect::Play));
    assert!(
        !restarted
            .effects
            .iter()
            .any(|effect| matches!(effect, EngineEffect::PreparePlaybackSource { .. }))
    );

    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Ended, 350)
        .with_position(1_000);
    let skip_restarted = engine.dispatch(EngineCommand::skip_previous(), 400).await;
    assert_eq!(
        PlaybackState::Buffering,
        skip_restarted.snapshot.playback_state
    );
    assert_eq!(0, skip_restarted.snapshot.position_millis);
    assert_eq!(Some("first"), skip_restarted.snapshot.media_id.as_deref());
    assert!(skip_restarted.effects.contains(&EngineEffect::Seek(0)));
    assert!(skip_restarted.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn stale_player_observation_cannot_mutate_a_newer_selection() {
    let mut engine = Engine::new(100);
    let items = vec![
        MediaItem {
            id: "a".into(),
            source_uri: Some("https://media.test/a".into()),
            ..Default::default()
        },
        MediaItem {
            id: "b".into(),
            source_uri: Some("https://media.test/b".into()),
            ..Default::default()
        },
    ];
    engine.set_repository(Box::new(InMemoryRepository::new(items.clone())));
    engine.queue().set_items(items);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    engine.dispatch(EngineCommand::skip_next(), 130).await;

    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaError,
                Some(r#"{"version":1,"playback_instance_id":1,"kind":"unknown"}"#.into()),
            ),
            140,
        )
        .await;

    assert_eq!(Some("b"), outcome.snapshot.media_id.as_deref());
    assert_eq!(PlaybackState::Buffering, outcome.snapshot.playback_state);
    assert!(outcome.effects.is_empty());
}

#[tokio::test]
async fn decoder_failure_recreates_the_local_player_once_without_resolving_a_new_source() {
    let mut engine = Engine::new(100);
    let item = MediaItem {
        id: "track-1".into(),
        source_uri: Some("https://media.test/capability".into()),
        ..Default::default()
    };
    engine.set_repository(Box::new(InMemoryRepository::new(vec![item.clone()])));
    engine.queue().set_items(vec![item]);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaLoaded,
                Some(r#"{"version":1,"playback_instance_id":1}"#.into()),
            ),
            130,
        )
        .await;
    let checkpoint = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(r#"{"version":1,"playback_instance_id":1,"position_ms":182900}"#.into()),
            ),
            135,
        )
        .await;
    assert_eq!(182_900, checkpoint.snapshot.position_millis);
    assert_eq!(135, checkpoint.snapshot.last_progress_tick_epoch_millis);
    assert!(checkpoint.effects.is_empty());

    let recovered = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaError,
                Some(r#"{"version":1,"playback_instance_id":1,"kind":"decoder_failed"}"#.into()),
            ),
            140,
        )
        .await;

    assert_eq!(PlaybackState::Recovering, recovered.snapshot.playback_state);
    assert!(
        recovered
            .effects
            .contains(&EngineEffect::RecreatePlayerAndLoad {
                media_id: "track-1".into(),
                playback_instance_id: 2,
                position_millis: 182_900,
            })
    );
    assert!(recovered.effects.contains(&EngineEffect::Play));

    let exhausted = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaError,
                Some(r#"{"version":1,"playback_instance_id":2,"kind":"decoder_failed"}"#.into()),
            ),
            150,
        )
        .await;

    assert_eq!(PlaybackState::Error, exhausted.snapshot.playback_state);
    assert!(matches!(
        exhausted.effects.as_slice(),
        [EngineEffect::NotifyUser { message }]
            if message == "Playback could not continue because the device audio decoder failed."
    ));
}

#[tokio::test]
async fn decoder_recovery_preserves_a_paused_player_intent() {
    let mut engine = Engine::new(100);
    let item = MediaItem {
        id: "track-1".into(),
        source_uri: Some("https://media.test/capability".into()),
        ..Default::default()
    };
    engine.set_repository(Box::new(InMemoryRepository::new(vec![item.clone()])));
    engine.queue().set_items(vec![item]);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaLoaded,
                Some(r#"{"version":1,"playback_instance_id":1}"#.into()),
            ),
            130,
        )
        .await;

    let recovered = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaError,
                Some(r#"{"version":1,"playback_instance_id":1,"kind":"decoder_failed","position_ms":183350,"decoder":"c2.android.mp3.decoder","error_code":4003,"phase":"decoding","play_when_ready":false}"#.into()),
            ),
            140,
        )
        .await;

    assert_eq!(PlaybackState::Recovering, recovered.snapshot.playback_state);
    assert!(
        recovered
            .effects
            .iter()
            .any(|effect| matches!(effect, EngineEffect::RecreatePlayerAndLoad { .. }))
    );
    assert!(!recovered.effects.contains(&EngineEffect::Play));
    assert!(!recovered.effects.contains(&EngineEffect::RequestAudioFocus));
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
async fn typed_audio_focus_resumes_only_when_playback_intent_is_active() {
    let mut engine = Engine::new(100);
    engine.dispatch(EngineCommand::play(), 125).await;
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 150);

    let loss = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"loss_transient"}"#.into()),
            ),
            200,
        )
        .await;

    assert_eq!(PlaybackState::Paused, loss.snapshot.playback_state);
    assert!(loss.effects.contains(&EngineEffect::Pause));

    let gain = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"gain"}"#.into()),
            ),
            225,
        )
        .await;

    assert_eq!(PlaybackState::Playing, gain.snapshot.playback_state);
    assert!(gain.effects.contains(&EngineEffect::Play));

    engine.dispatch(EngineCommand::pause(), 250).await;
    let gain_after_user_pause = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"gain"}"#.into()),
            ),
            275,
        )
        .await;

    assert_eq!(
        PlaybackState::Paused,
        gain_after_user_pause.snapshot.playback_state
    );
    assert!(!gain_after_user_pause.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn audio_focus_request_failure_clears_playback_intent_and_blocks_later_gain() {
    let mut engine = Engine::new(100);
    engine.dispatch(EngineCommand::play(), 125).await;
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Buffering, 140);

    let failed = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusRequestResult,
                Some(r#"{"version":1,"result":"failed"}"#.into()),
            ),
            150,
        )
        .await;

    assert_eq!(PlaybackState::Paused, failed.snapshot.playback_state);
    assert!(failed.effects.contains(&EngineEffect::Pause));

    let gain = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"gain"}"#.into()),
            ),
            175,
        )
        .await;

    assert_eq!(PlaybackState::Paused, gain.snapshot.playback_state);
    assert!(!gain.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn audio_focus_request_delayed_keeps_buffering_until_gain() {
    let mut engine = Engine::new(100);
    engine.dispatch(EngineCommand::play(), 125).await;
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Buffering, 140);

    let delayed = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusRequestResult,
                Some(r#"{"version":1,"result":"delayed"}"#.into()),
            ),
            150,
        )
        .await;

    assert_eq!(PlaybackState::Buffering, delayed.snapshot.playback_state);
    assert!(!delayed.effects.contains(&EngineEffect::Pause));

    let gain = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"gain"}"#.into()),
            ),
            175,
        )
        .await;

    assert_eq!(PlaybackState::Playing, gain.snapshot.playback_state);
    assert!(gain.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn audio_focus_duck_keeps_music_state_and_permanent_loss_does_not_resume() {
    let mut engine = Engine::new(100);
    engine.dispatch(EngineCommand::play(), 125).await;
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 150);

    let duck = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"duck"}"#.into()),
            ),
            175,
        )
        .await;

    assert_eq!(PlaybackState::Playing, duck.snapshot.playback_state);
    assert!(!duck.effects.contains(&EngineEffect::Pause));

    let loss = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"loss"}"#.into()),
            ),
            200,
        )
        .await;

    assert_eq!(PlaybackState::Paused, loss.snapshot.playback_state);
    assert!(loss.effects.contains(&EngineEffect::Pause));

    let gain = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::AudioFocusChanged,
                Some(r#"{"version":1,"focus_change":"gain"}"#.into()),
            ),
            225,
        )
        .await;

    assert_eq!(PlaybackState::Paused, gain.snapshot.playback_state);
    assert!(!gain.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn platform_safety_events_update_snapshot_without_changing_playback() {
    use crate::model::playback::{DrivingState, RestrictionState};

    let mut engine = Engine::new(1);
    let parked = EnginePlatformEvent::new(
        EnginePlatformEventType::VehicleDrivingStateChanged,
        Some(DrivingState::PARKED_WIRE.to_owned()),
    );

    let parked_outcome = engine.dispatch_platform_event(parked, 2).await;

    assert_eq!(DrivingState::Parked, parked_outcome.snapshot.driving_state);
    assert_eq!(PlaybackState::Idle, parked_outcome.snapshot.playback_state);

    let restricted = EnginePlatformEvent::new(
        EnginePlatformEventType::UxRestrictionsChanged,
        Some(RestrictionState::RESTRICTED_WIRE.to_owned()),
    );

    let restricted_outcome = engine.dispatch_platform_event(restricted, 3).await;

    assert_eq!(
        RestrictionState::Restricted,
        restricted_outcome.snapshot.restriction_state
    );
    assert_eq!(
        PlaybackState::Idle,
        restricted_outcome.snapshot.playback_state
    );
}

#[tokio::test]
async fn unknown_vehicle_payload_fails_closed_to_unknown() {
    use crate::model::playback::DrivingState;

    let mut engine = Engine::new(1);
    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::VehicleDrivingStateChanged,
                Some("future-state".to_owned()),
            ),
            2,
        )
        .await;

    assert_eq!(DrivingState::Unknown, outcome.snapshot.driving_state);
}

#[tokio::test]
async fn seek_settling_ignores_stale_position_checkpoints() {
    let mut engine = Engine::new(100);
    let item = MediaItem {
        id: "track-1".into(),
        source_uri: Some("https://media.test/track-1".into()),
        duration_millis: Some(180_000),
        ..Default::default()
    };
    engine.set_repository(Box::new(InMemoryRepository::new(vec![item.clone()])));
    engine.queue().set_items(vec![item]);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaLoaded,
                Some(r#"{"version":1,"playback_instance_id":1}"#.into()),
            ),
            130,
        )
        .await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(r#"{"version":1,"playback_instance_id":1,"position_ms":55000}"#.into()),
            ),
            140,
        )
        .await;

    let restarted = engine.dispatch(EngineCommand::seek(0), 150).await;
    assert_eq!(restarted.snapshot.position_millis, 0);

    let stale = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(r#"{"version":1,"playback_instance_id":1,"position_ms":55000}"#.into()),
            ),
            160,
        )
        .await;
    assert_eq!(stale.snapshot.position_millis, 0);

    let settled = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(r#"{"version":1,"playback_instance_id":1,"position_ms":80}"#.into()),
            ),
            170,
        )
        .await;
    assert_eq!(settled.snapshot.position_millis, 80);
}

#[tokio::test]
async fn decoder_duration_replaces_catalog_length_and_does_not_end_early() {
    let mut engine = Engine::new(100);
    let item = MediaItem {
        id: "track-1".into(),
        source_uri: Some("https://media.test/track-1".into()),
        duration_millis: Some(252_395),
        ..Default::default()
    };
    engine.set_repository(Box::new(InMemoryRepository::new(vec![item.clone()])));
    engine.queue().set_items(vec![item]);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    let loaded = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaLoaded,
                Some(r#"{"version":1,"playback_instance_id":1,"duration_ms":270000}"#.into()),
            ),
            130,
        )
        .await;
    assert_eq!(Some(270_000), loaded.snapshot.duration_millis);
    assert_ne!(PlaybackState::Ended, loaded.snapshot.playback_state);

    let past_catalog = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(
                    r#"{"version":1,"playback_instance_id":1,"position_ms":260000,"duration_ms":270000}"#.into(),
                ),
            ),
            140,
        )
        .await;
    assert_eq!(260_000, past_catalog.snapshot.position_millis);
    assert_eq!(Some(270_000), past_catalog.snapshot.duration_millis);
    assert_ne!(PlaybackState::Ended, past_catalog.snapshot.playback_state);
}

#[tokio::test]
async fn playback_completion_snaps_progress_to_decoder_duration() {
    let mut engine = Engine::new(100);
    let item = MediaItem {
        id: "track-1".into(),
        source_uri: Some("https://media.test/track-1".into()),
        duration_millis: Some(200_000),
        ..Default::default()
    };
    engine.set_repository(Box::new(InMemoryRepository::new(vec![item.clone()])));
    engine.queue().set_items(vec![item]);
    engine
        .dispatch(EngineCommand::start_session("user".into()), 110)
        .await;
    engine.dispatch(EngineCommand::play(), 120).await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::MediaLoaded,
                Some(r#"{"version":1,"playback_instance_id":1,"duration_ms":200000}"#.into()),
            ),
            130,
        )
        .await;
    engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(
                    r#"{"version":1,"playback_instance_id":1,"position_ms":196000,"duration_ms":200000}"#.into(),
                ),
            ),
            140,
        )
        .await;

    let completed = engine
        .dispatch_platform_event(
            EnginePlatformEvent::playback_completed("track-1", 200_000, 0.98),
            150,
        )
        .await;
    assert_eq!(PlaybackState::Ended, completed.snapshot.playback_state);
    assert_eq!(200_000, completed.snapshot.position_millis);
    assert_eq!(Some(200_000), completed.snapshot.duration_millis);

    let stale = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(
                EnginePlatformEventType::PlaybackPositionCheckpoint,
                Some(
                    r#"{"version":1,"playback_instance_id":1,"position_ms":196000,"duration_ms":200000}"#.into(),
                ),
            ),
            160,
        )
        .await;
    assert_eq!(200_000, stale.snapshot.position_millis);
    assert_eq!(PlaybackState::Ended, stale.snapshot.playback_state);
}
