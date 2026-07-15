use crate::{EngineError, EnginePageRequest, EnginePagedResult, EngineTrack};

/// Backend-neutral catalog boundary used by engine data consumers.
#[async_trait::async_trait]
pub trait CatalogPort: Send + Sync {
    async fn browse(
        &self,
        parent_id: Option<&str>,
        genres: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError>;

    async fn search(
        &self,
        query: &str,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError>;

    async fn get_media(&self, track_id: &str) -> Result<EngineTrack, EngineError>;
}
