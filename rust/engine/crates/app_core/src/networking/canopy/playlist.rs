use std::sync::Arc;

use tonic_014::Request;
use url::Url;

use crate::{
    EngineCreatePlaylist, EngineError, EngineErrorType, EnginePageRequest, EnginePageToken,
    EnginePagedResult, EnginePlaylist, EnginePlaylistIdentity, EnginePlaylistTrack,
    EngineUpdatePlaylist, PlaylistPort,
};

use super::catalog::{map_page_request, map_track_summary};
use super::operation::CanopyOperation;
use super::request::execute_with_bound_auth;
use super::sdk::clients::playlist_service_client::PlaylistServiceClient;
use super::sdk::resources::{
    AddPlaylistTrackRequest, CreatePlaylistRequest, DeletePlaylistRequest, GetPlaylistRequest,
    ListPlaylistTracksRequest, ListPlaylistTracksResponse, ListPlaylistsRequest,
    ListPlaylistsResponse, PageInfo, Playlist, PlaylistTrack, RemovePlaylistTrackRequest,
    ReorderPlaylistTracksRequest, UpdatePlaylistRequest,
};
use super::{CanopyChannel, SessionCoordinator};

#[derive(Clone)]
pub struct CanopyPlaylistClient {
    client: PlaylistServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
    media_origin: Url,
}

impl CanopyPlaylistClient {
    pub fn new(channel: &CanopyChannel, session: Arc<SessionCoordinator>, media_origin: Url) -> Self {
        Self {
            client: PlaylistServiceClient::new(channel.clone_inner()),
            session,
            media_origin,
        }
    }
}

#[async_trait::async_trait]
impl PlaylistPort for CanopyPlaylistClient {
    async fn create(
        &self,
        identity: &EnginePlaylistIdentity,
        input: EngineCreatePlaylist,
    ) -> Result<EnginePlaylist, EngineError> {
        validate_name(&input.name)?;
        let request = CreatePlaylistRequest {
            name: input.name,
            description: input.description,
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::CreatePlaylist,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.create_playlist(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist(response)
    }

    async fn get(
        &self,
        identity: &EnginePlaylistIdentity,
        id: &str,
    ) -> Result<EnginePlaylist, EngineError> {
        require_id("playlist id", id)?;
        let request = GetPlaylistRequest {
            playlist_id: id.into(),
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::GetPlaylist,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.get_playlist(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist(response)
    }

    async fn update(
        &self,
        identity: &EnginePlaylistIdentity,
        input: EngineUpdatePlaylist,
    ) -> Result<EnginePlaylist, EngineError> {
        require_id("playlist id", &input.id)?;
        validate_name(&input.name)?;
        let request = UpdatePlaylistRequest {
            playlist: Some(Playlist {
                id: input.id,
                name: input.name,
                description: input.description,
                revision: input.expected_revision,
                created_at: None,
                updated_at: None,
            }),
            update_mask: Some(prost_types_014::FieldMask {
                paths: vec!["name".into(), "description".into()],
            }),
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::UpdatePlaylist,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.update_playlist(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist(response)
    }

    async fn delete(&self, identity: &EnginePlaylistIdentity, id: &str) -> Result<(), EngineError> {
        require_id("playlist id", id)?;
        let request = DeletePlaylistRequest {
            playlist_id: id.into(),
        };
        let client = self.client.clone();
        execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::DeletePlaylist,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.delete_playlist(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn list(
        &self,
        identity: &EnginePlaylistIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylist>, EngineError> {
        let request = ListPlaylistsRequest {
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ListPlaylists,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.list_playlists(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist_page(response)
    }

    async fn add_track(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        track_id: &str,
    ) -> Result<EnginePlaylistTrack, EngineError> {
        require_id("playlist id", playlist_id)?;
        require_id("track id", track_id)?;
        let request = AddPlaylistTrackRequest {
            playlist_id: playlist_id.into(),
            track_id: track_id.into(),
            position: None,
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::AddPlaylistTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.add_playlist_track(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist_track(response, Some(&self.media_origin))
    }

    async fn remove_track(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        track_id: &str,
    ) -> Result<(), EngineError> {
        require_id("playlist id", playlist_id)?;
        require_id("track id", track_id)?;
        let request = RemovePlaylistTrackRequest {
            playlist_id: playlist_id.into(),
            track_id: track_id.into(),
        };
        let client = self.client.clone();
        execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::RemovePlaylistTrack,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.remove_playlist_track(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn reorder(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        ordered_membership_ids: &[String],
        expected_revision: u64,
    ) -> Result<EnginePlaylist, EngineError> {
        require_id("playlist id", playlist_id)?;
        if ordered_membership_ids.is_empty()
            || ordered_membership_ids.iter().any(|id| id.trim().is_empty())
        {
            return Err(invalid_input(
                "playlist reorder requires complete ordered membership ids",
            ));
        }
        let request = ReorderPlaylistTracksRequest {
            playlist_id: playlist_id.into(),
            track_ids: ordered_membership_ids.to_vec(),
            expected_revision,
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ReorderPlaylistTracks,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.reorder_playlist_tracks(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist(response)
    }

    async fn list_tracks(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError> {
        require_id("playlist id", playlist_id)?;
        let request = ListPlaylistTracksRequest {
            playlist_id: playlist_id.into(),
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute_playlist_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ListPlaylistTracks,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.list_playlist_tracks(request).await }
            },
        )
        .await?
        .into_inner();
        map_playlist_track_page(response, Some(&self.media_origin))
    }
}

async fn execute_playlist_request<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    session: &SessionCoordinator,
    identity: &EnginePlaylistIdentity,
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
    let bound = crate::EngineHistoryIdentity {
        account_id: identity.account_id.clone(),
        session_id: identity.session_id.clone(),
    };
    execute_with_bound_auth(session, &bound, operation, make_request, execute).await
}

fn map_playlist_page(
    response: ListPlaylistsResponse,
) -> Result<EnginePagedResult<EnginePlaylist>, EngineError> {
    Ok(EnginePagedResult {
        items: response
            .playlists
            .into_iter()
            .map(map_playlist)
            .collect::<Result<_, _>>()?,
        next_page_token: map_page_token(response.page_info)?,
    })
}
fn map_playlist_track_page(
    response: ListPlaylistTracksResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError> {
    Ok(EnginePagedResult {
        items: response
            .tracks
            .into_iter()
            .map(|track| map_playlist_track(track, media_origin))
            .collect::<Result<_, _>>()?,
        next_page_token: map_page_token(response.page_info)?,
    })
}
fn map_page_token(page_info: Option<PageInfo>) -> Result<Option<EnginePageToken>, EngineError> {
    match page_info.map(|value| value.next_page_token) {
        Some(token) if !token.is_empty() => EnginePageToken::new(token)
            .map(Some)
            .map_err(|_| mapping_defect("playlist returned an invalid page token")),
        _ => Ok(None),
    }
}
fn map_playlist(value: Playlist) -> Result<EnginePlaylist, EngineError> {
    require_mapping("playlist id", &value.id)?;
    require_mapping("playlist name", &value.name)?;
    Ok(EnginePlaylist {
        id: value.id,
        name: value.name,
        description: value.description,
        revision: value.revision,
        created_at_epoch_millis: timestamp_to_millis(
            value.created_at,
            "playlist missing created timestamp",
        )?,
        updated_at_epoch_millis: timestamp_to_millis(
            value.updated_at,
            "playlist missing updated timestamp",
        )?,
    })
}
fn map_playlist_track(
    value: PlaylistTrack,
    media_origin: Option<&Url>,
) -> Result<EnginePlaylistTrack, EngineError> {
    require_mapping("playlist track playlist id", &value.playlist_id)?;
    let track = value
        .track
        .ok_or_else(|| mapping_defect("playlist track missing track"))?;
    let membership_id = track.id.clone();
    require_mapping("playlist track id", &membership_id)?;
    Ok(EnginePlaylistTrack {
        membership_id,
        playlist_id: value.playlist_id,
        track: map_track_summary(track, Vec::new(), media_origin)?,
        position: value.position,
        added_at_epoch_millis: timestamp_to_millis(
            value.added_at,
            "playlist track missing added timestamp",
        )?,
    })
}
fn timestamp_to_millis(
    timestamp: Option<prost_types_014::Timestamp>,
    missing: &'static str,
) -> Result<u64, EngineError> {
    let value = timestamp.ok_or_else(|| mapping_defect(missing))?;
    if !(0..=253_402_300_799).contains(&value.seconds) || !(0..1_000_000_000).contains(&value.nanos)
    {
        return Err(mapping_defect("playlist timestamp is invalid"));
    }
    u64::try_from(value.seconds)
        .ok()
        .and_then(|seconds| seconds.checked_mul(1_000))
        .and_then(|millis| millis.checked_add(u64::from(value.nanos as u32) / 1_000_000))
        .ok_or_else(|| mapping_defect("playlist timestamp overflowed"))
}
fn validate_name(name: &str) -> Result<(), EngineError> {
    if name.trim().is_empty() {
        Err(invalid_input("playlist name is required"))
    } else {
        Ok(())
    }
}
fn require_id(label: &'static str, id: &str) -> Result<(), EngineError> {
    if id.trim().is_empty() {
        Err(invalid_input(format!("{label} is required")))
    } else {
        Ok(())
    }
}
fn require_mapping(label: &'static str, value: &str) -> Result<(), EngineError> {
    if value.trim().is_empty() {
        Err(mapping_defect(format!("{label} is missing")))
    } else {
        Ok(())
    }
}
fn invalid_input(message: impl Into<String>) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}
fn mapping_defect(message: impl Into<String>) -> EngineError {
    EngineError::new(EngineErrorType::MappingDefect, message, false)
}
