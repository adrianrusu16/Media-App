use crate::EnginePageRequest;

use super::payloads::{
    BrowseCatalogPayload, DeleteHistoryEntryPayload, DiscoveryFeedPayload,
    ListDeviceSessionsPayload, ListHistoryPayload, ListLibraryPayload, LoadNextCatalogPagePayload,
    LoadNextDiscoveryPagePayload, PlayQueuePayload, PlaylistDetailsPayload, PlaylistPagePayload,
    PlaylistPayload, PlaylistReorderPayload, PlaylistTrackPayload, RevokeDeviceSessionPayload,
    SearchCatalogPayload, TrackRelationshipPayload, UpdateHistorySettingsPayload,
};
use super::{CATALOG_PAYLOAD_VERSION, EngineCommandType};

pub(super) fn parse_search_catalog_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: SearchCatalogPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.query.trim().is_empty()).then_some(
        EngineCommandType::SearchCatalog {
            query: payload.query,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}

pub(super) fn parse_browse_catalog_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: BrowseCatalogPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::BrowseCatalog {
        parent_id: payload.parent_id,
        genres: payload.genres,
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}

pub(super) fn parse_load_next_catalog_page_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: LoadNextCatalogPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.operation_id.trim().is_empty())
        .then_some(EngineCommandType::LoadNextCatalogPage {
            operation_id: payload.operation_id,
        })
}

pub(super) fn parse_discovery_feed_payload(
    feed: crate::DiscoveryFeed,
    payload: &str,
) -> Option<EngineCommandType> {
    let payload: DiscoveryFeedPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(match feed {
        crate::DiscoveryFeed::Discovery => EngineCommandType::LoadDiscoveryFeed {
            excluded_track_ids: payload.exclude_track_ids,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
        crate::DiscoveryFeed::ForYou => EngineCommandType::LoadForYouFeed {
            excluded_track_ids: payload.exclude_track_ids,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
        crate::DiscoveryFeed::Recommendations => EngineCommandType::LoadRecommendations {
            excluded_track_ids: payload.exclude_track_ids,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    })
}

pub(super) fn parse_load_next_discovery_page_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: LoadNextDiscoveryPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::LoadNextDiscoveryPage)
}

pub(super) fn parse_update_history_settings_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: UpdateHistorySettingsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(
        EngineCommandType::UpdateHistorySettings {
            enabled: payload.enabled,
        },
    )
}

pub(super) fn parse_list_history_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: ListHistoryPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::ListHistory {
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}
pub(super) fn parse_delete_history_entry_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: DeleteHistoryEntryPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.history_id.trim().is_empty()).then_some(
        EngineCommandType::DeleteHistoryEntry {
            history_id: payload.history_id,
        },
    )
}

pub(super) fn parse_track_relationship_payload(
    command_type: &str,
    payload: &str,
) -> Option<EngineCommandType> {
    let payload: TrackRelationshipPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION || payload.track_id.trim().is_empty() {
        return None;
    }
    match command_type {
        EngineCommandType::SAVE_TRACK_WIRE => Some(EngineCommandType::SaveTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::REMOVE_SAVED_TRACK_WIRE => Some(EngineCommandType::RemoveSavedTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::LIKE_TRACK_WIRE => Some(EngineCommandType::LikeTrack {
            track_id: payload.track_id,
        }),
        EngineCommandType::UNLIKE_TRACK_WIRE => Some(EngineCommandType::UnlikeTrack {
            track_id: payload.track_id,
        }),
        _ => None,
    }
}

pub(super) fn parse_list_library_payload(
    command_type: &str,
    payload: &str,
) -> Option<EngineCommandType> {
    let payload: ListLibraryPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION {
        return None;
    }
    let page = EnginePageRequest {
        page_size: payload.page.page_size,
        page_token: None,
    };
    match command_type {
        EngineCommandType::LIST_SAVED_TRACKS_WIRE => {
            Some(EngineCommandType::ListSavedTracks { page })
        }
        EngineCommandType::LIST_LIKED_TRACKS_WIRE => {
            Some(EngineCommandType::ListLikedTracks { page })
        }
        _ => None,
    }
}

pub(super) fn parse_create_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistDetailsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && payload.playlist_id.is_none()
        && payload.expected_revision.is_none()
        && !payload.name.trim().is_empty())
    .then_some(EngineCommandType::CreatePlaylist {
        input: crate::EngineCreatePlaylist {
            name: payload.name,
            description: payload.description,
        },
    })
}
pub(super) fn parse_update_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistDetailsPayload = serde_json::from_str(payload).ok()?;
    let id = payload.playlist_id?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && !id.trim().is_empty()
        && !payload.name.trim().is_empty())
    .then_some(EngineCommandType::UpdatePlaylist {
        input: crate::EngineUpdatePlaylist {
            id,
            name: payload.name,
            description: payload.description,
            expected_revision: payload.expected_revision?,
        },
    })
}
pub(super) fn parse_delete_playlist_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.playlist_id.trim().is_empty())
        .then_some(EngineCommandType::DeletePlaylist {
            playlist_id: payload.playlist_id,
        })
}
pub(super) fn parse_list_playlists_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPagePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && payload.playlist_id.is_none()).then_some(
        EngineCommandType::ListPlaylists {
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}
pub(super) fn parse_list_playlist_tracks_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistPagePayload = serde_json::from_str(payload).ok()?;
    let playlist_id = payload.playlist_id?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !playlist_id.trim().is_empty()).then_some(
        EngineCommandType::ListPlaylistTracks {
            playlist_id,
            page: EnginePageRequest {
                page_size: payload.page.page_size,
                page_token: None,
            },
        },
    )
}
pub(super) fn parse_playlist_track_payload(
    command_type: &str,
    payload: &str,
) -> Option<EngineCommandType> {
    let payload: PlaylistTrackPayload = serde_json::from_str(payload).ok()?;
    if payload.version != CATALOG_PAYLOAD_VERSION
        || payload.playlist_id.trim().is_empty()
        || payload.track_id.trim().is_empty()
    {
        return None;
    }
    match command_type {
        EngineCommandType::ADD_PLAYLIST_TRACK_WIRE => Some(EngineCommandType::AddPlaylistTrack {
            playlist_id: payload.playlist_id,
            track_id: payload.track_id,
        }),
        EngineCommandType::REMOVE_PLAYLIST_TRACK_WIRE => {
            Some(EngineCommandType::RemovePlaylistTrack {
                playlist_id: payload.playlist_id,
                track_id: payload.track_id,
            })
        }
        _ => None,
    }
}
pub(super) fn parse_reorder_playlist_tracks_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlaylistReorderPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && !payload.playlist_id.trim().is_empty()
        && !payload.ordered_membership_ids.is_empty()
        && payload
            .ordered_membership_ids
            .iter()
            .all(|id| !id.trim().is_empty()))
    .then_some(EngineCommandType::ReorderPlaylistTracks {
        playlist_id: payload.playlist_id,
        ordered_membership_ids: payload.ordered_membership_ids,
        expected_revision: payload.expected_revision,
    })
}

pub(super) fn parse_list_device_sessions_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: ListDeviceSessionsPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION).then_some(EngineCommandType::ListDeviceSessions {
        page: EnginePageRequest {
            page_size: payload.page.page_size,
            page_token: None,
        },
    })
}

pub(super) fn parse_revoke_device_session_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: RevokeDeviceSessionPayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION && !payload.session_id.trim().is_empty()).then_some(
        EngineCommandType::RevokeDeviceSession {
            session_id: payload.session_id,
        },
    )
}

pub(super) fn parse_play_queue_payload(payload: &str) -> Option<EngineCommandType> {
    let payload: PlayQueuePayload = serde_json::from_str(payload).ok()?;
    (payload.version == CATALOG_PAYLOAD_VERSION
        && !payload.media_ids.is_empty()
        && payload.media_ids.iter().all(|id| !id.trim().is_empty())
        && payload.start_index < payload.media_ids.len())
    .then_some(EngineCommandType::PlayQueue {
        media_ids: payload.media_ids,
        start_index: payload.start_index,
    })
}
