use std::sync::{Arc, Mutex};

use panda_engine_core::{
    EngineArtist, EngineCreatePlaylist, EngineError, EngineErrorType, EnginePageRequest,
    EnginePagedResult, EnginePlaylist, EnginePlaylistIdentity, EnginePlaylistTrack, EngineTrack,
    EngineUpdatePlaylist, PlaylistPort, PlaylistReconciler,
};

#[tokio::test]
async fn playlist_aborted_reorder_refetches_before_proposing_reconciliation() {
    let port = playlist_port_aborts_then_returns_revision(8, vec!["a", "b", "c"]);
    let identity = EnginePlaylistIdentity::new("account", "session").unwrap();

    let result = PlaylistReconciler::new(port.clone())
        .reorder(&identity, "p1", vec!["c".into(), "a".into(), "b".into()], 7)
        .await;

    assert!(matches!(
        result,
        Err(EngineError {
            error_type: EngineErrorType::Conflict,
            ..
        })
    ));
    assert_eq!(port.list_calls(), 1);
    assert_eq!(port.reorder_calls(), 1);
}

fn playlist_port_aborts_then_returns_revision(
    revision: u64,
    memberships: Vec<&str>,
) -> Arc<RecordingPlaylistPort> {
    Arc::new(RecordingPlaylistPort {
        playlist: EnginePlaylist {
            id: "p1".into(),
            name: "Road trip".into(),
            description: None,
            revision,
            created_at_epoch_millis: 1,
            updated_at_epoch_millis: 2,
        },
        memberships: memberships.into_iter().map(str::to_owned).collect(),
        reorder_calls: Mutex::new(0),
        list_calls: Mutex::new(0),
    })
}

struct RecordingPlaylistPort {
    playlist: EnginePlaylist,
    memberships: Vec<String>,
    reorder_calls: Mutex<u32>,
    list_calls: Mutex<u32>,
}

impl RecordingPlaylistPort {
    fn reorder_calls(&self) -> u32 {
        *self.reorder_calls.lock().unwrap()
    }
    fn list_calls(&self) -> u32 {
        *self.list_calls.lock().unwrap()
    }
}

#[async_trait::async_trait]
impl PlaylistPort for RecordingPlaylistPort {
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
        _: &str,
    ) -> Result<EnginePlaylist, EngineError> {
        Ok(self.playlist.clone())
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
        _: &str,
        _: &[String],
        _: u64,
    ) -> Result<EnginePlaylist, EngineError> {
        *self.reorder_calls.lock().unwrap() += 1;
        Err(EngineError::new(
            EngineErrorType::Conflict,
            "conflict",
            false,
        ))
    }
    async fn list_tracks(
        &self,
        _: &EnginePlaylistIdentity,
        playlist_id: &str,
        _: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError> {
        *self.list_calls.lock().unwrap() += 1;
        Ok(EnginePagedResult {
            items: self
                .memberships
                .iter()
                .enumerate()
                .map(|(position, membership_id)| EnginePlaylistTrack {
                    membership_id: membership_id.clone(),
                    playlist_id: playlist_id.into(),
                    track: EngineTrack {
                        id: membership_id.clone(),
                        title: "Track".into(),
                        artist: EngineArtist {
                            id: "artist".into(),
                            name: "Artist".into(),
                        },
                        album: None,
                        duration_millis: 0,
                        explicit: false,
                        artwork_id: None,
                        genres: vec![],
                    },
                    position: position as u32,
                    added_at_epoch_millis: 1,
                })
                .collect(),
            next_page_token: None,
        })
    }
}
