use crate::{
    FFI_COMMAND_LIKE_TRACK, FFI_COMMAND_LIST_LIKED_TRACKS, FFI_COMMAND_LIST_SAVED_TRACKS,
    FFI_COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE, FFI_COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE,
    FFI_COMMAND_REMOVE_SAVED_TRACK, FFI_COMMAND_SAVE_TRACK, FFI_COMMAND_UNLIKE_TRACK,
    FfiEngineSnapshot, panda_engine_create, panda_engine_destroy,
    panda_engine_get_pending_library_track_id,
};
use panda_engine_core::{EngineLibraryRelationshipKind, EngineLibraryTrack, EngineSnapshot};

#[test]
fn ffi_snapshot_appends_library_counts_pending_and_pagination_flags() {
    let snapshot = EngineSnapshot {
        saved_tracks: vec![
            EngineLibraryTrack::new(
                EngineLibraryRelationshipKind::Saved,
                "track-1",
                "Title",
                "artist-1",
                "Artist",
                42,
            )
            .unwrap(),
        ],
        liked_tracks: vec![
            EngineLibraryTrack::new(
                EngineLibraryRelationshipKind::Liked,
                "track-2",
                "Liked",
                "artist-1",
                "Artist",
                43,
            )
            .unwrap(),
        ],
        saved_tracks_next_page_token: Some(
            panda_engine_core::EnginePageToken::new("saved+/=".into()).unwrap(),
        ),
        library_pending_track_ids: vec!["track-1".into()],
        ..EngineSnapshot::default()
    };

    let ffi = FfiEngineSnapshot::from(&snapshot);
    assert_eq!(ffi.saved_tracks_count, 1);
    assert_eq!(ffi.liked_tracks_count, 1);
    assert_eq!(ffi.library_pending_count, 1);
    assert!(ffi.has_saved_tracks_next_page);
    assert!(!ffi.has_liked_tracks_next_page);
}

#[test]
fn ffi_pending_library_identity_is_null_for_invalid_or_out_of_range_handles() {
    assert!(unsafe { panda_engine_get_pending_library_track_id(std::ptr::null(), 0) }.is_null());
    let engine = panda_engine_create(1_000);
    assert!(unsafe { panda_engine_get_pending_library_track_id(engine, 0) }.is_null());
    unsafe { panda_engine_destroy(engine) };
}

#[test]
fn ffi_library_discriminants_are_append_only() {
    assert_eq!(FFI_COMMAND_SAVE_TRACK, 32);
    assert_eq!(FFI_COMMAND_REMOVE_SAVED_TRACK, 33);
    assert_eq!(FFI_COMMAND_LIST_SAVED_TRACKS, 34);
    assert_eq!(FFI_COMMAND_LOAD_NEXT_SAVED_TRACKS_PAGE, 35);
    assert_eq!(FFI_COMMAND_LIKE_TRACK, 36);
    assert_eq!(FFI_COMMAND_UNLIKE_TRACK, 37);
    assert_eq!(FFI_COMMAND_LIST_LIKED_TRACKS, 38);
    assert_eq!(FFI_COMMAND_LOAD_NEXT_LIKED_TRACKS_PAGE, 39);
}
