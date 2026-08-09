use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use panda_engine_core::{
    Account, AuthSession, AuthState, AuthStateProvider, Engine, EngineCommand, EngineError,
    EngineErrorType, EngineLibraryIdentity, EngineLibraryRelationshipKind, EngineLibraryTrack,
    EnginePageRequest, EnginePageToken, EnginePagedResult, EngineSnapshot, LibraryPort,
};
use tokio::sync::Notify;

#[derive(Clone)]
struct MutableAuth(Arc<Mutex<AuthState>>);

impl MutableAuth {
    fn authenticated() -> Self {
        Self(Arc::new(Mutex::new(auth_state("account-1", "session-1"))))
    }
    fn replace(&self, account: &str, session: &str) {
        *self.0.lock().unwrap() = auth_state(account, session);
    }
    fn logout(&self) {
        *self.0.lock().unwrap() = AuthState::Anonymous;
    }
}

impl AuthStateProvider for MutableAuth {
    fn current_auth_state(&self) -> AuthState {
        self.0.lock().unwrap().clone()
    }
}

fn auth_state(account_id: &str, session_id: &str) -> AuthState {
    AuthState::Authenticated {
        account: Account {
            id: account_id.into(),
            primary_email: format!("{account_id}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 1,
        },
        session: AuthSession {
            id: session_id.into(),
            device_label: "PandaWave".into(),
            created_at_epoch_millis: 1,
            last_used_at_epoch_millis: 1,
            expires_at_epoch_millis: 10_000,
            current: true,
        },
    }
}

fn relation(kind: EngineLibraryRelationshipKind, id: &str, at: u64) -> EngineLibraryTrack {
    EngineLibraryTrack::new(kind, id, format!("Title {id}"), "artist-1", "Artist", at).unwrap()
}

struct RecordingLibraryPort {
    fail_save: bool,
    block_save: bool,
    save_started: Notify,
    release_save: Notify,
    owners: Mutex<Vec<EngineLibraryIdentity>>,
}

#[async_trait]
impl LibraryPort for RecordingLibraryPort {
    async fn save(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError> {
        self.owners.lock().unwrap().push(identity.clone());
        if self.block_save {
            self.save_started.notify_one();
            self.release_save.notified().await;
        }
        if self.fail_save {
            Err(EngineError::new(
                EngineErrorType::ServiceUnavailable,
                "sanitized save failure",
                false,
            ))
        } else {
            Ok(relation(EngineLibraryRelationshipKind::Saved, track_id, 11))
        }
    }
    async fn remove_saved(&self, _: &EngineLibraryIdentity, _: &str) -> Result<(), EngineError> {
        Ok(())
    }
    async fn list_saved(
        &self,
        _: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
        Ok(EnginePagedResult {
            items: vec![relation(
                EngineLibraryRelationshipKind::Saved,
                if page.page_token.is_some() {
                    "saved-2"
                } else {
                    "saved-1"
                },
                10,
            )],
            next_page_token: if page.page_token.is_none() {
                Some(EnginePageToken::new("saved+/=".into()).unwrap())
            } else {
                None
            },
        })
    }
    async fn like(
        &self,
        _: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError> {
        Ok(relation(EngineLibraryRelationshipKind::Liked, track_id, 12))
    }
    async fn unlike(&self, _: &EngineLibraryIdentity, _: &str) -> Result<(), EngineError> {
        Ok(())
    }
    async fn list_liked(
        &self,
        _: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError> {
        Ok(EnginePagedResult {
            items: vec![relation(
                EngineLibraryRelationshipKind::Liked,
                if page.page_token.is_some() {
                    "liked-2"
                } else {
                    "liked-1"
                },
                20,
            )],
            next_page_token: if page.page_token.is_none() {
                Some(EnginePageToken::new("liked+/=".into()).unwrap())
            } else {
                None
            },
        })
    }
}

fn engine(port: Arc<RecordingLibraryPort>, auth: Arc<MutableAuth>) -> Engine {
    let mut engine = Engine::new(0);
    engine.set_auth_state_provider(auth);
    engine.set_library_port(port);
    engine
}

#[tokio::test]
async fn saved_and_liked_pages_are_engine_owned_and_paginate_without_duplicates() {
    let auth = Arc::new(MutableAuth::authenticated());
    let port = Arc::new(RecordingLibraryPort {
        fail_save: false,
        block_save: false,
        save_started: Notify::new(),
        release_save: Notify::new(),
        owners: Mutex::new(Vec::new()),
    });
    let mut engine = engine(port, auth);
    engine
        .dispatch(EngineCommand::list_saved_tracks(2), 1)
        .await;
    let saved = engine
        .dispatch(EngineCommand::load_next_saved_tracks_page(), 2)
        .await
        .snapshot;
    assert_eq!(
        saved
            .saved_tracks
            .iter()
            .map(|item| item.track.id.as_str())
            .collect::<Vec<_>>(),
        ["saved-1", "saved-2"]
    );
    assert!(saved.saved_tracks_next_page_token.is_none());
    engine
        .dispatch(EngineCommand::list_liked_tracks(2), 3)
        .await;
    let liked = engine
        .dispatch(EngineCommand::load_next_liked_tracks_page(), 4)
        .await
        .snapshot;
    assert_eq!(
        liked
            .liked_tracks
            .iter()
            .map(|item| item.track.id.as_str())
            .collect::<Vec<_>>(),
        ["liked-1", "liked-2"]
    );
}

#[tokio::test]
async fn save_success_is_acknowledged_and_failure_rolls_back_pending_state() {
    let auth = Arc::new(MutableAuth::authenticated());
    let success = Arc::new(RecordingLibraryPort {
        fail_save: false,
        block_save: false,
        save_started: Notify::new(),
        release_save: Notify::new(),
        owners: Mutex::new(Vec::new()),
    });
    let mut success_engine = engine(success, auth.clone());
    let saved = success_engine
        .dispatch(EngineCommand::save_track("track-1"), 1)
        .await
        .snapshot;
    assert!(saved.library_pending_track_ids.is_empty());
    assert_eq!(saved.saved_tracks[0].track.id, "track-1");

    let failure = Arc::new(RecordingLibraryPort {
        fail_save: true,
        block_save: false,
        save_started: Notify::new(),
        release_save: Notify::new(),
        owners: Mutex::new(Vec::new()),
    });
    let mut failure_engine = engine(failure, auth);
    let rolled_back = failure_engine
        .dispatch(EngineCommand::save_track("track-2"), 2)
        .await
        .snapshot;
    assert!(rolled_back.library_pending_track_ids.is_empty());
    assert!(rolled_back.saved_tracks.is_empty());
    assert_eq!(
        rolled_back.last_error.unwrap().error_type,
        EngineErrorType::ServiceUnavailable
    );
}

#[tokio::test]
async fn in_flight_library_result_is_rejected_after_exact_identity_changes() {
    let auth = Arc::new(MutableAuth::authenticated());
    let port = Arc::new(RecordingLibraryPort {
        fail_save: false,
        block_save: true,
        save_started: Notify::new(),
        release_save: Notify::new(),
        owners: Mutex::new(Vec::new()),
    });
    let mut engine = engine(port.clone(), auth.clone());
    let dispatch = engine.dispatch(EngineCommand::save_track("track-1"), 1);
    let replace = async {
        port.save_started.notified().await;
        auth.replace("account-2", "session-2");
        port.release_save.notify_one();
    };
    let (outcome, ()) = tokio::join!(dispatch, replace);
    assert!(outcome.snapshot.saved_tracks.is_empty());
    assert!(outcome.snapshot.library_pending_track_ids.is_empty());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::LoginRequired
    );
    assert_eq!(
        port.owners.lock().unwrap()[0],
        EngineLibraryIdentity::new("account-1", "session-1").unwrap()
    );
}

#[tokio::test]
async fn logout_masks_all_library_projections_immediately() {
    let auth = Arc::new(MutableAuth::authenticated());
    let port = Arc::new(RecordingLibraryPort {
        fail_save: false,
        block_save: false,
        save_started: Notify::new(),
        release_save: Notify::new(),
        owners: Mutex::new(Vec::new()),
    });
    let mut engine = engine(port, auth.clone());
    engine
        .dispatch(EngineCommand::list_saved_tracks(2), 1)
        .await;
    engine
        .dispatch(EngineCommand::list_liked_tracks(2), 2)
        .await;
    auth.logout();
    let masked: EngineSnapshot = engine.snapshot();
    assert!(masked.saved_tracks.is_empty());
    assert!(masked.liked_tracks.is_empty());
    assert!(masked.library_pending_track_ids.is_empty());
}
