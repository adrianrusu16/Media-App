use std::sync::{Arc, Mutex};

use crate::{
    EngineError, EngineErrorType, EnginePageRequest, EnginePlaylist, EnginePlaylistIdentity,
    PlaylistPort, PlaylistReconciliation,
};

/// Refetches authoritative state on an optimistic-concurrency abort without retrying a mutation.
pub struct PlaylistReconciler {
    port: Arc<dyn PlaylistPort>,
    proposed: Mutex<Option<PlaylistReconciliation>>,
}

impl PlaylistReconciler {
    pub fn new(port: Arc<dyn PlaylistPort>) -> Self {
        Self {
            port,
            proposed: Mutex::new(None),
        }
    }

    pub fn proposed(&self) -> Option<PlaylistReconciliation> {
        self.proposed
            .lock()
            .expect("playlist reconciliation mutex poisoned")
            .clone()
    }

    pub async fn reorder(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
        ordered_membership_ids: Vec<String>,
        expected_revision: u64,
    ) -> Result<EnginePlaylist, EngineError> {
        validate_reorder(playlist_id, &ordered_membership_ids)?;
        match self
            .port
            .reorder(
                identity,
                playlist_id,
                &ordered_membership_ids,
                expected_revision,
            )
            .await
        {
            Ok(playlist) => {
                *self
                    .proposed
                    .lock()
                    .expect("playlist reconciliation mutex poisoned") = None;
                Ok(playlist)
            }
            Err(error) if error.error_type == EngineErrorType::Conflict => {
                let playlist = self.port.get(identity, playlist_id).await?;
                let server_membership_ids =
                    self.list_all_membership_ids(identity, playlist_id).await?;
                *self
                    .proposed
                    .lock()
                    .expect("playlist reconciliation mutex poisoned") =
                    Some(PlaylistReconciliation {
                        playlist_id: playlist_id.to_owned(),
                        expected_revision,
                        server_revision: playlist.revision,
                        server_membership_ids,
                        proposed_membership_ids: ordered_membership_ids,
                    });
                Err(error)
            }
            Err(error) => Err(error),
        }
    }

    async fn list_all_membership_ids(
        &self,
        identity: &EnginePlaylistIdentity,
        playlist_id: &str,
    ) -> Result<Vec<String>, EngineError> {
        let mut request = EnginePageRequest {
            page_size: 100,
            page_token: None,
        };
        let mut membership_ids = Vec::new();
        loop {
            let page = self
                .port
                .list_tracks(identity, playlist_id, request.clone())
                .await?;
            membership_ids.extend(page.items.into_iter().map(|item| item.membership_id));
            match page.next_page_token {
                Some(token) => request.page_token = Some(token),
                None => return Ok(membership_ids),
            }
        }
    }
}

fn validate_reorder(
    playlist_id: &str,
    ordered_membership_ids: &[String],
) -> Result<(), EngineError> {
    if playlist_id.trim().is_empty()
        || ordered_membership_ids.is_empty()
        || ordered_membership_ids.iter().any(|id| id.trim().is_empty())
    {
        return Err(EngineError::new(
            EngineErrorType::InvalidInput,
            "playlist reorder requires a complete non-empty membership order",
            false,
        ));
    }
    let mut unique = std::collections::HashSet::new();
    if !ordered_membership_ids.iter().all(|id| unique.insert(id)) {
        return Err(EngineError::new(
            EngineErrorType::InvalidInput,
            "playlist reorder membership ids must be unique",
            false,
        ));
    }
    Ok(())
}
