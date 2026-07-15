use crate::{AuthSessionEnvelope, EngineError};

/// Service-neutral backend boundary for authentication operations.
#[async_trait::async_trait]
pub trait AuthPort: Send + Sync {
    async fn login_password(
        &self,
        email: &str,
        password: &str,
        device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError>;

    async fn refresh_session(
        &self,
        refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError>;

    async fn logout(&self, access_token: &str) -> Result<(), EngineError>;
}
