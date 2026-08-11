use serde::{Deserialize, Serialize};

use crate::{EngineError, EngineErrorType, EnginePageRequest, EnginePagedResult, EngineTrack};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EnginePlaylistIdentity {
    pub account_id: String,
    pub session_id: String,
}

impl EnginePlaylistIdentity {
    pub fn new(
        account_id: impl Into<String>,
        session_id: impl Into<String>,
    ) -> Result<Self, EngineError> {
        let identity = Self {
            account_id: account_id.into(),
            session_id: session_id.into(),
        };
        if identity.account_id.trim().is_empty() || identity.session_id.trim().is_empty() {
            return Err(invalid_playlist_input(
                "playlist identity requires account and current session ids",
            ));
        }
        Ok(identity)
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EnginePlaylist {
    pub id: String,
    pub name: String,
    pub description: Option<String>,
    pub revision: u64,
    pub created_at_epoch_millis: u64,
    pub updated_at_epoch_millis: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineCreatePlaylist {
    pub name: String,
    pub description: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineUpdatePlaylist {
    pub id: String,
    pub name: String,
    pub description: Option<String>,
    pub expected_revision: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EnginePlaylistTrack {
    /// The stable item identity used for ordering. Canopy v1 currently exposes a track id here.
    pub membership_id: String,
    pub playlist_id: String,
    pub track: EngineTrack,
    pub position: u32,
    pub added_at_epoch_millis: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct PlaylistReconciliation {
    pub playlist_id: String,
    pub expected_revision: u64,
    pub server_revision: u64,
    pub server_membership_ids: Vec<String>,
    pub proposed_membership_ids: Vec<String>,
}

#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait PlaylistPort: Send + Sync {
    async fn create(
        &self,
        identity: &EnginePlaylistIdentity,
        input: EngineCreatePlaylist,
    ) -> Result<EnginePlaylist, EngineError>;
    async fn get(
        &self,
        identity: &EnginePlaylistIdentity,
        id: &str,
    ) -> Result<EnginePlaylist, EngineError>;
    async fn update(
        &self,
        identity: &EnginePlaylistIdentity,
        input: EngineUpdatePlaylist,
    ) -> Result<EnginePlaylist, EngineError>;
    async fn delete(&self, identity: &EnginePlaylistIdentity, id: &str) -> Result<(), EngineError>;
    async fn list(
        &self,
        identity: &EnginePlaylistIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylist>, EngineError>;
    async fn add_track(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        track_id: &str,
    ) -> Result<EnginePlaylistTrack, EngineError>;
    async fn remove_track(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        track_id: &str,
    ) -> Result<(), EngineError>;
    async fn reorder(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        ordered_membership_ids: &[String],
        expected_revision: u64,
    ) -> Result<EnginePlaylist, EngineError>;
    async fn list_tracks(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EnginePlaylistTrack>, EngineError>;
}

fn invalid_playlist_input(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}
