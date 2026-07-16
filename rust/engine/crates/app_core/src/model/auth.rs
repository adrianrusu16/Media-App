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

/// Service-neutral acknowledgement for enumeration-safe auth bootstrap requests.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct AuthRequestAcceptance {
    accepted: bool,
}

impl AuthRequestAcceptance {
    pub fn new(accepted: bool) -> Self {
        Self { accepted }
    }

    pub fn accepted() -> Self {
        Self::new(true)
    }

    pub fn is_accepted(self) -> bool {
        self.accepted
    }
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

    pub(crate) fn to_storage_bytes(&self) -> Result<Vec<u8>, serde_json::Error> {
        serde_json::to_vec(&StoredAuthSessionEnvelope::from(self))
    }

    pub(crate) fn from_storage_bytes(bytes: &[u8]) -> Result<Self, String> {
        let stored: StoredAuthSessionEnvelope =
            serde_json::from_slice(bytes).map_err(|_| "stored session payload is malformed")?;
        stored.try_into()
    }
}

#[derive(Serialize, Deserialize)]
struct StoredAuthSessionEnvelope {
    codec_version: u8,
    access_token: String,
    access_token_expires_at_epoch_millis: u64,
    refresh_token: String,
    refresh_token_expires_at_epoch_millis: u64,
    account: Account,
    session: AuthSession,
}

impl From<&AuthSessionEnvelope> for StoredAuthSessionEnvelope {
    fn from(envelope: &AuthSessionEnvelope) -> Self {
        Self {
            codec_version: 1,
            access_token: envelope.access_token.clone(),
            access_token_expires_at_epoch_millis: envelope.access_token_expires_at_epoch_millis,
            refresh_token: envelope.refresh_token.clone(),
            refresh_token_expires_at_epoch_millis: envelope.refresh_token_expires_at_epoch_millis,
            account: envelope.account.clone(),
            session: envelope.session.clone(),
        }
    }
}

impl TryFrom<StoredAuthSessionEnvelope> for AuthSessionEnvelope {
    type Error = String;

    fn try_from(stored: StoredAuthSessionEnvelope) -> Result<Self, Self::Error> {
        if stored.codec_version != 1 {
            return Err("stored session codec version is unsupported".into());
        }
        if stored.access_token.is_empty()
            || stored.refresh_token.is_empty()
            || stored.account.id.is_empty()
            || stored.account.primary_email.is_empty()
            || stored.session.id.is_empty()
            || stored.session.device_label.is_empty()
            || stored.access_token_expires_at_epoch_millis == 0
            || stored.refresh_token_expires_at_epoch_millis == 0
        {
            return Err("stored session payload is incomplete".into());
        }
        Ok(Self::new(
            stored.access_token,
            stored.access_token_expires_at_epoch_millis,
            stored.refresh_token,
            stored.refresh_token_expires_at_epoch_millis,
            stored.account,
            stored.session,
        ))
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
