use super::*;
use crate::{EngineErrorType, EngineLibraryTrack};

impl Engine {
    pub(super) async fn dispatch_library_command(
        &mut self,
        command: &EngineCommandType,
        snapshot: &mut EngineSnapshot,
    ) {
        match command {
            EngineCommandType::ListSavedTracks { page } => {
                self.saved_library_operation = None;
                snapshot.saved_tracks.clear();
                snapshot.saved_tracks_next_page_token = None;
                match self.load_library_page(snapshot, page.clone(), true).await {
                    Ok((identity, result)) => {
                        snapshot.saved_tracks = result.items;
                        snapshot.saved_tracks_next_page_token = result.next_page_token;
                        self.library_projection_identity = Some(identity.clone());
                        self.saved_library_operation = Some(LibraryPageOperation {
                            auth_identity: identity,
                            page_size: page.page_size,
                        });
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {}
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::LoadNextSavedTracksPage => {
                let result = self.load_next_library_page(snapshot, true).await;
                match result {
                    Ok(result) => {
                        append_unique(&mut snapshot.saved_tracks, result.items);
                        snapshot.saved_tracks_next_page_token = result.next_page_token;
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::ListLikedTracks { page } => {
                self.liked_library_operation = None;
                snapshot.liked_tracks.clear();
                snapshot.liked_tracks_next_page_token = None;
                match self.load_library_page(snapshot, page.clone(), false).await {
                    Ok((identity, result)) => {
                        snapshot.liked_tracks = result.items;
                        snapshot.liked_tracks_next_page_token = result.next_page_token;
                        self.library_projection_identity = Some(identity.clone());
                        self.liked_library_operation = Some(LibraryPageOperation {
                            auth_identity: identity,
                            page_size: page.page_size,
                        });
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {}
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::LoadNextLikedTracksPage => {
                let result = self.load_next_library_page(snapshot, false).await;
                match result {
                    Ok(result) => {
                        append_unique(&mut snapshot.liked_tracks, result.items);
                        snapshot.liked_tracks_next_page_token = result.next_page_token;
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::SaveTrack { track_id } => {
                self.mutate_library(snapshot, track_id, LibraryMutation::Save)
                    .await;
            }
            EngineCommandType::RemoveSavedTrack { track_id } => {
                self.mutate_library(snapshot, track_id, LibraryMutation::RemoveSaved)
                    .await;
            }
            EngineCommandType::LikeTrack { track_id } => {
                self.mutate_library(snapshot, track_id, LibraryMutation::Like)
                    .await;
            }
            EngineCommandType::UnlikeTrack { track_id } => {
                self.mutate_library(snapshot, track_id, LibraryMutation::Unlike)
                    .await;
            }
            _ => unreachable!("library dispatcher received a non-library command"),
        }
    }

    async fn load_library_page(
        &mut self,
        snapshot: &mut EngineSnapshot,
        page: crate::EnginePageRequest,
        saved: bool,
    ) -> Result<(AuthIdentity, crate::EnginePagedResult<EngineLibraryTrack>), EngineError> {
        let (identity, port) = Self::library_context(snapshot, self.library_port.clone())?;
        let result = if saved {
            port.list_saved(&identity.library_identity(), page).await
        } else {
            port.list_liked(&identity.library_identity(), page).await
        };
        self.library_result_for_current_identity(snapshot, &identity, result)
            .map(|result| (identity, result))
    }

    async fn load_next_library_page(
        &mut self,
        snapshot: &mut EngineSnapshot,
        saved: bool,
    ) -> Result<crate::EnginePagedResult<EngineLibraryTrack>, EngineError> {
        let operation = if saved {
            self.saved_library_operation.clone()
        } else {
            self.liked_library_operation.clone()
        };
        let token = if saved {
            snapshot.saved_tracks_next_page_token.clone()
        } else {
            snapshot.liked_tracks_next_page_token.clone()
        };
        let identity = AuthIdentity::from_state(&snapshot.auth_state)
            .ok_or_else(Self::library_login_required)?;
        let operation = operation.ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "no current library page operation",
                false,
            )
        })?;
        if identity != operation.auth_identity {
            return Err(Self::library_login_required());
        }
        let token = token.ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "library page has no continuation",
                false,
            )
        })?;
        let port = self.library_port.clone().ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "library service is not configured",
                false,
            )
        })?;
        let page = crate::EnginePageRequest {
            page_size: operation.page_size,
            page_token: Some(token),
        };
        let result = if saved {
            port.list_saved(&identity.library_identity(), page).await
        } else {
            port.list_liked(&identity.library_identity(), page).await
        };
        self.library_result_for_current_identity(snapshot, &identity, result)
    }

    async fn mutate_library(
        &mut self,
        snapshot: &mut EngineSnapshot,
        track_id: &str,
        mutation: LibraryMutation,
    ) {
        if track_id.trim().is_empty() {
            snapshot.last_error = Some(EngineError::new(
                EngineErrorType::InvalidInput,
                "library track id is required",
                false,
            ));
            return;
        }
        let (identity, port) = match Self::library_context(snapshot, self.library_port.clone()) {
            Ok(context) => context,
            Err(error) => {
                snapshot.last_error = Some(error);
                return;
            }
        };
        let previous_saved = snapshot.saved_tracks.clone();
        let previous_liked = snapshot.liked_tracks.clone();
        if !snapshot
            .library_pending_track_ids
            .iter()
            .any(|id| id == track_id)
        {
            snapshot.library_pending_track_ids.push(track_id.to_owned());
        }
        match mutation {
            LibraryMutation::RemoveSaved => snapshot
                .saved_tracks
                .retain(|item| item.track.id != track_id),
            LibraryMutation::Unlike => snapshot
                .liked_tracks
                .retain(|item| item.track.id != track_id),
            LibraryMutation::Save | LibraryMutation::Like => {}
        }
        self.library_projection_identity = Some(identity.clone());
        self.publish_intermediate_snapshot(snapshot.clone());

        let result = match mutation {
            LibraryMutation::Save => port
                .save(&identity.library_identity(), track_id)
                .await
                .map(Some),
            LibraryMutation::RemoveSaved => port
                .remove_saved(&identity.library_identity(), track_id)
                .await
                .map(|()| None),
            LibraryMutation::Like => port
                .like(&identity.library_identity(), track_id)
                .await
                .map(Some),
            LibraryMutation::Unlike => port
                .unlike(&identity.library_identity(), track_id)
                .await
                .map(|()| None),
        };
        let result = self.library_result_for_current_identity(snapshot, &identity, result);
        snapshot
            .library_pending_track_ids
            .retain(|id| id != track_id);
        match result {
            Ok(Some(item)) => match mutation {
                LibraryMutation::Save => upsert(&mut snapshot.saved_tracks, item),
                LibraryMutation::Like => upsert(&mut snapshot.liked_tracks, item),
                LibraryMutation::RemoveSaved | LibraryMutation::Unlike => unreachable!(),
            },
            Ok(None) => {}
            Err(error) => {
                if AuthIdentity::from_state(&snapshot.auth_state).as_ref() == Some(&identity) {
                    snapshot.saved_tracks = previous_saved;
                    snapshot.liked_tracks = previous_liked;
                }
                snapshot.last_error = Some(error);
            }
        }
    }

    fn library_context(
        snapshot: &EngineSnapshot,
        port: Option<Arc<dyn crate::LibraryPort>>,
    ) -> Result<(AuthIdentity, Arc<dyn crate::LibraryPort>), EngineError> {
        let identity = AuthIdentity::from_state(&snapshot.auth_state)
            .ok_or_else(Self::library_login_required)?;
        let port = port.ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "library service is not configured",
                false,
            )
        })?;
        Ok((identity, port))
    }

    fn library_result_for_current_identity<T>(
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
            self.library_projection_identity = None;
            self.saved_library_operation = None;
            self.liked_library_operation = None;
            Self::clear_library_projection(snapshot);
            Err(Self::library_login_required())
        }
    }

    fn library_login_required() -> EngineError {
        EngineError::new(
            EngineErrorType::LoginRequired,
            "library operation requires the current authenticated session",
            false,
        )
    }
}

#[derive(Clone, Copy)]
enum LibraryMutation {
    Save,
    RemoveSaved,
    Like,
    Unlike,
}

fn upsert(items: &mut Vec<EngineLibraryTrack>, item: EngineLibraryTrack) {
    items.retain(|existing| existing.relationship_id != item.relationship_id);
    items.insert(0, item);
}

fn append_unique(items: &mut Vec<EngineLibraryTrack>, incoming: Vec<EngineLibraryTrack>) {
    for item in incoming {
        if !items
            .iter()
            .any(|existing| existing.relationship_id == item.relationship_id)
        {
            items.push(item);
        }
    }
}
