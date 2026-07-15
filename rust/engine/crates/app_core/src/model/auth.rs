use serde::{Deserialize, Serialize};

/// A service-neutral authenticated account resource.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct Account {
    pub id: String,
    pub primary_email: String,
    /// Backend-defined lifecycle status preserved without interpretation.
    pub status: String,
    pub created_at_epoch_millis: u64,
}

/// A service-neutral authentication session summary.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct AuthSession {
    pub id: String,
    pub device_label: String,
    pub created_at_epoch_millis: u64,
    pub last_used_at_epoch_millis: u64,
    pub expires_at_epoch_millis: u64,
    pub current: bool,
}

/// The complete credential rotation unit owned by the engine.
///
/// All fields are intentionally private so callers cannot update individual
/// token fields. The envelope is replaced atomically through `SessionStore`.
#[derive(Clone, Eq, PartialEq, Serialize, Deserialize)]
pub struct AuthSessionEnvelope {
    access_token: String,
    access_token_expires_at_epoch_millis: u64,
    refresh_token: String,
    refresh_token_expires_at_epoch_millis: u64,
    account: Account,
    session: AuthSession,
}

impl AuthSessionEnvelope {
    pub fn new(
        access_token: String,
        access_token_expires_at_epoch_millis: u64,
        refresh_token: String,
        refresh_token_expires_at_epoch_millis: u64,
        account: Account,
        session: AuthSession,
    ) -> Self {
        Self {
            access_token,
            access_token_expires_at_epoch_millis,
            refresh_token,
            refresh_token_expires_at_epoch_millis,
            account,
            session,
        }
    }

    /// Projects the credential-bearing aggregate into safe application state.
    pub fn state(&self) -> AuthState {
        AuthState::Authenticated {
            account: self.account.clone(),
            session: self.session.clone(),
        }
    }
}

impl std::fmt::Debug for AuthSessionEnvelope {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("AuthSessionEnvelope")
            .field("access_token", &"[REDACTED]")
            .field(
                "access_token_expires_at_epoch_millis",
                &self.access_token_expires_at_epoch_millis,
            )
            .field("refresh_token", &"[REDACTED]")
            .field(
                "refresh_token_expires_at_epoch_millis",
                &self.refresh_token_expires_at_epoch_millis,
            )
            .field("account", &self.account)
            .field("session", &self.session)
            .finish()
    }
}

/// Credential-free authentication state suitable for snapshots and UI-facing projection.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub enum AuthState {
    Anonymous,
    Authenticated {
        account: Account,
        session: AuthSession,
    },
    LoginRequired,
}
