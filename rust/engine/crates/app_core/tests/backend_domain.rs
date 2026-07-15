use panda_engine_core::{
    EngineAlbum, EngineArtist, EngineBackendStatus, EngineDependencyStatus, EngineError,
    EngineErrorType, EnginePageToken, EnginePlaybackSource, EngineStatusValue, EngineTrack,
    RetryClass,
};

#[test]
fn page_token_is_opaque_and_round_trips() {
    let token = EnginePageToken::new("opaque+/=value".into()).unwrap();

    assert_eq!(token.as_str(), "opaque+/=value");
}

#[test]
fn empty_page_token_is_typed_invalid_input() {
    let error = EnginePageToken::new(String::new()).unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
}

#[test]
fn unknown_backend_status_is_preserved() {
    let value = EngineStatusValue::from_wire("future-status");

    assert_eq!(value.as_wire(), "future-status");
}

#[test]
fn unsafe_transport_error_is_typed_and_displayable() {
    let error = EngineError::unsafe_transport();

    assert_eq!(error.error_type, EngineErrorType::UnsafeTransport);
    assert_eq!(error.to_string(), "unsafe backend transport configuration");
}

#[test]
fn rate_limit_preserves_retry_hint() {
    let error = EngineError::rate_limited(Some(1_250));

    assert_eq!(error.error_type, EngineErrorType::RateLimited);
    assert_eq!(error.retry_after_millis, Some(1_250));
}

#[test]
fn retry_classes_distinguish_replay_safety() {
    assert_ne!(RetryClass::Read, RetryClass::NonReplayableMutation);
    assert_ne!(RetryClass::Refresh, RetryClass::IdempotentMutation);
}

#[test]
fn catalog_and_status_models_are_service_neutral() {
    let track = EngineTrack {
        id: "track-1".into(),
        title: "Panda".into(),
        artist: EngineArtist {
            id: "artist-1".into(),
            name: "Wave".into(),
        },
        album: Some(EngineAlbum {
            id: "album-1".into(),
            title: "Canopy".into(),
        }),
        duration_millis: 42_000,
        explicit: false,
        artwork_id: Some("artwork-1".into()),
        genres: vec!["electronic".into()],
    };
    let source = EnginePlaybackSource {
        track_id: track.id.clone(),
        url: "https://stream.example/opaque?token=a%2Fb".into(),
        content_type: "audio/flac".into(),
        codec: "flac".into(),
        duration_millis: track.duration_millis,
        expires_at_epoch_millis: 123_456,
    };
    let status = EngineBackendStatus {
        healthy: true,
        version: "0.2.0".into(),
        status: EngineStatusValue::from_wire("ready"),
        dependencies: vec![EngineDependencyStatus {
            name: "catalog".into(),
            status: EngineStatusValue::from_wire("available"),
            message: String::new(),
        }],
        checked_at_epoch_millis: Some(100),
    };

    assert_eq!(source.url, "https://stream.example/opaque?token=a%2Fb");
    assert_eq!(status.dependencies[0].status.as_wire(), "available");
}
