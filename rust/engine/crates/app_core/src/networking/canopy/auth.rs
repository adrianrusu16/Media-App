use std::time::Duration;

use tonic_014::metadata::MetadataValue;
use tonic_014::transport::Channel;
use tonic_014::{Code, Request, Status};

use crate::{Account, AuthPort, AuthSession, AuthSessionEnvelope, EngineError, EngineErrorType};

use super::CanopyChannel;
use super::error::map_status;
use super::sdk::{
    clients::auth_service_client::AuthServiceClient,
    resources::{
        AccountSummary, LoginPasswordRequest, LogoutRequest, RefreshSessionRequest,
        SessionEnvelope, SessionSummary,
    },
    well_known_types::Timestamp,
};

const AUTH_TIMEOUT: Duration = Duration::from_secs(5);

/// Canonical Canopy authentication transport adapter.
pub struct CanopyAuthClient {
    client: AuthServiceClient<Channel>,
}

impl CanopyAuthClient {
    pub fn new(channel: &CanopyChannel) -> Self {
        Self {
            client: AuthServiceClient::new(channel.clone_inner()),
        }
    }
}

#[async_trait::async_trait]
impl AuthPort for CanopyAuthClient {
    async fn login_password(
        &self,
        email: &str,
        password: &str,
        device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        let mut client = self.client.clone();
        let response = client
            .login_password(login_request(email, password, device_label))
            .await
            .map_err(map_status)?
            .into_inner();
        map_session_envelope(response)
    }

    async fn refresh_session(
        &self,
        refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        let mut client = self.client.clone();
        let response = client
            .refresh_session(refresh_request(refresh_token))
            .await
            .map_err(map_status)?
            .into_inner();
        map_session_envelope(response)
    }

    async fn logout(&self, access_token: &str) -> Result<(), EngineError> {
        let mut client = self.client.clone();
        client
            .logout(logout_request(access_token)?)
            .await
            .map_err(map_protected_status)?;
        Ok(())
    }
}

fn login_request(email: &str, password: &str, device_label: &str) -> Request<LoginPasswordRequest> {
    request_with_timeout(LoginPasswordRequest {
        email: email.to_owned(),
        password: password.to_owned(),
        device_label: device_label.to_owned(),
    })
}

fn refresh_request(refresh_token: &str) -> Request<RefreshSessionRequest> {
    request_with_timeout(RefreshSessionRequest {
        refresh_token: refresh_token.to_owned(),
    })
}

fn logout_request(access_token: &str) -> Result<Request<LogoutRequest>, EngineError> {
    let mut request = request_with_timeout(LogoutRequest {});
    let value = MetadataValue::try_from(format!("Bearer {access_token}"))
        .map_err(|_| invalid_authorization_metadata())?;
    request.metadata_mut().insert("authorization", value);
    Ok(request)
}

fn request_with_timeout<T>(message: T) -> Request<T> {
    let mut request = Request::new(message);
    request.set_timeout(AUTH_TIMEOUT);
    request
}

fn map_session_envelope(wire: SessionEnvelope) -> Result<AuthSessionEnvelope, EngineError> {
    if wire.access_token.is_empty() || wire.refresh_token.is_empty() {
        return Err(mapping_defect());
    }
    let access_expiry =
        u64::try_from(wire.access_expires_at_epoch_ms).map_err(|_| mapping_defect())?;
    let refresh_expiry =
        u64::try_from(wire.refresh_expires_at_epoch_ms).map_err(|_| mapping_defect())?;
    let account = map_account(wire.account.ok_or_else(mapping_defect)?)?;
    let session = map_session(wire.session.ok_or_else(mapping_defect)?)?;

    Ok(AuthSessionEnvelope::new(
        wire.access_token,
        access_expiry,
        wire.refresh_token,
        refresh_expiry,
        account,
        session,
    ))
}

fn map_account(wire: AccountSummary) -> Result<Account, EngineError> {
    if wire.id.is_empty() || wire.primary_email.is_empty() {
        return Err(mapping_defect());
    }
    Ok(Account {
        id: wire.id,
        primary_email: wire.primary_email,
        status: wire.status,
        created_at_epoch_millis: timestamp_to_epoch_millis(
            wire.created_at.ok_or_else(mapping_defect)?,
        )?,
    })
}

fn map_session(wire: SessionSummary) -> Result<AuthSession, EngineError> {
    if wire.id.is_empty() {
        return Err(mapping_defect());
    }
    Ok(AuthSession {
        id: wire.id,
        device_label: wire.device_label,
        created_at_epoch_millis: timestamp_to_epoch_millis(
            wire.created_at.ok_or_else(mapping_defect)?,
        )?,
        last_used_at_epoch_millis: timestamp_to_epoch_millis(
            wire.last_used_at.ok_or_else(mapping_defect)?,
        )?,
        expires_at_epoch_millis: timestamp_to_epoch_millis(
            wire.expires_at.ok_or_else(mapping_defect)?,
        )?,
        current: wire.current,
    })
}

fn timestamp_to_epoch_millis(timestamp: Timestamp) -> Result<u64, EngineError> {
    if !(0..=PROTOBUF_TIMESTAMP_MAX_SECONDS).contains(&timestamp.seconds)
        || !(0..1_000_000_000).contains(&timestamp.nanos)
    {
        return Err(mapping_defect());
    }
    let seconds = u64::try_from(timestamp.seconds).map_err(|_| mapping_defect())?;
    seconds
        .checked_mul(1_000)
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(mapping_defect)
}

const PROTOBUF_TIMESTAMP_MAX_SECONDS: i64 = 253_402_300_799;

fn map_protected_status(status: Status) -> EngineError {
    if status.code() == Code::Unauthenticated {
        EngineError::new(
            EngineErrorType::AuthExpired,
            "backend access credential expired",
            false,
        )
    } else {
        map_status(status)
    }
}

fn mapping_defect() -> EngineError {
    EngineError::new(
        EngineErrorType::MappingDefect,
        "invalid canonical Canopy session response",
        false,
    )
}

fn invalid_authorization_metadata() -> EngineError {
    EngineError::new(
        EngineErrorType::InvalidInput,
        "access credential cannot be encoded as authorization metadata",
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::{
        login_request, logout_request, map_protected_status, map_session_envelope, refresh_request,
    };
    use crate::EngineErrorType;
    use crate::networking::canopy::sdk::resources::{
        AccountSummary, SessionEnvelope, SessionSummary,
    };
    use crate::networking::canopy::sdk::well_known_types::Timestamp;

    fn canonical_envelope() -> SessionEnvelope {
        SessionEnvelope {
            access_token: "access-secret".into(),
            refresh_token: "refresh-secret".into(),
            access_expires_at_epoch_ms: 2_000,
            refresh_expires_at_epoch_ms: 3_000,
            account: Some(AccountSummary {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at: Some(Timestamp {
                    seconds: 1,
                    nanos: 500_000_000,
                }),
            }),
            session: Some(SessionSummary {
                id: "session-1".into(),
                device_label: "car".into(),
                created_at: Some(Timestamp {
                    seconds: 1,
                    nanos: 600_000_000,
                }),
                last_used_at: Some(Timestamp {
                    seconds: 1,
                    nanos: 700_000_000,
                }),
                expires_at: Some(Timestamp {
                    seconds: 3,
                    nanos: 0,
                }),
                current: true,
            }),
        }
    }

    #[test]
    fn canonical_session_envelope_maps_to_service_neutral_aggregate() {
        let mapped = map_session_envelope(canonical_envelope()).unwrap();
        let credentials = mapped.credentials();

        assert_eq!(credentials.access_token, "access-secret");
        assert_eq!(credentials.refresh_token, "refresh-secret");
        assert_eq!(credentials.access_token_expires_at_epoch_millis, 2_000);
        assert_eq!(credentials.refresh_token_expires_at_epoch_millis, 3_000);
        let state = mapped.state();
        let rendered = format!("{state:?}");
        assert!(rendered.contains("driver@example.com"));
        assert!(!rendered.contains("access-secret"));
        assert!(!rendered.contains("refresh-secret"));
    }

    #[test]
    fn required_nested_resources_are_rejected_when_missing() {
        let mut missing_account = canonical_envelope();
        missing_account.account = None;
        let mut missing_session = canonical_envelope();
        missing_session.session = None;

        assert_eq!(
            map_session_envelope(missing_account)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
        assert_eq!(
            map_session_envelope(missing_session)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn negative_epoch_millis_and_timestamps_are_mapping_defects() {
        let mut negative_expiry = canonical_envelope();
        negative_expiry.access_expires_at_epoch_ms = -1;
        let mut negative_timestamp = canonical_envelope();
        negative_timestamp.account.as_mut().unwrap().created_at = Some(Timestamp {
            seconds: -1,
            nanos: 0,
        });

        assert_eq!(
            map_session_envelope(negative_expiry)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
        assert_eq!(
            map_session_envelope(negative_timestamp)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn timestamp_above_the_protobuf_maximum_is_a_mapping_defect() {
        let mut invalid = canonical_envelope();
        invalid.account.as_mut().unwrap().created_at = Some(Timestamp {
            seconds: 253_402_300_800,
            nanos: 0,
        });

        assert_eq!(
            map_session_envelope(invalid).unwrap_err().error_type,
            EngineErrorType::MappingDefect
        );
    }

    #[test]
    fn empty_required_session_identity_and_credentials_are_mapping_defects() {
        let mut empty_access = canonical_envelope();
        empty_access.access_token.clear();
        let mut empty_refresh = canonical_envelope();
        empty_refresh.refresh_token.clear();
        let mut empty_account_id = canonical_envelope();
        empty_account_id.account.as_mut().unwrap().id.clear();
        let mut empty_session_id = canonical_envelope();
        empty_session_id.session.as_mut().unwrap().id.clear();

        for invalid in [
            empty_access,
            empty_refresh,
            empty_account_id,
            empty_session_id,
        ] {
            assert_eq!(
                map_session_envelope(invalid).unwrap_err().error_type,
                EngineErrorType::MappingDefect
            );
        }
    }

    #[test]
    fn auth_metadata_is_added_only_to_the_protected_logout_request() {
        let login = login_request("driver@example.com", "password", "car");
        let refresh = refresh_request("refresh-secret");
        let logout = logout_request("access-secret").unwrap();

        assert!(login.metadata().get("authorization").is_none());
        assert!(refresh.metadata().get("authorization").is_none());
        assert_eq!(
            logout
                .metadata()
                .get("authorization")
                .unwrap()
                .to_str()
                .unwrap(),
            "Bearer access-secret"
        );
    }

    #[test]
    fn invalid_authorization_metadata_is_typed_without_echoing_the_token() {
        let token = "secret\nheader";
        let error = logout_request(token).unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::InvalidInput);
        assert!(!error.message.contains(token));
        assert!(!error.message.contains("secret"));
    }

    #[test]
    fn unauthenticated_protected_call_maps_to_auth_expired() {
        let error = map_protected_status(tonic_014::Status::unauthenticated("ignored"));

        assert_eq!(error.error_type, EngineErrorType::AuthExpired);
        assert!(!error.message.contains("ignored"));
    }
}
