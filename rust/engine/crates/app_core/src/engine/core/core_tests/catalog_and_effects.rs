use super::*;
use crate::networking::PlaybackSource;
use crate::networking::audio_source_client::{MockAudioSourceClient, MockPlaybackPort};
use std::sync::Arc;

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
            source_uri: Some("https://media.test/1".into()),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "Song 2".to_string(),
            artist: "Artist 2".to_string(),
            source_uri: Some("https://media.test/2".into()),
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
async fn skip_previous_restarts_current_item_after_threshold_and_selects_previous_before_it() {
    let mut engine = Engine::new(100);
    engine
        .dispatch(EngineCommand::start_session("user1".to_string()), 120)
        .await;
    engine.queue().set_items(vec![
        MediaItem {
            id: "1".into(),
            title: "Song 1".into(),
            source_uri: Some("https://media.test/1".into()),
            ..Default::default()
        },
        MediaItem {
            id: "2".into(),
            title: "Song 2".into(),
            source_uri: Some("https://media.test/2".into()),
            ..Default::default()
        },
    ]);
    engine.queue().set_current_index(1);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_media(engine.queue().current_item().cloned().unwrap())
        .with_playback_state(PlaybackState::Playing, 150)
        .with_position(12_000);

    let restarted = engine.dispatch(EngineCommand::skip_previous(), 200).await;
    assert_eq!(restarted.snapshot.media_id.as_deref(), Some("2"));
    assert_eq!(restarted.snapshot.position_millis, 0);
    assert!(restarted.effects.contains(&EngineEffect::Seek(0)));
    assert!(
        !restarted
            .effects
            .iter()
            .any(|effect| matches!(effect, EngineEffect::PreparePlaybackSource { .. }))
    );

    engine.snapshot.position_millis = 5_000;
    let selected = engine.dispatch(EngineCommand::skip_previous(), 250).await;
    assert_eq!(selected.snapshot.media_id.as_deref(), Some("1"));
    assert_eq!(selected.snapshot.playback_state, PlaybackState::Buffering);
    assert_eq!(selected.snapshot.position_millis, 0);
    assert!(
        selected
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "1".into(),
                playback_instance_id: 1,
                position_millis: 0,
            })
    );
}

#[tokio::test]
async fn play_queue_preserves_order_and_exposes_boundary_transport() {
    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(InMemoryRepository::new(vec![
        MediaItem {
            id: "first".into(),
            title: "First".into(),
            source_uri: Some("https://media.test/first".into()),
            ..Default::default()
        },
        MediaItem {
            id: "second".into(),
            title: "Second".into(),
            source_uri: Some("https://media.test/second".into()),
            ..Default::default()
        },
    ])));

    let first = engine
        .dispatch(
            EngineCommand::play_queue(vec!["second".into(), "first".into()], 0),
            200,
        )
        .await;
    assert_eq!(first.snapshot.media_id.as_deref(), Some("second"));
    assert!(first.snapshot.controls.skip_prev.is_enabled);
    assert!(first.snapshot.controls.skip_next.is_enabled);

    let next = engine.dispatch(EngineCommand::skip_next(), 250).await;
    assert_eq!(next.snapshot.media_id.as_deref(), Some("first"));
    assert!(!next.snapshot.controls.skip_next.is_enabled);
    assert!(next.snapshot.controls.skip_prev.is_enabled);

    let boundary = engine.dispatch(EngineCommand::skip_next(), 300).await;
    assert_eq!(boundary.snapshot.media_id.as_deref(), Some("first"));
    assert!(boundary.effects.is_empty());
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
async fn load_next_catalog_page_reuses_token_and_appends_results() {
    let mut repository = crate::data::repository::MockMediaRepository::new();
    repository
        .expect_search_catalog()
        .withf(|query, page| {
            query == "Rust"
                && page
                    .page_token
                    .as_ref()
                    .map(|token| token.as_str())
                    .is_none()
        })
        .times(1)
        .returning(|_, _| {
            Ok(crate::EnginePagedResult {
                items: vec![MediaItem {
                    id: "page-1".into(),
                    title: "First".into(),
                    ..Default::default()
                }],
                next_page_token: Some(crate::EnginePageToken::new("opaque+/=".into()).unwrap()),
            })
        });
    repository
        .expect_search_catalog()
        .withf(|query, page| {
            query == "Rust"
                && page.page_token.as_ref().map(|token| token.as_str()) == Some("opaque+/=")
        })
        .times(1)
        .returning(|_, _| {
            Ok(crate::EnginePagedResult {
                items: vec![MediaItem {
                    id: "page-2".into(),
                    title: "Second".into(),
                    ..Default::default()
                }],
                next_page_token: None,
            })
        });

    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(repository));
    let first = engine
        .dispatch(
            EngineCommand::search_catalog(
                "Rust".into(),
                crate::EnginePageRequest {
                    page_size: 1,
                    page_token: None,
                },
            ),
            150,
        )
        .await;

    assert_eq!(first.snapshot.search_results[0].id, "page-1");
    assert_eq!(first.event.message.as_deref(), Some("catalog-150-0"));

    let second = engine
        .dispatch(
            EngineCommand::load_next_catalog_page("catalog-150-0".into()),
            200,
        )
        .await;

    assert_eq!(second.snapshot.search_results.len(), 2);
    assert_eq!(second.snapshot.search_results[1].id, "page-2");
}

#[tokio::test]
async fn catalog_operation_ids_are_unique_at_the_same_timestamp() {
    let mut engine = Engine::new(100);

    let first = engine
        .dispatch(EngineCommand::search("first".into()), 150)
        .await;
    let second = engine
        .dispatch(EngineCommand::search("second".into()), 150)
        .await;

    assert_ne!(first.event.message, second.event.message);
}

#[tokio::test]
async fn continuing_older_search_restores_that_operations_accumulated_items() {
    let mut repository = crate::data::repository::MockMediaRepository::new();
    repository.expect_search_catalog().returning(|query, page| {
        let id = match (query, page.page_token.as_ref().map(|token| token.as_str())) {
            ("A", None) => "a-1",
            ("A", Some("a-next")) => "a-2",
            ("B", None) => "b-1",
            _ => unreachable!(),
        };
        Ok(crate::EnginePagedResult {
            items: vec![MediaItem {
                id: id.into(),
                ..Default::default()
            }],
            next_page_token: (id == "a-1")
                .then(|| crate::EnginePageToken::new("a-next".into()).unwrap()),
        })
    });
    let mut engine = Engine::new(0);
    engine.set_repository(Box::new(repository));

    let a = engine.dispatch(EngineCommand::search("A".into()), 1).await;
    engine.dispatch(EngineCommand::search("B".into()), 2).await;
    let continued = engine
        .dispatch(
            EngineCommand::load_next_catalog_page(a.event.message.unwrap()),
            3,
        )
        .await;

    assert_eq!(
        continued
            .snapshot
            .search_results
            .iter()
            .map(|item| item.id.as_str())
            .collect::<Vec<_>>(),
        vec!["a-1", "a-2"]
    );
}

#[tokio::test]
async fn continuing_older_browse_restores_that_operations_accumulated_items() {
    let mut repository = crate::data::repository::MockMediaRepository::new();
    repository
        .expect_browse_catalog()
        .returning(|parent, _, page| {
            let id = match (parent, page.page_token.as_ref().map(|token| token.as_str())) {
                (Some("A"), None) => "a-1",
                (Some("A"), Some("a-next")) => "a-2",
                (Some("B"), None) => "b-1",
                _ => unreachable!(),
            };
            Ok(crate::EnginePagedResult {
                items: vec![MediaItem {
                    id: id.into(),
                    ..Default::default()
                }],
                next_page_token: (id == "a-1")
                    .then(|| crate::EnginePageToken::new("a-next".into()).unwrap()),
            })
        });
    let mut engine = Engine::new(0);
    engine.set_repository(Box::new(repository));

    let a = engine.dispatch(EngineCommand::browse("A".into()), 1).await;
    engine.dispatch(EngineCommand::browse("B".into()), 2).await;
    let continued = engine
        .dispatch(
            EngineCommand::load_next_catalog_page(a.event.message.unwrap()),
            3,
        )
        .await;

    assert_eq!(
        continued
            .snapshot
            .browse_results
            .iter()
            .map(|item| item.id.as_str())
            .collect::<Vec<_>>(),
        vec!["a-1", "a-2"]
    );
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
        source_uri: Some("https://media.test/1".into()),
        ..Default::default()
    }];
    engine.queue().set_items(items);

    let outcome = engine.dispatch(EngineCommand::play(), 200).await;

    // Should emit PreparePlaybackSource, UpdateMetadata, RequestAudioFocus, and Play
    assert!(
        outcome
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "1".to_string(),
                playback_instance_id: 1,
                position_millis: 0,
            })
    );
    assert!(outcome.effects.contains(&EngineEffect::UpdateMetadata {
        media_id: "1".to_string(),
        title: "Song 1".to_string(),
        artist: "Artist 1".to_string(),
    }));
    assert!(outcome.effects.contains(&EngineEffect::RequestAudioFocus));
    assert!(outcome.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn play_media_by_id_resolves_playback_source() {
    let mut engine = Engine::new(100);
    let items = vec![MediaItem {
        id: "track-1".to_string(),
        title: "Resolved Track".to_string(),
        artist: "PandaWave".to_string(),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    let mut audio_source_client = MockAudioSourceClient::new();
    audio_source_client
        .expect_resolve_track()
        .withf(|track_id| track_id == "track-1")
        .times(1)
        .returning(|_| {
            Ok(PlaybackSource {
                source_id: "source-track-1".to_string(),
                uri: "https://cdn.pandawave.test/audio/track-1.mp3".to_string(),
                mime_type: Some("audio/mpeg".to_string()),
                expected_duration_ms: Some(222_000),
            })
        });
    engine.set_audio_source_client(Arc::new(audio_source_client));

    let outcome = engine
        .dispatch(EngineCommand::play_media_by_id("track-1".to_string()), 200)
        .await;

    assert_eq!(
        outcome.snapshot.source_uri.as_deref(),
        Some("https://cdn.pandawave.test/audio/track-1.mp3")
    );
    assert_eq!(outcome.snapshot.mime_type.as_deref(), Some("audio/mpeg"));
    assert_eq!(outcome.snapshot.duration_millis, Some(222_000));
    assert!(
        outcome
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "track-1".to_string(),
                playback_instance_id: 1,
                position_millis: 0,
            })
    );
    assert!(outcome.effects.contains(&EngineEffect::UpdateMetadata {
        media_id: "track-1".to_string(),
        title: "Resolved Track".to_string(),
        artist: "PandaWave".to_string(),
    }));
    assert!(outcome.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn play_media_by_id_reasserts_play_when_replacing_item_while_buffering() {
    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(InMemoryRepository::new(vec![
        MediaItem {
            id: "track-1".to_string(),
            title: "First Track".to_string(),
            artist: "PandaWave".to_string(),
            source_uri: Some("https://media.test/track-1.mp3".to_string()),
            ..Default::default()
        },
        MediaItem {
            id: "track-2".to_string(),
            title: "Second Track".to_string(),
            artist: "PandaWave".to_string(),
            source_uri: Some("https://media.test/track-2.mp3".to_string()),
            ..Default::default()
        },
    ])));

    let first = engine
        .dispatch(EngineCommand::play_media_by_id("track-1".to_string()), 200)
        .await;
    assert_eq!(first.snapshot.playback_state, PlaybackState::Buffering);
    assert!(first.effects.contains(&EngineEffect::RequestAudioFocus));
    assert!(first.effects.contains(&EngineEffect::Play));

    let second = engine
        .dispatch(EngineCommand::play_media_by_id("track-2".to_string()), 300)
        .await;

    assert_eq!(second.snapshot.media_id.as_deref(), Some("track-2"));
    assert_eq!(second.snapshot.playback_state, PlaybackState::Buffering);
    assert_eq!(second.snapshot.position_millis, 0);
    assert!(
        second
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "track-2".to_string(),
                playback_instance_id: 2,
                position_millis: 0,
            })
    );
    assert!(second.effects.contains(&EngineEffect::UpdateMetadata {
        media_id: "track-2".to_string(),
        title: "Second Track".to_string(),
        artist: "PandaWave".to_string(),
    }));
    assert!(second.effects.contains(&EngineEffect::RequestAudioFocus));
    assert!(second.effects.contains(&EngineEffect::Play));
}

#[tokio::test]
async fn play_media_by_id_resets_position_when_replacing_a_playing_item() {
    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(InMemoryRepository::new(vec![
        MediaItem {
            id: "track-1".into(),
            title: "First Track".into(),
            source_uri: Some("https://media.test/track-1.mp3".into()),
            duration_millis: Some(180_000),
            ..Default::default()
        },
        MediaItem {
            id: "track-2".into(),
            title: "Second Track".into(),
            source_uri: Some("https://media.test/track-2.mp3".into()),
            duration_millis: Some(90_000),
            ..Default::default()
        },
    ])));

    engine
        .dispatch(EngineCommand::play_media_by_id("track-1".into()), 200)
        .await;
    engine.snapshot.position_millis = 55_000;

    let switched = engine
        .dispatch(EngineCommand::play_media_by_id("track-2".into()), 300)
        .await;

    assert_eq!(switched.snapshot.media_id.as_deref(), Some("track-2"));
    assert_eq!(switched.snapshot.position_millis, 0);
    assert!(
        switched
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "track-2".into(),
                playback_instance_id: 2,
                position_millis: 0,
            })
    );
}

#[tokio::test]
async fn play_media_by_id_projects_canonical_playback_capability_verbatim() {
    let mut engine = Engine::new(100);
    engine.set_repository(Box::new(InMemoryRepository::new(vec![MediaItem {
        id: "track-1".into(),
        title: "Canonical Track".into(),
        artist: "PandaWave".into(),
        ..Default::default()
    }])));

    let mut playback_port = MockPlaybackPort::new();
    playback_port
        .expect_resolve_playback()
        .withf(|track_id| track_id == "track-1")
        .times(1)
        .returning(|_| {
            Ok(crate::EnginePlaybackSource {
                track_id: "track-1".into(),
                url: "http://10.0.2.2:8080/s/opaque?token=a%2Fb".into(),
                content_type: "audio/flac".into(),
                codec: "flac".into(),
                duration_millis: 42_000,
                expires_at_epoch_millis: 1_750_000_000_250,
            })
        });
    engine.set_playback_port(Arc::new(playback_port));

    let outcome = engine
        .dispatch(EngineCommand::play_media_by_id("track-1".into()), 200)
        .await;

    assert_eq!(
        outcome.snapshot.source_uri.as_deref(),
        Some("http://10.0.2.2:8080/s/opaque?token=a%2Fb")
    );
    assert_eq!(outcome.snapshot.mime_type.as_deref(), Some("audio/flac"));
    assert_eq!(outcome.snapshot.duration_millis, Some(42_000));
    assert_eq!(
        outcome.snapshot.playback_expires_at_epoch_millis,
        Some(1_750_000_000_250)
    );
    assert!(
        outcome
            .effects
            .contains(&EngineEffect::PreparePlaybackSource {
                media_id: "track-1".into(),
                playback_instance_id: 1,
                position_millis: 0,
            })
    );
}

#[tokio::test]
async fn play_media_by_id_source_resolution_failure_moves_to_error() {
    let mut engine = Engine::new(100);
    let items = vec![MediaItem {
        id: "track-1".to_string(),
        title: "Broken Track".to_string(),
        artist: "PandaWave".to_string(),
        ..Default::default()
    }];
    engine.set_repository(Box::new(InMemoryRepository::new(items)));

    let mut audio_source_client = MockAudioSourceClient::new();
    audio_source_client
        .expect_resolve_track()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("canopy unavailable")));
    engine.set_audio_source_client(Arc::new(audio_source_client));

    let outcome = engine
        .dispatch(EngineCommand::play_media_by_id("track-1".to_string()), 200)
        .await;

    assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
    assert_eq!(outcome.snapshot.media_id.as_deref(), Some("track-1"));
    assert_eq!(outcome.snapshot.title.as_deref(), Some("Broken Track"));
    assert_eq!(outcome.snapshot.artist.as_deref(), Some("PandaWave"));
    assert_eq!(outcome.snapshot.source_uri, None);
    assert_eq!(
        outcome
            .snapshot
            .last_error
            .as_ref()
            .map(|error| &error.error_type),
        Some(&crate::model::error::EngineErrorType::NetworkError)
    );
    assert!(outcome.effects.is_empty());
}

#[tokio::test]
async fn play_media_by_id_without_playback_composition_fails_closed() {
    for source_uri in [None, Some("   ".to_string())] {
        let mut engine = Engine::new(100);
        engine.set_repository(Box::new(InMemoryRepository::new(vec![MediaItem {
            id: "track-1".into(),
            title: "Unresolved Track".into(),
            artist: "PandaWave".into(),
            source_uri,
            ..Default::default()
        }])));

        let outcome = engine
            .dispatch(EngineCommand::play_media_by_id("track-1".into()), 200)
            .await;

        assert_eq!(PlaybackState::Error, outcome.snapshot.playback_state);
        assert_eq!(
            outcome
                .snapshot
                .last_error
                .as_ref()
                .map(|error| &error.error_type),
            Some(&crate::EngineErrorType::ServiceUnavailable)
        );
        assert_eq!(outcome.snapshot.source_uri, None);
        assert!(
            !outcome
                .effects
                .iter()
                .any(|effect| matches!(effect, EngineEffect::PreparePlaybackSource { .. }))
        );
    }
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

    // 1 second later. Progress is owned by the platform player, so ticks must
    // not invent Seek commands or snapshot position.
    let outcomes = engine.tick(1100).await;

    assert!(outcomes.is_empty());
    assert_eq!(engine.snapshot().position_millis, 5000);
}

#[tokio::test]
async fn tick_progress_is_not_reset_by_unrelated_command() {
    let mut engine = Engine::new(100);
    engine.snapshot = engine
        .snapshot
        .clone()
        .with_playback_state(PlaybackState::Playing, 100)
        .with_progress_tick(100)
        .with_position(5000)
        .with_speed(1.0);

    // Unrelated command updates snapshot timestamp, but should not rebase playback progress clock.
    let _ = engine
        .dispatch(EngineCommand::start_session("user-1".to_string()), 900)
        .await;

    let outcomes = engine.tick(1100).await;

    assert!(outcomes.is_empty());
    assert_eq!(engine.snapshot().position_millis, 5000);
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
            album: Some("Recovery Album".to_string()),
            duration_millis: Some(222_000),
            thumbnail_url: Some("https://example.com/recovery.jpg".to_string()),
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
    assert_eq!(outcome.snapshot.album.as_deref(), Some("Recovery Album"));
    assert_eq!(outcome.snapshot.duration_millis, Some(222_000));
    assert_eq!(
        outcome.snapshot.thumbnail_url.as_deref(),
        Some("https://example.com/recovery.jpg")
    );
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
        source_uri: Some("https://media.test/track_1".into()),
        ..Default::default()
    }];
    engine.queue().set_items(items);

    // Play command should trigger source preparation and Play effects
    let play_outcome = engine.dispatch(EngineCommand::play(), 120).await;
    assert!(play_outcome.effects.contains(&EngineEffect::Play));

    // MediaLoaded Buffering->Playing must not re-issue Play; the Buffering
    // transition already set playWhenReady.
    let outcome = engine
        .dispatch_platform_event(
            EnginePlatformEvent::new(EnginePlatformEventType::MediaLoaded, None),
            130,
        )
        .await;

    assert_eq!(outcome.snapshot.playback_state, PlaybackState::Playing);
    assert!(!outcome.effects.contains(&EngineEffect::Play));
    assert_eq!(outcome.snapshot.last_progress_tick_epoch_millis, 130);
}
