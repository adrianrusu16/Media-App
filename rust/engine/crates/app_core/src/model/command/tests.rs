use super::*;

#[test]
fn from_wire_unknown_maps_to_unknown_variant() {
    let command_type = EngineCommandType::from_wire("totally_unknown_command");
    assert_eq!(
        command_type,
        EngineCommandType::Unknown("totally_unknown_command".to_string())
    );
    assert_eq!(command_type.as_wire(), "totally_unknown_command");
}

#[test]
fn create_playlist_rejects_an_expected_revision() {
    let command = EngineCommand::from_wire(
            EngineCommandType::CREATE_PLAYLIST_WIRE,
            Some(
                r#"{"version":1,"playlist_id":null,"name":"Mix","description":null,"expected_revision":7}"#
                    .into(),
            ),
        );

    assert!(matches!(
        command.command_type,
        EngineCommandType::Unknown(_)
    ));
}

#[test]
fn from_wire_known_commands_use_safe_defaults() {
    assert_eq!(
        EngineCommandType::from_wire(EngineCommandType::START_SESSION_WIRE),
        EngineCommandType::StartSession {
            user_id: "unknown".to_string()
        }
    );
    assert_eq!(
        EngineCommandType::from_wire(EngineCommandType::SEARCH_WIRE),
        EngineCommandType::SearchCatalog {
            query: String::new(),
            page: EnginePageRequest::default(),
        }
    );
    assert_eq!(
        EngineCommandType::from_wire(EngineCommandType::BROWSE_WIRE),
        EngineCommandType::BrowseCatalog {
            parent_id: None,
            genres: Vec::new(),
            page: EnginePageRequest::default(),
        }
    );
}

#[test]
fn engine_command_from_wire_preserves_payload() {
    let command = EngineCommand::from_wire("invalid_wire", Some("payload".to_string()));
    assert_eq!(
        command.command_type,
        EngineCommandType::Unknown("invalid_wire".to_string())
    );
    assert_eq!(command.payload.as_deref(), Some("payload"));
}

#[test]
fn play_queue_decodes_a_valid_ordered_snapshot() {
    let command = EngineCommand::from_wire(
        EngineCommandType::PLAY_QUEUE_WIRE,
        Some(r#"{"version":1,"media_ids":["a","b"],"start_index":1}"#.into()),
    );

    assert_eq!(
        command.command_type,
        EngineCommandType::PlayQueue {
            media_ids: vec!["a".into(), "b".into()],
            start_index: 1,
        }
    );
}

#[test]
fn search_catalog_decodes_versioned_json_page_payload() {
    let payload = r#"{"version":1,"query":"jazz","page":{"page_size":25}}"#;

    let command = EngineCommand::from_wire("search", Some(payload.into()));

    assert_eq!(
        command.command_type,
        EngineCommandType::SearchCatalog {
            query: "jazz".into(),
            page: crate::EnginePageRequest {
                page_size: 25,
                page_token: None,
            },
        }
    );
}

#[test]
fn initial_catalog_payload_rejects_page_token() {
    let payload =
        r#"{"version":1,"query":"jazz","page":{"page_size":25,"page_token":"opaque+/="}}"#;
    let command = EngineCommand::from_wire("search", Some(payload.into()));

    assert_eq!(
        command.command_type,
        EngineCommandType::Unknown("invalid_search_payload".into())
    );

    let browse_payload =
        r#"{"version":1,"parent_id":"root","genres":[],"page":{"page_size":25,"page_token":null}}"#;
    let browse = EngineCommand::from_wire("browse", Some(browse_payload.into()));
    assert_eq!(
        browse.command_type,
        EngineCommandType::Unknown("invalid_browse_payload".into())
    );
}

#[test]
fn browse_catalog_decodes_versioned_json_filters() {
    let payload =
        r#"{"version":1,"parent_id":null,"genres":["jazz","fusion"],"page":{"page_size":10}}"#;

    let command = EngineCommand::from_wire("browse", Some(payload.into()));

    assert_eq!(
        command.command_type,
        EngineCommandType::BrowseCatalog {
            parent_id: None,
            genres: vec!["jazz".into(), "fusion".into()],
            page: crate::EnginePageRequest {
                page_size: 10,
                page_token: None,
            },
        }
    );
}

#[test]
fn next_catalog_page_decodes_operation_id_from_json() {
    let payload = r#"{"version":1,"operation_id":"catalog-42"}"#;

    let command = EngineCommand::from_wire("load_next_catalog_page", Some(payload.into()));

    assert_eq!(
        command.command_type,
        EngineCommandType::LoadNextCatalogPage {
            operation_id: "catalog-42".into(),
        }
    );
}
#[test]
fn discovery_commands_decode_versioned_engine_owned_pagination_payloads() {
    let load = EngineCommand::from_wire(
        "load_discovery_feed",
        Some(r#"{"version":1,"exclude_track_ids":["played-1"],"page":{"page_size":25}}"#.into()),
    );
    assert_eq!(
        load.command_type,
        EngineCommandType::LoadDiscoveryFeed {
            excluded_track_ids: vec!["played-1".into()],
            page: EnginePageRequest {
                page_size: 25,
                page_token: None,
            },
        }
    );

    let next =
        EngineCommand::from_wire("load_next_discovery_page", Some(r#"{"version":1}"#.into()));
    assert_eq!(next.command_type, EngineCommandType::LoadNextDiscoveryPage);

    for (wire, expected) in [
        (
            EngineCommandType::LOAD_FOR_YOU_FEED_WIRE,
            EngineCommandType::LoadForYouFeed {
                excluded_track_ids: vec!["played-1".into()],
                page: EnginePageRequest {
                    page_size: 25,
                    page_token: None,
                },
            },
        ),
        (
            EngineCommandType::LOAD_RECOMMENDATIONS_WIRE,
            EngineCommandType::LoadRecommendations {
                excluded_track_ids: vec!["played-1".into()],
                page: EnginePageRequest {
                    page_size: 25,
                    page_token: None,
                },
            },
        ),
    ] {
        let command = EngineCommand::from_wire(
            wire,
            Some(
                r#"{"version":1,"exclude_track_ids":["played-1"],"page":{"page_size":25}}"#.into(),
            ),
        );
        assert_eq!(command.command_type, expected);
    }
}

#[test]
fn initial_discovery_payload_rejects_external_page_token() {
    let payload =
        r#"{"version":1,"exclude_track_ids":[],"page":{"page_size":25,"page_token":"opaque"}}"#;
    let command = EngineCommand::from_wire("load_discovery_feed", Some(payload.into()));

    assert_eq!(
        command.command_type,
        EngineCommandType::Unknown("invalid_load_discovery_feed_payload".into())
    );
}
