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
#[derive(Clone, Eq, PartialEq)]
pub struct AuthSessionEnvelope {
    access_token: String,
    access_token_expires_at_epoch_millis: u64,
    refresh_token: String,
    refresh_token_expires_at_epoch_millis: u64,
    account: Account,
    session: AuthSession,
}

/// Borrowed credentials for transport adapters inside this crate.
#[allow(dead_code)]
pub(crate) struct AuthSessionCredentials<'a> {
    pub(crate) access_token: &'a str,
    pub(crate) access_token_expires_at_epoch_millis: u64,
    pub(crate) refresh_token: &'a str,
    pub(crate) refresh_token_expires_at_epoch_millis: u64,
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

    /// Exposes credentials only to engine-internal transport composition.
    #[allow(dead_code)]
    pub(crate) fn credentials(&self) -> AuthSessionCredentials<'_> {
        AuthSessionCredentials {
            access_token: &self.access_token,
            access_token_expires_at_epoch_millis: self.access_token_expires_at_epoch_millis,
            refresh_token: &self.refresh_token,
            refresh_token_expires_at_epoch_millis: self.refresh_token_expires_at_epoch_millis,
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
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
pub enum AuthState {
    #[default]
    Anonymous,
    Authenticated {
        account: Account,
        session: AuthSession,
    },
    LoginRequired,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn envelope() -> AuthSessionEnvelope {
        AuthSessionEnvelope::new(
            "access-secret".into(),
            2_000,
            "refresh-secret".into(),
            3_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 500,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "car".into(),
                created_at_epoch_millis: 1_000,
                last_used_at_epoch_millis: 1_100,
                expires_at_epoch_millis: 4_000,
                current: true,
            },
        )
    }

    #[test]
    fn crate_internal_credentials_include_both_tokens_and_expiries() {
        let envelope = envelope();
        let credentials = envelope.credentials();

        assert_eq!(credentials.access_token, "access-secret");
        assert_eq!(credentials.access_token_expires_at_epoch_millis, 2_000);
        assert_eq!(credentials.refresh_token, "refresh-secret");
        assert_eq!(credentials.refresh_token_expires_at_epoch_millis, 3_000);
    }
}
