use std::sync::Arc;

use tonic_014::Request;

use crate::{
    EngineError, EngineErrorType, EngineHistoryIdentity, EngineLibraryIdentity, EngineLibraryTrack,
    EnginePageRequest, EnginePageToken, EnginePagedResult, LibraryPort,
};

use super::catalog::{map_page_request, map_track_summary};
use super::operation::CanopyOperation;
use super::request::execute_with_bound_auth;
use super::sdk::clients::library_service_client::LibraryServiceClient;
use super::sdk::resources::{
    LikeTrackRequest, LikedTrack, ListLikedTracksRequest, ListLikedTracksResponse,
    ListSavedTracksRequest, ListSavedTracksResponse, PageInfo, RemoveSavedTrackRequest,
    SaveTrackRequest, SavedTrack, UnlikeTrackRequest,
};
use super::{CanopyChannel, SessionCoordinator};

#[derive(Clone)]
pub struct CanopyLibraryClient {
    client: LibraryServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
}

impl CanopyLibraryClient {
    pub fn new(channel: &CanopyChannel, session: Arc<SessionCoordinator>) -> Self {
        Self {
            client: LibraryServiceClient::new(channel.clone_inner()),
            session,
        }
    }
}

#[async_trait::async_trait]
impl LibraryPort for CanopyLibraryClient {
    async fn save(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError> {
        require_track_id(track_id)?;
        let request = SaveTrackRequest {
            track_id: track_id.into(),
        };
        let client = self.client.clone();
        let response = execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::SaveTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.save_track(request).await }
            },
        )
        .await?
        .into_inner();
        map_saved_track(response)
    }

    async fn remove_saved(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<(), EngineError> {
        require_track_id(track_id)?;
        let request = RemoveSavedTrackRequest {
            track_id: track_id.into(),
        };
        let client = self.client.clone();
        execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::RemoveSavedTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.remove_saved_track(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn list_saved(
        &self,
        identity: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
        let request = ListSavedTracksRequest {
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ListSavedTracks,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.list_saved_tracks(request).await }
            },
        )
        .await?
        .into_inner();
        map_saved_response(response)
    }

    async fn like(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError> {
        require_track_id(track_id)?;
        let request = LikeTrackRequest {
            track_id: track_id.into(),
        };
        let client = self.client.clone();
        let response = execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::LikeTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.like_track(request).await }
            },
        )
        .await?
        .into_inner();
        map_liked_track(response)
    }

    async fn unlike(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<(), EngineError> {
        require_track_id(track_id)?;
        let request = UnlikeTrackRequest {
            track_id: track_id.into(),
        };
        let client = self.client.clone();
        execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::UnlikeTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.unlike_track(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn list_liked(
        &self,
        identity: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
        let request = ListLikedTracksRequest {
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute_library_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ListLikedTracks,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.list_liked_tracks(request).await }
            },
        )
        .await?
        .into_inner();
        map_liked_response(response)
    }
}

async fn execute_library_request<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    session: &SessionCoordinator,
    identity: &EngineLibraryIdentity,
    operation: CanopyOperation,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<tonic_014::Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture:
        std::future::Future<Output = Result<tonic_014::Response<TResponse>, tonic_014::Status>>,
{
    let bound = EngineHistoryIdentity {
        account_id: identity.account_id.clone(),
        session_id: identity.session_id.clone(),
    };
    execute_with_bound_auth(session, &bound, operation, make_request, execute).await
}

fn map_saved_response(
    response: ListSavedTracksResponse,
) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
    map_saved_page(response.tracks, response.page_info)
}

fn map_saved_page(
    tracks: Vec<SavedTrack>,
    page_info: Option<PageInfo>,
) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
    map_relationship_page(tracks.into_iter().map(map_saved_track), page_info)
}

fn map_liked_response(
    response: ListLikedTracksResponse,
) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
    map_relationship_page(
        response.tracks.into_iter().map(map_liked_track),
        response.page_info,
    )
}

fn map_relationship_page<I>(
    items: I,
    page_info: Option<PageInfo>,
) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError>
where
    I: Iterator<Item = Result<EngineLibraryTrack, EngineError>>,
{
    Ok(EnginePagedResult {
        items: items.collect::<Result<_, _>>()?,
        next_page_token: match page_info.map(|info| info.next_page_token) {
            Some(token) if !token.is_empty() => Some(
                EnginePageToken::new(token)
                    .map_err(|_| mapping_defect("library returned an invalid page token"))?,
            ),
            _ => None,
        },
    })
}

fn map_saved_track(value: SavedTrack) -> Result<EngineLibraryTrack, EngineError> {
    map_relationship(value.track, value.saved_at)
}

fn map_liked_track(value: LikedTrack) -> Result<EngineLibraryTrack, EngineError> {
    map_relationship(value.track, value.liked_at)
}

fn map_relationship(
    track: Option<super::sdk::resources::TrackSummary>,
    timestamp: Option<prost_types_014::Timestamp>,
) -> Result<EngineLibraryTrack, EngineError> {
    let track = track.ok_or_else(|| mapping_defect("library relationship missing track"))?;
    let timestamp =
        timestamp.ok_or_else(|| mapping_defect("library relationship missing timestamp"))?;
    let relationship_id = track.id.clone();
    if relationship_id.trim().is_empty() {
        return Err(mapping_defect("library relationship missing track id"));
    }
    Ok(EngineLibraryTrack {
        relationship_id,
        track: map_track_summary(track, Vec::new())?,
        relationship_at_epoch_millis: timestamp_to_epoch_millis(timestamp)?,
    })
}

fn timestamp_to_epoch_millis(timestamp: prost_types_014::Timestamp) -> Result<u64, EngineError> {
    if !(0..=253_402_300_799).contains(&timestamp.seconds)
        || !(0..1_000_000_000).contains(&timestamp.nanos)
    {
        return Err(mapping_defect("library relationship has invalid timestamp"));
    }
    u64::try_from(timestamp.seconds)
        .ok()
        .and_then(|seconds| seconds.checked_mul(1_000))
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(|| mapping_defect("library relationship timestamp overflowed"))
}

fn require_track_id(track_id: &str) -> Result<(), EngineError> {
    if track_id.trim().is_empty() {
        Err(EngineError::new(
            EngineErrorType::InvalidInput,
            "library track id is required",
            false,
        ))
    } else {
        Ok(())
    }
}

fn mapping_defect(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::MappingDefect, message, false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::EngineErrorType;
    use crate::networking::canopy::sdk::resources::{
        ArtistSummary, LikedTrack, PageInfo, SavedTrack, TrackSummary,
    };

    fn track(id: &str) -> TrackSummary {
        TrackSummary {
            id: id.into(),
            title: "Track title".into(),
            artist: Some(ArtistSummary {
                id: "artist-1".into(),
                name: "Artist name".into(),
            }),
            duration_ms: 123,
            ..TrackSummary::default()
        }
    }

    #[test]
    fn maps_saved_and_liked_relationships_with_checked_timestamps() {
        let saved = map_saved_track(SavedTrack {
            track: Some(track("track-1")),
            saved_at: Some(prost_types_014::Timestamp {
                seconds: 2,
                nanos: 3_000_000,
            }),
        })
        .unwrap();
        assert_eq!(saved.relationship_id, "track-1");
        assert_eq!(saved.track.title, "Track title");
        assert_eq!(saved.relationship_at_epoch_millis, 2_003);

        let liked = map_liked_track(LikedTrack {
            track: Some(track("track-2")),
            liked_at: Some(prost_types_014::Timestamp {
                seconds: 4,
                nanos: 5_000_000,
            }),
        })
        .unwrap();
        assert_eq!(liked.relationship_id, "track-2");
        assert_eq!(liked.relationship_at_epoch_millis, 4_005);
    }

    #[test]
    fn rejects_missing_relationship_fields_and_invalid_timestamp() {
        let missing_track = SavedTrack {
            track: None,
            saved_at: Some(prost_types_014::Timestamp {
                seconds: 1,
                nanos: 0,
            }),
        };
        assert_eq!(
            map_saved_track(missing_track).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );
        let missing_timestamp = LikedTrack {
            track: Some(track("track-1")),
            liked_at: None,
        };
        assert_eq!(
            map_liked_track(missing_timestamp).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );
        let invalid_timestamp = SavedTrack {
            track: Some(track("track-1")),
            saved_at: Some(prost_types_014::Timestamp {
                seconds: -1,
                nanos: 0,
            }),
        };
        assert_eq!(
            map_saved_track(invalid_timestamp).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn preserves_opaque_tokens() {
        let page = map_saved_page(
            Vec::new(),
            Some(PageInfo {
                next_page_token: "opaque+/=".into(),
            }),
        )
        .unwrap();
        assert_eq!(page.next_page_token.unwrap().as_str(), "opaque+/=");
    }
}
