use serde::{Deserialize, Serialize};

use crate::{
    EngineAlbum, EngineArtist, EngineError, EngineErrorType, EnginePageRequest, EnginePagedResult,
    EngineTrack,
};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineLibraryIdentity {
    pub account_id: String,
    pub session_id: String,
}

impl EngineLibraryIdentity {
    pub fn new(
        account_id: impl Into<String>,
        session_id: impl Into<String>,
    ) -> Result<Self, EngineError> {
        let identity = Self {
            account_id: account_id.into(),
            session_id: session_id.into(),
        };
        if identity.account_id.trim().is_empty() || identity.session_id.trim().is_empty() {
            return Err(invalid_library_input(
                "library identity requires account and current session ids",
            ));
        }
        Ok(identity)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum EngineLibraryRelationshipKind {
    Saved,
    Liked,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineLibraryTrack {
    pub relationship_id: String,
    pub track: EngineTrack,
    pub relationship_at_epoch_millis: u64,
}

impl EngineLibraryTrack {
    pub fn new(
        _kind: EngineLibraryRelationshipKind,
        track_id: impl Into<String>,
        title: impl Into<String>,
        artist_id: impl Into<String>,
        artist_name: impl Into<String>,
        relationship_at_epoch_millis: u64,
    ) -> Result<Self, EngineError> {
        let track_id = track_id.into();
        if track_id.trim().is_empty() {
            return Err(invalid_library_input(
                "library relationship track id is required",
            ));
        }
        Ok(Self {
            relationship_id: track_id.clone(),
            track: EngineTrack {
                id: track_id,
                title: title.into(),
                artist: EngineArtist {
                    id: artist_id.into(),
                    name: artist_name.into(),
                },
                album: None::<EngineAlbum>,
                duration_millis: 0,
                explicit: false,
                artwork: None,
                genres: Vec::new(),
            },
            relationship_at_epoch_millis,
        })
    }
}

fn invalid_library_input(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}

#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait LibraryPort: Send + Sync {
    async fn save(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError>;
    async fn remove_saved(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<(), EngineError>;
    async fn list_saved(
        &self,
        identity: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError>;
    async fn like(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<EngineLibraryTrack, EngineError>;
    async fn unlike(
        &self,
        identity: &EngineLibraryIdentity,
        track_id: &str,
    ) -> Result<(), EngineError>;
    async fn list_liked(
        &self,
        identity: &EngineLibraryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineLibraryTrack>, EngineError>;
}
