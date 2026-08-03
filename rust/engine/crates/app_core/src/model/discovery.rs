use crate::{EngineError, EnginePageRequest, EnginePagedResult, EngineTrack};

/// Backend-neutral boundary for the authenticated Canopy discovery feed.
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait DiscoveryPort: Send + Sync {
    async fn get_feed(
        &self,
        excluded_track_ids: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError>;
}
