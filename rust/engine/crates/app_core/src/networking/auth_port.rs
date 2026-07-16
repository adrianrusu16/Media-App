use crate::{AuthRequestAcceptance, AuthSessionEnvelope, EngineError, EngineErrorType};

/// Service-neutral backend boundary for authentication operations.
#[async_trait::async_trait]
pub trait AuthPort: Send + Sync {
    async fn register_password(
        &self,
        _email: &str,
        _password: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        Err(bootstrap_unavailable())
    }

    async fn resend_verification(
        &self,
        _email: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        Err(bootstrap_unavailable())
    }

    async fn verify_email(
        &self,
        _verification_token: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Err(bootstrap_unavailable())
    }

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

fn bootstrap_unavailable() -> EngineError {
    EngineError::new(
        EngineErrorType::BackendFault,
        "authentication bootstrap operation unavailable",
        false,
    )
}
