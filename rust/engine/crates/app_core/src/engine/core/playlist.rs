use std::sync::Arc;

use super::*;
use crate::{EngineError, EngineErrorType, EnginePageRequest, PlaylistPort, PlaylistReconciler};

impl Engine {
    pub(super) async fn dispatch_playlist_command(
        &mut self,
        command: &EngineCommandType,
        snapshot: &mut EngineSnapshot,
    ) {
        match command {
            EngineCommandType::ListPlaylists { page } => {
                snapshot.playlists.clear();
                snapshot.playlists_next_page_token = None;
                self.playlists_operation = None;
                match self.playlist_context(snapshot) {
                    Ok((identity, port)) => match self.playlist_result_for_current_identity(
                        snapshot,
                        &identity,
                        port.list(&identity.playlist_identity(), page.clone()).await,
                    ) {
                        Ok(result) => {
                            snapshot.playlists = result.items;
                            snapshot.playlists_next_page_token = result.next_page_token;
                            self.playlist_projection_identity = Some(identity.clone());
                            self.playlists_operation = Some(PlaylistPageOperation {
                                auth_identity: identity,
                                playlist_id: None,
                                page_size: page.page_size,
                            });
                        }
                        Err(error) if error.error_type == EngineErrorType::NotFound => {}
                        Err(error) => snapshot.last_error = Some(error),
                    },
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::LoadNextPlaylistsPage => {
                match self.load_next_playlists(snapshot).await {
                    Ok(result) => {
                        snapshot.playlists.extend(result.items);
                        snapshot.playlists_next_page_token = result.next_page_token;
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::ListPlaylistTracks { playlist_id, page } => {
                snapshot.playlist_tracks.clear();
                snapshot.playlist_tracks_next_page_token = None;
                snapshot.playlist_tracks_playlist_id = None;
                self.playlist_tracks_operation = None;
                match self.playlist_context(snapshot) {
                    Ok((identity, port)) => match self.playlist_result_for_current_identity(
                        snapshot,
                        &identity,
                        port.list_tracks(&identity.playlist_identity(), playlist_id, page.clone())
                            .await,
                    ) {
                        Ok(result) => {
                            snapshot.playlist_tracks = result.items;
                            snapshot.playlist_tracks_next_page_token = result.next_page_token;
                            snapshot.playlist_tracks_playlist_id = Some(playlist_id.clone());
                            self.playlist_projection_identity = Some(identity.clone());
                            self.playlist_tracks_operation = Some(PlaylistPageOperation {
                                auth_identity: identity,
                                playlist_id: Some(playlist_id.clone()),
                                page_size: page.page_size,
                            });
                        }
                        Err(error) if error.error_type == EngineErrorType::NotFound => {}
                        Err(error) => snapshot.last_error = Some(error),
                    },
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::LoadNextPlaylistTracksPage => match self
                .load_next_playlist_page(snapshot, snapshot.playlist_tracks_playlist_id.clone())
                .await
            {
                Ok(result) => {
                    snapshot.playlist_tracks.extend(result.items);
                    snapshot.playlist_tracks_next_page_token = result.next_page_token;
                }
                Err(error) => snapshot.last_error = Some(error),
            },
            EngineCommandType::CreatePlaylist { input } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.create(&identity.playlist_identity(), input.clone())
                        .await
                        .map(|playlist| PlaylistMutation::Playlist(playlist))
                })
                .await
            }
            EngineCommandType::UpdatePlaylist { input } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.update(&identity.playlist_identity(), input.clone())
                        .await
                        .map(|playlist| PlaylistMutation::Playlist(playlist))
                })
                .await
            }
            EngineCommandType::DeletePlaylist { playlist_id } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.delete(&identity.playlist_identity(), playlist_id)
                        .await
                        .map(|_| PlaylistMutation::Deleted(playlist_id.clone()))
                })
                .await
            }
            EngineCommandType::AddPlaylistTrack {
                playlist_id,
                track_id,
            } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.add_track(&identity.playlist_identity(), playlist_id, track_id)
                        .await
                        .map(PlaylistMutation::Track)
                })
                .await
            }
            EngineCommandType::RemovePlaylistTrack {
                playlist_id,
                track_id,
            } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.remove_track(&identity.playlist_identity(), playlist_id, track_id)
                        .await
                        .map(|_| PlaylistMutation::RemovedTrack {
                            playlist_id: playlist_id.clone(),
                            membership_id: track_id.clone(),
                        })
                })
                .await
            }
            EngineCommandType::ReorderPlaylistTracks {
                playlist_id,
                ordered_membership_ids,
                expected_revision,
            } => {
                let (result, proposal) = match self.playlist_context(snapshot) {
                    Ok((identity, port)) => {
                        let reconciler = PlaylistReconciler::new(port.clone());
                        let result = reconciler
                            .reorder(
                                &identity.playlist_identity(),
                                playlist_id,
                                ordered_membership_ids.clone(),
                                *expected_revision,
                            )
                            .await;
                        let proposal = reconciler.proposed();
                        (
                            self.playlist_result_for_current_identity(snapshot, &identity, result),
                            proposal,
                        )
                    }
                    Err(error) => (Err(error), None),
                };
                match result {
                    Ok(playlist) => {
                        snapshot.playlist_reconciliation = None;
                        upsert_playlist(&mut snapshot.playlists, playlist);
                    }
                    Err(error) => {
                        if error.error_type == EngineErrorType::Conflict {
                            snapshot.playlist_reconciliation = proposal;
                        }
                        snapshot.last_error = Some(error);
                    }
                }
            }
            _ => unreachable!("playlist dispatcher received a non-playlist command"),
        }
    }

    async fn load_next_playlist_page(
        &mut self,
        snapshot: &mut EngineSnapshot,
        playlist_id: Option<String>,
    ) -> Result<crate::EnginePagedResult<crate::EnginePlaylistTrack>, EngineError> {
        let operation = self
            .playlist_tracks_operation
            .clone()
            .ok_or_else(|| playlist_error("no current playlist tracks page operation"))?;
        let token = snapshot
            .playlist_tracks_next_page_token
            .clone()
            .ok_or_else(|| playlist_error("playlist tracks have no continuation"))?;
        let identity = AuthIdentity::from_state(&snapshot.auth_state)
            .ok_or_else(Self::playlist_login_required)?;
        if identity != operation.auth_identity || operation.playlist_id != playlist_id {
            return Err(Self::playlist_login_required());
        }
        let port = self
            .playlist_port
            .clone()
            .ok_or_else(|| playlist_error("playlist service is not configured"))?;
        self.playlist_result_for_current_identity(
            snapshot,
            &identity,
            port.list_tracks(
                &identity.playlist_identity(),
                playlist_id.as_deref().unwrap_or_default(),
                EnginePageRequest {
                    page_size: operation.page_size,
                    page_token: Some(token),
                },
            )
            .await,
        )
    }

    async fn load_next_playlists(
        &mut self,
        snapshot: &mut EngineSnapshot,
    ) -> Result<crate::EnginePagedResult<crate::EnginePlaylist>, EngineError> {
        let operation = self
            .playlists_operation
            .clone()
            .ok_or_else(|| playlist_error("no current playlists page operation"))?;
        let token = snapshot
            .playlists_next_page_token
            .clone()
            .ok_or_else(|| playlist_error("playlists have no continuation"))?;
        let identity = AuthIdentity::from_state(&snapshot.auth_state)
            .ok_or_else(Self::playlist_login_required)?;
        if identity != operation.auth_identity || operation.playlist_id.is_some() {
            return Err(Self::playlist_login_required());
        }
        let port = self
            .playlist_port
            .clone()
            .ok_or_else(|| playlist_error("playlist service is not configured"))?;
        self.playlist_result_for_current_identity(
            snapshot,
            &identity,
            port.list(
                &identity.playlist_identity(),
                EnginePageRequest {
                    page_size: operation.page_size,
                    page_token: Some(token),
                },
            )
            .await,
        )
    }

    fn playlist_context(
        &self,
        snapshot: &EngineSnapshot,
    ) -> Result<(AuthIdentity, Arc<dyn PlaylistPort>), EngineError> {
        Ok((
            AuthIdentity::from_state(&snapshot.auth_state)
                .ok_or_else(Self::playlist_login_required)?,
            self.playlist_port
                .clone()
                .ok_or_else(|| playlist_error("playlist service is not configured"))?,
        ))
    }
    fn playlist_result_for_current_identity<T>(
        &mut self,
        snapshot: &mut EngineSnapshot,
        expected: &AuthIdentity,
        result: Result<T, EngineError>,
    ) -> Result<T, EngineError> {
        let auth_state = self
            .auth_state_provider
            .as_ref()
            .map(|provider| provider.current_auth_state())
            .unwrap_or(crate::AuthState::Anonymous);
        let current = AuthIdentity::from_state(&auth_state);
        snapshot.auth_state = auth_state;
        if current.as_ref() == Some(expected) {
            result
        } else {
            self.playlist_projection_identity = None;
            self.playlists_operation = None;
            self.playlist_tracks_operation = None;
            Self::clear_playlist_projection(snapshot);
            Err(Self::playlist_login_required())
        }
    }
    fn playlist_login_required() -> EngineError {
        EngineError::new(
            EngineErrorType::LoginRequired,
            "playlist operation requires the current authenticated session",
            false,
        )
    }
    async fn mutate_playlist<F, Fut>(&mut self, snapshot: &mut EngineSnapshot, action: F)
    where
        F: FnOnce(AuthIdentity, Arc<dyn PlaylistPort>) -> Fut,
        Fut: std::future::Future<Output = Result<PlaylistMutation, EngineError>>,
    {
        let (identity, port) = match self.playlist_context(snapshot) {
            Ok(value) => value,
            Err(error) => {
                snapshot.last_error = Some(error);
                return;
            }
        };
        match self.playlist_result_for_current_identity(
            snapshot,
            &identity,
            action(identity.clone(), port).await,
        ) {
            Ok(PlaylistMutation::Playlist(playlist)) => {
                self.playlist_projection_identity = Some(identity);
                upsert_playlist(&mut snapshot.playlists, playlist);
            }
            Ok(PlaylistMutation::Track(track)) => {
                if snapshot.playlist_tracks_playlist_id.as_deref() == Some(&track.playlist_id) {
                    snapshot.playlist_tracks.push(track);
                }
            }
            Ok(PlaylistMutation::Deleted(id)) => {
                snapshot.playlists.retain(|playlist| playlist.id != id);
                if snapshot.playlist_tracks_playlist_id.as_deref() == Some(&id) {
                    snapshot.playlist_tracks.clear();
                    snapshot.playlist_tracks_playlist_id = None;
                }
            }
            Ok(PlaylistMutation::RemovedTrack {
                playlist_id,
                membership_id,
            }) => {
                if snapshot.playlist_tracks_playlist_id.as_deref() == Some(&playlist_id) {
                    snapshot
                        .playlist_tracks
                        .retain(|track| track.membership_id != membership_id);
                }
            }
            Err(error) if error.error_type == EngineErrorType::NotFound => {}
            Err(error) => snapshot.last_error = Some(error),
        }
    }
}

enum PlaylistMutation {
    Playlist(crate::EnginePlaylist),
    Track(crate::EnginePlaylistTrack),
    Deleted(String),
    RemovedTrack {
        playlist_id: String,
        membership_id: String,
    },
}
fn upsert_playlist(items: &mut Vec<crate::EnginePlaylist>, playlist: crate::EnginePlaylist) {
    items.retain(|existing| existing.id != playlist.id);
    items.insert(0, playlist);
}
fn playlist_error(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::FailedPrecondition, message, false)
}
