use async_trait::async_trait;

use crate::{EngineBackendStatus, EngineError};

/// Backend-neutral port for public service health.
#[async_trait]
pub trait SystemPort: Send + Sync {
    async fn get_status(&self) -> Result<EngineBackendStatus, EngineError>;
}
