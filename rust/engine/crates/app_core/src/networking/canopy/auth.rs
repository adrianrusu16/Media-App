use std::sync::Arc;
use std::time::Duration;

use tonic_014::metadata::{Ascii, MetadataValue};
use tonic_014::transport::Channel;
use tonic_014::{Request, Status};

use crate::{
    Account, AccountPort, AuthPort, AuthRequestAcceptance, AuthSession, AuthSessionEnvelope,
    EngineAccountIdentity, EngineError, EngineErrorType, EngineHistoryIdentity, EnginePageRequest,
    EnginePageToken, EnginePagedResult,
};

use super::catalog::map_page_request;
use super::operation::CanopyOperation;
use super::request::{execute, execute_with_bound_auth};
use super::sdk::{
    clients::auth_service_client::AuthServiceClient,
    resources::{
        AccountSummary, DeleteAccountRequest, GetAccountRequest, ListSessionsRequest,
        ListSessionsResponse, LoginPasswordRequest, LogoutRequest, RefreshSessionRequest,
        RegisterPasswordRequest, ResendVerificationRequest, RevokeSessionRequest, SessionEnvelope,
        SessionSummary, VerifyEmailRequest,
    },
    well_known_types::Timestamp,
};
use super::{CanopyChannel, SessionCoordinator};

const AUTH_TIMEOUT: Duration = Duration::from_secs(5);

/// Canonical Canopy authentication transport adapter.
pub struct CanopyAuthClient {
    client: AuthServiceClient<Channel>,
    session: Option<Arc<SessionCoordinator>>,
}

impl CanopyAuthClient {
    pub fn new(channel: &CanopyChannel) -> Self {
        Self {
            client: AuthServiceClient::new(channel.clone_inner()),
            session: None,
        }
    }

    pub fn new_protected(channel: &CanopyChannel, session: Arc<SessionCoordinator>) -> Self {
        Self {
            client: AuthServiceClient::new(channel.clone_inner()),
            session: Some(session),
        }
    }
}

#[async_trait::async_trait]
impl AccountPort for CanopyAuthClient {
    async fn get_account(&self, identity: &EngineAccountIdentity) -> Result<Account, EngineError> {
        let response = self
            .execute_account(
                identity,
                CanopyOperation::GetAccount,
                || Request::new(GetAccountRequest {}),
                |mut client, request| async move { client.get_account(request).await },
            )
            .await?
            .into_inner();
        map_account(response.account.ok_or_else(mapping_defect)?)
    }

    async fn list_sessions(
        &self,
        identity: &EngineAccountIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<AuthSession>, EngineError> {
        let request = ListSessionsRequest {
            page: Some(map_page_request(page)),
        };
        let response = self
            .execute_account(
                identity,
                CanopyOperation::ListSessions,
                || Request::new(request.clone()),
                |mut client, request| async move { client.list_sessions(request).await },
            )
            .await?
            .into_inner();
        map_session_page(response)
    }

    async fn revoke_session(
        &self,
        identity: &EngineAccountIdentity,
        session_id: &str,
    ) -> Result<(), EngineError> {
        if session_id.trim().is_empty() {
            return Err(EngineError::new(
                EngineErrorType::InvalidInput,
                "session id is required",
                false,
            ));
        }
        let request = RevokeSessionRequest {
            session_id: session_id.into(),
        };
        self.execute_account(
            identity,
            CanopyOperation::RevokeSession,
            || Request::new(request.clone()),
            |mut client, request| async move { client.revoke_session(request).await },
        )
        .await?;
        Ok(())
    }

    async fn delete_account(&self, identity: &EngineAccountIdentity) -> Result<(), EngineError> {
        self.execute_account(
            identity,
            CanopyOperation::DeleteAccount,
            || Request::new(DeleteAccountRequest {}),
            |mut client, request| async move { client.delete_account(request).await },
        )
        .await?;
        let session = self
            .session
            .as_ref()
            .ok_or_else(protected_client_unavailable)?;
        session
            .clear_after_account_delete(&bound_identity(identity))
            .await
    }
}

impl CanopyAuthClient {
    async fn execute_account<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
        &self,
        identity: &EngineAccountIdentity,
        operation: CanopyOperation,
        make_request: MakeRequest,
        execute: Execute,
    ) -> Result<tonic_014::Response<TResponse>, EngineError>
    where
        MakeRequest: Fn() -> Request<TRequest>,
        Execute: Fn(AuthServiceClient<Channel>, Request<TRequest>) -> ExecuteFuture,
        ExecuteFuture: std::future::Future<Output = Result<tonic_014::Response<TResponse>, Status>>,
    {
        let session = self
            .session
            .as_ref()
            .ok_or_else(protected_client_unavailable)?;
        let client = self.client.clone();
        execute_with_bound_auth(
            session,
            &bound_identity(identity),
            operation,
            make_request,
            move |request| execute(client.clone(), request),
        )
        .await
    }
}

fn bound_identity(identity: &EngineAccountIdentity) -> EngineHistoryIdentity {
    EngineHistoryIdentity {
        account_id: identity.account_id.clone(),
        session_id: identity.session_id.clone(),
    }
}

fn protected_client_unavailable() -> EngineError {
    EngineError::new(
        EngineErrorType::FailedPrecondition,
        "protected account service is not configured",
        false,
    )
}

#[async_trait::async_trait]
impl AuthPort for CanopyAuthClient {
    async fn register_password(
        &self,
        email: &str,
        password: &str,
    ) -> Result<AuthRequestAcceptance, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::RegisterPassword,
            || register_request(email, password),
            move |request| {
                let mut client = client.clone();
                async move { client.register_password(request).await }
            },
        )
        .await?
        .into_inner();
        Ok(AuthRequestAcceptance::new(response.accepted))
    }

    async fn resend_verification(&self, email: &str) -> Result<AuthRequestAcceptance, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::ResendVerification,
            || resend_request(email),
            move |request| {
                let mut client = client.clone();
                async move { client.resend_verification(request).await }
            },
        )
        .await?
        .into_inner();
        Ok(AuthRequestAcceptance::new(response.accepted))
    }

    async fn verify_email(
        &self,
        verification_token: &str,
        device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::VerifyEmail,
            || verify_request(verification_token, device_label),
            move |request| {
                let mut client = client.clone();
                async move { client.verify_email(request).await }
            },
        )
        .await?
        .into_inner();
        map_session_envelope(response)
    }

    async fn login_password(
        &self,
        email: &str,
        password: &str,
        device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::LoginPassword,
            || login_request(email, password, device_label),
            move |request| {
                let mut client = client.clone();
                async move { client.login_password(request).await }
            },
        )
        .await?
        .into_inner();
        map_session_envelope(response)
    }

    async fn refresh_session(
        &self,
        refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        let client = self.client.clone();
        let response = execute(
            None,
            CanopyOperation::RefreshSession,
            || refresh_request(refresh_token),
            move |request| {
                let mut client = client.clone();
                async move { client.refresh_session(request).await }
            },
        )
        .await?
        .into_inner();
        map_session_envelope(response)
    }

    async fn logout(&self, access_token: &str) -> Result<(), EngineError> {
        let authorization = authorization_metadata(access_token)?;
        let client = self.client.clone();
        execute(
            None,
            CanopyOperation::Logout,
            || logout_request_with_authorization(authorization.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.logout(request).await }
            },
        )
        .await?;
        Ok(())
    }
}

fn register_request(email: &str, password: &str) -> Request<RegisterPasswordRequest> {
    request_with_timeout(RegisterPasswordRequest {
        email: email.to_owned(),
        password: password.to_owned(),
    })
}

fn resend_request(email: &str) -> Request<ResendVerificationRequest> {
    request_with_timeout(ResendVerificationRequest {
        email: email.to_owned(),
    })
}

fn verify_request(verification_token: &str, device_label: &str) -> Request<VerifyEmailRequest> {
    request_with_timeout(VerifyEmailRequest {
        verification_token: verification_token.to_owned(),
        device_label: device_label.to_owned(),
    })
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

#[cfg(test)]
fn logout_request(access_token: &str) -> Result<Request<LogoutRequest>, EngineError> {
    Ok(logout_request_with_authorization(authorization_metadata(
        access_token,
    )?))
}

fn authorization_metadata(access_token: &str) -> Result<MetadataValue<Ascii>, EngineError> {
    MetadataValue::try_from(format!("Bearer {access_token}"))
        .map_err(|_| invalid_authorization_metadata())
}

fn logout_request_with_authorization(
    authorization: MetadataValue<Ascii>,
) -> Request<LogoutRequest> {
    let mut request = request_with_timeout(LogoutRequest {});
    request
        .metadata_mut()
        .insert("authorization", authorization);
    request
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

fn map_session_page(
    wire: ListSessionsResponse,
) -> Result<EnginePagedResult<AuthSession>, EngineError> {
    Ok(EnginePagedResult {
        items: wire
            .sessions
            .into_iter()
            .map(map_session)
            .collect::<Result<_, _>>()?,
        next_page_token: match wire.page_info.map(|page| page.next_page_token) {
            Some(token) if !token.is_empty() => {
                Some(EnginePageToken::new(token).map_err(|_| mapping_defect())?)
            }
            _ => None,
        },
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
        login_request, logout_request, map_session_envelope, refresh_request, register_request,
        resend_request, verify_request,
    };
    use crate::EngineErrorType;
    use crate::networking::canopy::sdk::resources::{
        AccountSummary, ListSessionsResponse, PageInfo, SessionEnvelope, SessionSummary,
    };
    use crate::networking::canopy::sdk::well_known_types::Timestamp;

    fn bearer(token: &str) -> String {
        format!("Bearer {token}")
    }

    fn fixture_token(kind: &str) -> String {
        format!("{kind}-secret")
    }

    fn canonical_envelope() -> SessionEnvelope {
        SessionEnvelope {
            access_token: "access-secret".into(),
            refresh_token: fixture_token("refresh"),
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
    fn device_session_page_is_credential_free_and_preserves_opaque_token() {
        let response = ListSessionsResponse {
            sessions: vec![canonical_envelope().session.unwrap()],
            page_info: Some(PageInfo {
                next_page_token: "opaque-next".into(),
            }),
        };

        let mapped = super::map_session_page(response).unwrap();

        assert_eq!(mapped.items[0].id, "session-1");
        assert_eq!(
            mapped.next_page_token.as_ref().unwrap().as_str(),
            "opaque-next"
        );
        let rendered = format!("{mapped:?}");
        assert!(!rendered.contains("access-secret"));
        assert!(!rendered.contains("refresh-secret"));
        assert!(!rendered.to_ascii_lowercase().contains("bearer "));
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
        let register = register_request("driver@example.com", "password");
        let resend = resend_request("driver@example.com");
        let verify = verify_request("opaque-token", "PandaWave");
        let login = login_request("driver@example.com", "password", "car");
        let refresh = refresh_request("refresh-secret");
        let logout = logout_request("access-secret").unwrap();

        assert!(register.metadata().get("authorization").is_none());
        assert!(resend.metadata().get("authorization").is_none());
        assert!(verify.metadata().get("authorization").is_none());
        assert!(login.metadata().get("authorization").is_none());
        assert!(refresh.metadata().get("authorization").is_none());
        assert_eq!(
            logout
                .metadata()
                .get("authorization")
                .unwrap()
                .to_str()
                .unwrap(),
            bearer("access-secret")
        );
    }

    #[test]
    fn bootstrap_requests_preserve_canonical_fields_verbatim() {
        let register = register_request("driver@example.com", "secret-password");
        let resend = resend_request("driver@example.com");
        let verify = verify_request("opaque-verification-token", "PandaWave emulator");

        assert_eq!(register.get_ref().email, "driver@example.com");
        assert_eq!(register.get_ref().password, "secret-password");
        assert_eq!(resend.get_ref().email, "driver@example.com");
        assert_eq!(
            verify.get_ref().verification_token,
            "opaque-verification-token"
        );
        assert_eq!(verify.get_ref().device_label, "PandaWave emulator");
    }

    #[test]
    fn invalid_authorization_metadata_is_typed_without_echoing_the_token() {
        let token = "secret\nheader";
        let error = logout_request(token).unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::InvalidInput);
        assert!(!error.message.contains(token));
        assert!(!error.message.contains("secret"));
    }
}
