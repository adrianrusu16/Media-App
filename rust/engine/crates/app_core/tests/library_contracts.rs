use panda_engine_core::{
    EngineCommand, EngineCommandType, EngineLibraryIdentity, EngineLibraryRelationshipKind,
    EngineLibraryTrack,
};

#[test]
fn relationship_uses_canonical_track_id_and_keeps_renderable_track_and_timestamp() {
    let saved = EngineLibraryTrack::new(
        EngineLibraryRelationshipKind::Saved,
        "track-1",
        "Track title",
        "artist-1",
        "Artist name",
        42,
    )
    .unwrap();

    // Canopy v1 SavedTrack/LikedTrack has no independent relationship-id field.
    // The canonical stable relationship key is therefore the required track id.
    assert_eq!(saved.relationship_id, "track-1");
    assert_eq!(saved.track.id, "track-1");
    assert_eq!(saved.relationship_at_epoch_millis, 42);
}

#[test]
fn protected_library_identity_rejects_incomplete_owner_binding() {
    assert!(EngineLibraryIdentity::new("", "session-1").is_err());
    assert!(EngineLibraryIdentity::new("account-1", "").is_err());
    assert_eq!(
        EngineLibraryIdentity::new("account-1", "session-1").unwrap(),
        EngineLibraryIdentity {
            account_id: "account-1".into(),
            session_id: "session-1".into(),
        }
    );
}

#[test]
fn library_wire_commands_reject_unknown_fields_tokens_and_unexpected_payloads() {
    let extra = r#"{"version":1,"track_id":"track-1","extra":true}"#;
    assert!(matches!(
        EngineCommand::from_wire(EngineCommandType::SAVE_TRACK_WIRE, Some(extra.into()))
            .command_type,
        EngineCommandType::Unknown(_)
    ));
    let token = r#"{"version":1,"page":{"page_size":25,"page_token":"opaque"}}"#;
    assert!(matches!(
        EngineCommand::from_wire(
            EngineCommandType::LIST_SAVED_TRACKS_WIRE,
            Some(token.into())
        )
        .command_type,
        EngineCommandType::Unknown(_)
    ));
    assert!(matches!(
        EngineCommand::from_wire(
            EngineCommandType::LOAD_NEXT_SAVED_TRACKS_PAGE_WIRE,
            Some("{}".into()),
        )
        .command_type,
        EngineCommandType::Unknown(_)
    ));
}
