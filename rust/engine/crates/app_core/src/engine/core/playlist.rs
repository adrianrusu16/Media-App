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
                    Ok((identity, port)) => {
                        let listed = match self.take_prefetched_playlists() {
                            Some(prefetched) => prefetched,
                            None => port.list(&identity.playlist_identity(), page.clone()).await,
                        };
                        match self.playlist_result_for_current_identity(snapshot, &identity, listed)
                        {
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
                        }
                    }
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
                        .map(PlaylistMutation::Playlist)
                })
                .await
            }
            EngineCommandType::UpdatePlaylist { input } => {
                self.mutate_playlist(snapshot, |identity, port| async move {
                    port.update(&identity.playlist_identity(), input.clone())
                        .await
                        .map(PlaylistMutation::Playlist)
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
                if snapshot.playlist_tracks_playlist_id.as_deref() == Some(playlist_id)
                    && snapshot.playlist_tracks_next_page_token.is_some()
                {
                    snapshot.last_error = Some(EngineError::new(
                        EngineErrorType::InvalidInput,
                        "playlist reorder requires the complete membership projection",
                        false,
                    ));
                    return;
                }
                let (result, proposal, identity) = match self.playlist_context(snapshot) {
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
                            Some(identity),
                        )
                    }
                    Err(error) => (Err(error), None, None),
                };
                match result {
                    Ok(playlist) => {
                        self.playlist_projection_identity = identity;
                        snapshot.playlist_reconciliation = None;
                        upsert_playlist(&mut snapshot.playlists, playlist);
                        if snapshot.playlist_tracks_playlist_id.as_deref()
                            == Some(playlist_id.as_str())
                        {
                            apply_membership_order(
                                &mut snapshot.playlist_tracks,
                                ordered_membership_ids,
                            );
                        }
                    }
                    Err(error) => {
                        if error.error_type == EngineErrorType::Conflict {
                            snapshot.playlist_reconciliation = proposal;
                            self.playlist_projection_identity = identity;
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
fn apply_membership_order(
    tracks: &mut [crate::EnginePlaylistTrack],
    ordered_membership_ids: &[String],
) {
    tracks.sort_by_key(|track| {
        ordered_membership_ids
            .iter()
            .position(|id| id == &track.membership_id)
            .unwrap_or(usize::MAX)
    });
    for (position, track) in tracks.iter_mut().enumerate() {
        track.position = u32::try_from(position).unwrap_or(u32::MAX);
    }
}
fn playlist_error(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::FailedPrecondition, message, false)
}

#[cfg(test)]
mod tests {
    use std::sync::{Arc, Mutex};

    use async_trait::async_trait;

    use super::*;
    use crate::{
        Account, AuthSession, AuthState, AuthStateProvider, EngineCreatePlaylist, EnginePageToken,
        EnginePagedResult, EnginePlaylist, EnginePlaylistIdentity, EnginePlaylistTrack,
        EngineUpdatePlaylist,
    };

    #[derive(Clone)]
    struct MutableAuth(Arc<Mutex<AuthState>>);

    impl MutableAuth {
        fn new() -> Self {
            Self(Arc::new(Mutex::new(auth("account-1", "session-1"))))
        }
        fn switch(&self) {
            *self.0.lock().unwrap() = auth("account-2", "session-2");
        }
    }

    impl AuthStateProvider for MutableAuth {
        fn current_auth_state(&self) -> AuthState {
            self.0.lock().unwrap().clone()
        }
    }

    fn auth(account: &str, session: &str) -> AuthState {
        AuthState::Authenticated {
            account: Account {
                id: account.into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 1,
            },
            session: AuthSession {
                id: session.into(),
                device_label: "car".into(),
                created_at_epoch_millis: 1,
                last_used_at_epoch_millis: 1,
                expires_at_epoch_millis: 10_000,
                current: true,
            },
        }
    }

    struct ReorderPort {
        conflict: bool,
        calls: Mutex<usize>,
    }

    #[async_trait]
    impl PlaylistPort for ReorderPort {
        async fn create(
            &self,
            _: &EnginePlaylistIdentity,
            _: EngineCreatePlaylist,
        ) -> Result<EnginePlaylist, EngineError> {
            unreachable!()
        }
        async fn get(
            &self,
            _: &EnginePlaylistIdentity,
            id: &str,
        ) -> Result<EnginePlaylist, EngineError> {
            Ok(playlist(id, 8))
        }
        async fn update(
            &self,
            _: &EnginePlaylistIdentity,
            _: EngineUpdatePlaylist,
        ) -> Result<EnginePlaylist, EngineError> {
            unreachable!()
        }
        async fn delete(&self, _: &EnginePlaylistIdentity, _: &str) -> Result<(), EngineError> {
            unreachable!()
        }
        async fn list(
            &self,
            _: &EnginePlaylistIdentity,
            _: EnginePageRequest,
        ) -> Result<EnginePagedResult<EnginePlaylist>, EngineError> {
            unreachable!()
        }
        async fn add_track(
            &self,
            _: &EnginePlaylistIdentity,
            _: &str,
            _: &str,
        ) -> Result<EnginePlaylistTrack, EngineError> {
            unreachable!()
        }
        async fn remove_track(
            &self,
            _: &EnginePlaylistIdentity,
            _: &str,
            _: &str,
        ) -> Result<(), EngineError> {
            unreachable!()
        }
        async fn reorder(
            &self,
            _: &EnginePlaylistIdentity,
            playlist_id: &str,
            _: &[String],
            _: u64,
        ) -> Result<EnginePlaylist, EngineError> {
            *self.calls.lock().unwrap() += 1;
            if self.conflict {
                Err(EngineError::new(
                    EngineErrorType::Conflict,
                    "revision conflict",
                    false,
                ))
            } else {
                Ok(playlist(playlist_id, 8))
            }
        }
        async fn list_tracks(
            &self,
            _: &EnginePlaylistIdentity,
            playlist_id: &str,
            _: EnginePageRequest,
        ) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError> {
            Ok(EnginePagedResult {
                items: vec![track(playlist_id, "m2", 0), track(playlist_id, "m1", 1)],
                next_page_token: None,
            })
        }
    }

    fn playlist(id: &str, revision: u64) -> EnginePlaylist {
        EnginePlaylist {
            id: id.into(),
            name: "Mix".into(),
            description: None,
            revision,
            created_at_epoch_millis: 1,
            updated_at_epoch_millis: 2,
        }
    }

    fn track(playlist_id: &str, membership_id: &str, position: u32) -> EnginePlaylistTrack {
        EnginePlaylistTrack {
            membership_id: membership_id.into(),
            playlist_id: playlist_id.into(),
            track: crate::EngineTrack {
                id: membership_id.into(),
                title: membership_id.into(),
                artist: crate::EngineArtist {
                    id: "artist".into(),
                    name: "Artist".into(),
                },
                album: None,
                duration_millis: 0,
                explicit: false,
                artwork_id: None,
                genres: Vec::new(),
            },
            position,
            added_at_epoch_millis: 1,
        }
    }

    fn reorder(ids: &[&str]) -> EngineCommand {
        reorder_for("p1", ids)
    }

    fn reorder_for(playlist_id: &str, ids: &[&str]) -> EngineCommand {
        EngineCommand::new(
            EngineCommandType::ReorderPlaylistTracks {
                playlist_id: playlist_id.into(),
                ordered_membership_ids: ids.iter().map(|id| (*id).to_owned()).collect(),
                expected_revision: 7,
            },
            None,
        )
    }

    #[tokio::test]
    async fn reorder_rejects_partial_projection_and_does_not_call_port() {
        let auth = Arc::new(MutableAuth::new());
        let port = Arc::new(ReorderPort {
            conflict: false,
            calls: Mutex::new(0),
        });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth);
        engine.set_playlist_port(port.clone());
        engine.snapshot.playlist_tracks_playlist_id = Some("p1".into());
        engine.snapshot.playlist_tracks = vec![track("p1", "m1", 0)];
        engine.snapshot.playlist_tracks_next_page_token =
            Some(EnginePageToken::new("next".into()).unwrap());

        let outcome = engine.dispatch(reorder(&["m1"]), 1).await;

        assert_eq!(
            outcome.snapshot.last_error.unwrap().error_type,
            EngineErrorType::InvalidInput
        );
        assert_eq!(*port.calls.lock().unwrap(), 0);
    }

    #[tokio::test]
    async fn successful_reorder_projects_order_and_binds_it_to_identity() {
        let auth = Arc::new(MutableAuth::new());
        let port = Arc::new(ReorderPort {
            conflict: false,
            calls: Mutex::new(0),
        });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth.clone());
        engine.set_playlist_port(port);
        engine.snapshot.playlist_tracks_playlist_id = Some("p1".into());
        engine.snapshot.playlist_tracks = vec![track("p1", "m1", 0), track("p1", "m2", 1)];

        let outcome = engine.dispatch(reorder(&["m2", "m1"]), 1).await;
        assert_eq!(
            outcome
                .snapshot
                .playlist_tracks
                .iter()
                .map(|item| item.membership_id.as_str())
                .collect::<Vec<_>>(),
            ["m2", "m1"]
        );

        auth.switch();
        assert!(engine.snapshot().playlist_tracks.is_empty());
    }

    #[tokio::test]
    async fn successful_reorder_does_not_mutate_another_selected_playlist_projection() {
        let auth = Arc::new(MutableAuth::new());
        let port = Arc::new(ReorderPort {
            conflict: false,
            calls: Mutex::new(0),
        });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth);
        engine.set_playlist_port(port);
        engine.snapshot.playlists = vec![playlist("playlist-a", 7)];
        engine.snapshot.playlist_tracks_playlist_id = Some("playlist-b".into());
        engine.snapshot.playlist_tracks = vec![
            track("playlist-b", "member-b1", 5),
            track("playlist-b", "member-b2", 9),
        ];
        let previous_tracks = engine.snapshot.playlist_tracks.clone();

        let outcome = engine
            .dispatch(reorder_for("playlist-a", &["member-a2", "member-a1"]), 1)
            .await;

        assert_eq!(outcome.snapshot.playlist_tracks, previous_tracks);
        assert_eq!(outcome.snapshot.playlists[0].id, "playlist-a");
        assert_eq!(outcome.snapshot.playlists[0].revision, 8);
    }

    #[tokio::test]
    async fn conflict_projection_is_bound_to_identity_when_reorder_is_first_operation() {
        let auth = Arc::new(MutableAuth::new());
        let port = Arc::new(ReorderPort {
            conflict: true,
            calls: Mutex::new(0),
        });
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth.clone());
        engine.set_playlist_port(port);

        let outcome = engine.dispatch(reorder(&["m1", "m2"]), 1).await;
        assert!(outcome.snapshot.playlist_reconciliation.is_some());

        auth.switch();
        assert!(engine.snapshot().playlist_reconciliation.is_none());
    }
}
