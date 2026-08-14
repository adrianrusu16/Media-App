use std::future::Future;
use std::time::Duration;

use tonic_014::metadata::MetadataValue;
use tonic_014::{Code, Request, Response, Status};

use crate::{EngineError, EngineErrorType, EngineHistoryIdentity, RetryClass};

use super::error::map_status;
use super::operation::{AuthRequirement, CanopyOperation};
use super::session::{AccessSnapshot, SessionCoordinator};

const RPC_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Clone, Copy)]
enum ExecutionClock {
    System,
    #[cfg(test)]
    Fixed(u64),
}

pub(crate) async fn execute<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    coordinator: Option<&SessionCoordinator>,
    operation: CanopyOperation,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture: Future<Output = Result<Response<TResponse>, Status>>,
{
    execute_with_auth_with_clock(
        coordinator,
        None,
        operation,
        ExecutionClock::System,
        make_request,
        execute,
    )
    .await
}

pub(crate) async fn execute_with_bound_auth<
    TRequest,
    TResponse,
    MakeRequest,
    Execute,
    ExecuteFuture,
>(
    coordinator: &SessionCoordinator,
    identity: &EngineHistoryIdentity,
    operation: CanopyOperation,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture: Future<Output = Result<Response<TResponse>, Status>>,
{
    execute_with_auth_with_clock(
        Some(coordinator),
        Some(identity),
        operation,
        ExecutionClock::System,
        make_request,
        execute,
    )
    .await
}

#[cfg(test)]
async fn execute_with_auth_at<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    coordinator: Option<&SessionCoordinator>,
    operation: CanopyOperation,
    now_epoch_millis: u64,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture: Future<Output = Result<Response<TResponse>, Status>>,
{
    execute_with_auth_with_clock(
        coordinator,
        None,
        operation,
        ExecutionClock::Fixed(now_epoch_millis),
        make_request,
        execute,
    )
    .await
}

async fn execute_with_auth_with_clock<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    coordinator: Option<&SessionCoordinator>,
    expected_identity: Option<&EngineHistoryIdentity>,
    operation: CanopyOperation,
    clock: ExecutionClock,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture: Future<Output = Result<Response<TResponse>, Status>>,
{
    let first_request = make_request();
    let has_explicit_access = first_request.metadata().contains_key("authorization");
    let uses_explicit_access = operation.auth_requirement() == AuthRequirement::AccessAuthenticated
        && coordinator.is_none()
        && has_explicit_access;
    let snapshot = match operation.auth_requirement() {
        AuthRequirement::Anonymous | AuthRequirement::RefreshCredential => {
            AccessSnapshot::Anonymous
        }
        AuthRequirement::OptionalAccess => access_snapshot(coordinator, clock).await?,
        AuthRequirement::AccessAuthenticated if coordinator.is_some() => {
            access_snapshot(coordinator, clock).await?
        }
        AuthRequirement::AccessAuthenticated if has_explicit_access => AccessSnapshot::Anonymous,
        AuthRequirement::AccessAuthenticated => return Err(missing_session_coordinator()),
    };
    if operation.auth_requirement() == AuthRequirement::AccessAuthenticated
        && snapshot == AccessSnapshot::Anonymous
        && !uses_explicit_access
    {
        return Err(missing_authenticated_session());
    }
    verify_bound_identity(&snapshot, expected_identity)?;

    let first = execute(authorized_request(first_request, &snapshot)?).await;
    revalidate_bound_identity(coordinator, expected_identity)?;
    let status = match first {
        Ok(response) => return Ok(response),
        Err(status) => status,
    };
    if status.code() != Code::Unauthenticated
        || !matches!(
            operation.retry_class(),
            RetryClass::Read | RetryClass::IdempotentMutation
        )
        || snapshot == AccessSnapshot::Anonymous
    {
        return Err(map_operation_status(status, uses_explicit_access));
    }

    let coordinator = coordinator.expect("an authenticated snapshot requires a coordinator");
    let replacement = match clock {
        ExecutionClock::System => coordinator.refresh_after_rejection(&snapshot).await?,
        #[cfg(test)]
        ExecutionClock::Fixed(now) => {
            coordinator
                .refresh_after_rejection_at(&snapshot, now)
                .await?
        }
    };
    verify_bound_identity(&replacement, expected_identity)?;
    let retry = execute(authorized_request(make_request(), &replacement)?).await;
    revalidate_bound_identity(Some(coordinator), expected_identity)?;
    match retry {
        Ok(response) => Ok(response),
        Err(status) if status.code() == Code::Unauthenticated => {
            coordinator.invalidate_if_current(&replacement).await?;
            Err(map_operation_status(status, false))
        }
        Err(status) => Err(map_operation_status(status, false)),
    }
}

fn map_operation_status(status: Status, uses_explicit_access: bool) -> EngineError {
    if uses_explicit_access && status.code() == Code::Unauthenticated {
        EngineError::new(
            EngineErrorType::AuthExpired,
            "backend access credential expired",
            false,
        )
    } else {
        map_status(status)
    }
}

async fn access_snapshot(
    coordinator: Option<&SessionCoordinator>,
    clock: ExecutionClock,
) -> Result<AccessSnapshot, EngineError> {
    match (coordinator, clock) {
        (Some(coordinator), ExecutionClock::System) => coordinator.fresh_access_snapshot().await,
        #[cfg(test)]
        (Some(coordinator), ExecutionClock::Fixed(now)) => {
            coordinator.fresh_access_snapshot_at(now).await
        }
        (None, _) => Ok(AccessSnapshot::Anonymous),
    }
}

fn missing_session_coordinator() -> EngineError {
    EngineError::new(
        EngineErrorType::FailedPrecondition,
        "authenticated Canopy operation requires a session coordinator",
        false,
    )
}

fn missing_authenticated_session() -> EngineError {
    EngineError::new(
        EngineErrorType::LoginRequired,
        "authenticated Canopy operation requires a current session",
        false,
    )
}

fn revalidate_bound_identity(
    coordinator: Option<&SessionCoordinator>,
    expected_identity: Option<&EngineHistoryIdentity>,
) -> Result<(), EngineError> {
    let (Some(coordinator), Some(expected_identity)) = (coordinator, expected_identity) else {
        return Ok(());
    };
    verify_bound_identity(
        &coordinator.current_access_snapshot()?,
        Some(expected_identity),
    )
}

fn verify_bound_identity(
    snapshot: &AccessSnapshot,
    expected: Option<&EngineHistoryIdentity>,
) -> Result<(), EngineError> {
    match (snapshot, expected) {
        (_, None) => Ok(()),
        (AccessSnapshot::Authenticated { identity, .. }, Some(expected))
            if identity == expected =>
        {
            Ok(())
        }
        _ => Err(EngineError::new(
            EngineErrorType::LoginRequired,
            "protected request identity no longer matches the current session",
            false,
        )),
    }
}

fn authorized_request<T>(
    mut request: Request<T>,
    snapshot: &AccessSnapshot,
) -> Result<Request<T>, EngineError> {
    if !request.metadata().contains_key("grpc-timeout") {
        request.set_timeout(RPC_TIMEOUT);
    }
    if let AccessSnapshot::Authenticated { token, .. } = snapshot {
        let value = MetadataValue::try_from(format!("Bearer {token}"))
            .map_err(|_| invalid_authorization_metadata())?;
        request.metadata_mut().insert("authorization", value);
    }
    Ok(request)
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
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::{Arc, Mutex};

    use tonic_014::{Code, Request, Response, Status};

    use super::{
        AccessSnapshot, ExecutionClock, execute_with_auth_at, execute_with_auth_with_clock,
        verify_bound_identity,
    };
    use crate::networking::canopy::CanopyOperation;
    use crate::{
        Account, AuthPort, AuthSession, AuthSessionEnvelope, EngineError, EngineErrorType,
        EngineHistoryIdentity, InMemorySessionStore, SessionCoordinator, SessionStore,
    };

    struct RotatingAuth {
        refreshes: AtomicUsize,
        replacement: AuthSessionEnvelope,
    }

    #[async_trait::async_trait]
    impl AuthPort for RotatingAuth {
        async fn login_password(
            &self,
            _email: &str,
            _password: &str,
            _device_label: &str,
        ) -> Result<AuthSessionEnvelope, EngineError> {
            unreachable!()
        }

        async fn refresh_session(
            &self,
            _refresh_token: &str,
        ) -> Result<AuthSessionEnvelope, EngineError> {
            self.refreshes.fetch_add(1, Ordering::SeqCst);
            Ok(self.replacement.clone())
        }

        async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
            unreachable!()
        }
    }

    fn envelope(access: &str, refresh: &str, access_expiry: u64) -> AuthSessionEnvelope {
        envelope_with_current(access, refresh, access_expiry, true)
    }

    fn envelope_with_current(
        access: &str,
        refresh: &str,
        access_expiry: u64,
        current: bool,
    ) -> AuthSessionEnvelope {
        AuthSessionEnvelope::new(
            access.into(),
            access_expiry,
            refresh.into(),
            50_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 1,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "car".into(),
                created_at_epoch_millis: 1,
                last_used_at_epoch_millis: 1,
                expires_at_epoch_millis: 50_000,
                current,
            },
        )
    }

    fn envelope_for_identity(
        account_id: &str,
        session_id: &str,
        current: bool,
    ) -> AuthSessionEnvelope {
        AuthSessionEnvelope::new(
            "access".into(),
            20_000,
            "refresh".into(),
            50_000,
            Account {
                id: account_id.into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 1,
            },
            AuthSession {
                id: session_id.into(),
                device_label: "car".into(),
                created_at_epoch_millis: 1,
                last_used_at_epoch_millis: 1,
                expires_at_epoch_millis: 50_000,
                current,
            },
        )
    }

    fn coordinator(
        initial: Option<AuthSessionEnvelope>,
        replacement: AuthSessionEnvelope,
    ) -> (Arc<SessionCoordinator>, Arc<RotatingAuth>) {
        let store: Arc<dyn SessionStore> = match initial {
            Some(session) => Arc::new(InMemorySessionStore::with_session(session)),
            None => Arc::new(InMemorySessionStore::new()),
        };
        let auth = Arc::new(RotatingAuth {
            refreshes: AtomicUsize::new(0),
            replacement,
        });
        (Arc::new(SessionCoordinator::new(store, auth.clone())), auth)
    }

    #[tokio::test]
    async fn access_authenticated_operation_requires_a_session_coordinator() {
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            None,
            CanopyOperation::GetProfile,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::FailedPrecondition);
        assert_eq!(attempts.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn access_authenticated_operation_rejects_an_empty_session_before_dispatch() {
        let (coordinator, _) = coordinator(None, envelope("unused", "unused", 20_000));
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetProfile,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn access_authenticated_operation_accepts_an_explicit_access_header() {
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        execute_with_auth_at(
            None,
            CanopyOperation::Logout,
            1_000,
            || {
                let mut request = Request::new(());
                request
                    .metadata_mut()
                    .insert("authorization", "Bearer opaque-access".parse().unwrap());
                request
            },
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap();

        assert_eq!(attempts.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn rejected_explicit_logout_preserves_the_typed_expired_auth_error() {
        let error = execute_with_auth_at(
            None,
            CanopyOperation::Logout,
            1_000,
            || {
                let mut request = Request::new(());
                request
                    .metadata_mut()
                    .insert("authorization", "Bearer expired-access".parse().unwrap());
                request
            },
            |_| async { Err::<Response<()>, _>(Status::unauthenticated("ignored")) },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::AuthExpired);
        assert!(!error.message.contains("ignored"));
    }

    #[test]
    fn bound_authorization_rejects_a_different_account_or_session() {
        let snapshot = AccessSnapshot::Authenticated {
            token: "replacement-token".into(),
            generation: 2,
            identity: EngineHistoryIdentity {
                account_id: "account-2".into(),
                session_id: "session-2".into(),
            },
        };
        let expected = EngineHistoryIdentity {
            account_id: "account-1".into(),
            session_id: "session-1".into(),
        };

        assert_eq!(
            verify_bound_identity(&snapshot, Some(&expected))
                .unwrap_err()
                .error_type,
            EngineErrorType::LoginRequired,
        );
    }

    #[tokio::test]
    async fn bound_request_revalidates_identity_after_successful_rpc() {
        enum Transition {
            Account,
            Session,
            NotCurrent,
            Logout,
        }

        for transition in [
            Transition::Account,
            Transition::Session,
            Transition::NotCurrent,
            Transition::Logout,
        ] {
            let store = Arc::new(InMemorySessionStore::with_session(envelope_for_identity(
                "account-1",
                "session-1",
                true,
            )));
            let auth = Arc::new(RotatingAuth {
                refreshes: AtomicUsize::new(0),
                replacement: envelope("unused", "unused", 30_000),
            });
            let coordinator = SessionCoordinator::new(store.clone(), auth);
            let expected = EngineHistoryIdentity {
                account_id: "account-1".into(),
                session_id: "session-1".into(),
            };

            let error = execute_with_auth_with_clock(
                Some(&coordinator),
                Some(&expected),
                CanopyOperation::RecordPlayback,
                ExecutionClock::Fixed(1_000),
                || Request::new(()),
                move |_| {
                    match transition {
                        Transition::Account => store
                            .replace(envelope_for_identity("account-2", "session-1", true))
                            .unwrap(),
                        Transition::Session => store
                            .replace(envelope_for_identity("account-1", "session-2", true))
                            .unwrap(),
                        Transition::NotCurrent => store
                            .replace(envelope_for_identity("account-1", "session-1", false))
                            .unwrap(),
                        Transition::Logout => store.clear().unwrap(),
                    }
                    async { Ok::<_, Status>(Response::new(())) }
                },
            )
            .await
            .unwrap_err();

            assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        }
    }

    #[tokio::test]
    async fn bound_request_revalidates_identity_after_failed_rpc() {
        let store = Arc::new(InMemorySessionStore::with_session(envelope_for_identity(
            "account-1",
            "session-1",
            true,
        )));
        let auth = Arc::new(RotatingAuth {
            refreshes: AtomicUsize::new(0),
            replacement: envelope("unused", "unused", 30_000),
        });
        let coordinator = SessionCoordinator::new(store.clone(), auth);
        let expected = EngineHistoryIdentity {
            account_id: "account-1".into(),
            session_id: "session-1".into(),
        };

        let error = execute_with_auth_with_clock(
            Some(&coordinator),
            Some(&expected),
            CanopyOperation::RecordPlayback,
            ExecutionClock::Fixed(1_000),
            || Request::new(()),
            move |_| {
                store.clear().unwrap();
                async { Err::<Response<()>, _>(Status::unavailable("typed failure")) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
    }

    #[tokio::test]
    async fn bound_request_preserves_typed_failure_for_same_identity() {
        let (coordinator, _) = coordinator(
            Some(envelope("access", "refresh", 20_000)),
            envelope("unused", "unused", 30_000),
        );
        let expected = EngineHistoryIdentity {
            account_id: "account-1".into(),
            session_id: "session-1".into(),
        };

        let error = execute_with_auth_with_clock(
            Some(&coordinator),
            Some(&expected),
            CanopyOperation::RecordPlayback,
            ExecutionClock::Fixed(1_000),
            || Request::new(()),
            |_| async { Err::<Response<()>, _>(Status::unavailable("typed failure")) },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::ServiceUnavailable);
    }

    #[tokio::test]
    async fn anonymous_request_omits_authorization_metadata() {
        let (coordinator, _) = coordinator(None, envelope("unused", "unused", 20_000));
        let seen = Arc::new(Mutex::new(Vec::new()));
        let capture = seen.clone();

        execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |request| {
                let capture = capture.clone();
                async move {
                    capture.lock().unwrap().push(
                        request
                            .metadata()
                            .get("authorization")
                            .and_then(|value| value.to_str().ok())
                            .map(str::to_owned),
                    );
                    Ok(Response::new(()))
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(*seen.lock().unwrap(), [None]);
    }

    #[tokio::test]
    async fn every_adapter_request_has_a_bounded_deadline() {
        let (coordinator, _) = coordinator(None, envelope("unused", "unused", 20_000));
        let timeout = Arc::new(Mutex::new(None));
        let capture = timeout.clone();

        execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |request| {
                let capture = capture.clone();
                async move {
                    *capture.lock().unwrap() = request
                        .metadata()
                        .get("grpc-timeout")
                        .and_then(|value| value.to_str().ok())
                        .map(str::to_owned);
                    Ok(Response::new(()))
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(*timeout.lock().unwrap(), Some("5000000u".to_owned()));
    }

    #[tokio::test]
    async fn resolve_playback_preserves_its_three_second_deadline() {
        let timeout = Arc::new(Mutex::new(None));
        let capture = timeout.clone();

        execute_with_auth_at(
            None,
            CanopyOperation::ResolvePlayback,
            1_000,
            || {
                let mut request = Request::new(());
                request.set_timeout(std::time::Duration::from_secs(3));
                request
            },
            move |request| {
                let capture = capture.clone();
                async move {
                    *capture.lock().unwrap() = request
                        .metadata()
                        .get("grpc-timeout")
                        .and_then(|value| value.to_str().ok())
                        .map(str::to_owned);
                    Ok(Response::new(()))
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(*timeout.lock().unwrap(), Some("3000000u".to_owned()));
    }

    #[tokio::test]
    async fn get_status_preserves_its_three_second_deadline() {
        let timeout = Arc::new(Mutex::new(None));
        let capture = timeout.clone();

        execute_with_auth_at(
            None,
            CanopyOperation::GetStatus,
            1_000,
            || {
                let mut request = Request::new(());
                request.set_timeout(std::time::Duration::from_secs(3));
                request
            },
            move |request| {
                let capture = capture.clone();
                async move {
                    *capture.lock().unwrap() = request
                        .metadata()
                        .get("grpc-timeout")
                        .and_then(|value| value.to_str().ok())
                        .map(str::to_owned);
                    Ok(Response::new(()))
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(*timeout.lock().unwrap(), Some("3000000u".to_owned()));
    }

    #[tokio::test]
    async fn authenticated_request_attaches_lowercase_bearer_metadata() {
        let (coordinator, _) = coordinator(
            Some(envelope("opaque-access", "refresh-1", 20_000)),
            envelope("unused", "unused", 30_000),
        );
        let seen = Arc::new(Mutex::new(Vec::new()));
        let capture = seen.clone();

        execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |request| {
                let capture = capture.clone();
                async move {
                    capture.lock().unwrap().push(
                        request
                            .metadata()
                            .get("authorization")
                            .unwrap()
                            .to_str()
                            .unwrap()
                            .to_owned(),
                    );
                    Ok(Response::new(()))
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(*seen.lock().unwrap(), ["Bearer opaque-access"]);
    }

    #[tokio::test]
    async fn non_current_initial_session_is_rejected_before_protected_rpc() {
        let (coordinator, auth) = coordinator(
            Some(envelope_with_current(
                "non-current-access",
                "refresh-1",
                20_000,
                false,
            )),
            envelope("unused", "unused", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 0);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn safe_authenticated_request_refreshes_and_retries_exactly_once() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let seen = Arc::new(Mutex::new(Vec::new()));
        let attempt_counter = attempts.clone();
        let capture = seen.clone();

        execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |request| {
                let attempt = attempt_counter.fetch_add(1, Ordering::SeqCst);
                let capture = capture.clone();
                async move {
                    capture.lock().unwrap().push(
                        request
                            .metadata()
                            .get("authorization")
                            .unwrap()
                            .to_str()
                            .unwrap()
                            .to_owned(),
                    );
                    if attempt == 0 {
                        Err(Status::unauthenticated("message must not drive behavior"))
                    } else {
                        Ok(Response::new(()))
                    }
                }
            },
        )
        .await
        .unwrap();

        assert_eq!(attempts.load(Ordering::SeqCst), 2);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
        assert_eq!(
            *seen.lock().unwrap(),
            ["Bearer rejected-access", "Bearer rotated-access"]
        );
    }

    #[tokio::test]
    async fn non_current_refresh_is_rejected_before_safe_replay() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope_with_current("rotated-access", "refresh-2", 30_000, false),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                let attempt = counter.fetch_add(1, Ordering::SeqCst);
                async move {
                    if attempt == 0 {
                        Err(Status::unauthenticated("refresh required"))
                    } else {
                        Ok(Response::new(()))
                    }
                }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 1);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn rejected_retry_invalidates_the_still_current_rotated_session() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Err::<Response<()>, _>(Status::unauthenticated("ignored")) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 2);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
        assert_eq!(
            coordinator.auth_state().unwrap(),
            crate::AuthState::LoginRequired
        );

        let later_counter = attempts.clone();
        let later = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                later_counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap_err();
        assert_eq!(later.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 2);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn concurrent_rejections_of_one_access_snapshot_share_one_refresh() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let calls = Arc::new(AtomicUsize::new(0));
        let rejected_barrier = Arc::new(tokio::sync::Barrier::new(8));
        let mut tasks = Vec::new();
        for _ in 0..8 {
            let coordinator = coordinator.clone();
            let calls = calls.clone();
            let rejected_barrier = rejected_barrier.clone();
            tasks.push(tokio::spawn(async move {
                execute_with_auth_at(
                    Some(&coordinator),
                    CanopyOperation::GetMedia,
                    1_000,
                    || Request::new(()),
                    move |request| {
                        let calls = calls.clone();
                        let rejected_barrier = rejected_barrier.clone();
                        async move {
                            calls.fetch_add(1, Ordering::SeqCst);
                            if request.metadata().get("authorization").unwrap()
                                == "Bearer rejected-access"
                            {
                                rejected_barrier.wait().await;
                                Err(Status::new(Code::Unauthenticated, "any message"))
                            } else {
                                Ok(Response::new(()))
                            }
                        }
                    },
                )
                .await
            }));
        }
        for task in tasks {
            task.await.unwrap().unwrap();
        }

        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
        assert_eq!(calls.load(Ordering::SeqCst), 16);
    }

    #[tokio::test]
    async fn identical_rotated_token_text_still_advances_generation_once() {
        let (coordinator, auth) = coordinator(
            Some(envelope("same-access", "refresh-1", 20_000)),
            envelope("same-access", "refresh-2", 30_000),
        );
        let rejected_barrier = Arc::new(tokio::sync::Barrier::new(8));
        let mut tasks = Vec::new();
        for _ in 0..8 {
            let coordinator = coordinator.clone();
            let rejected_barrier = rejected_barrier.clone();
            tasks.push(tokio::spawn(async move {
                let attempt = Arc::new(AtomicUsize::new(0));
                execute_with_auth_at(
                    Some(&coordinator),
                    CanopyOperation::GetMedia,
                    1_000,
                    || Request::new(()),
                    move |_| {
                        let attempt = attempt.clone();
                        let rejected_barrier = rejected_barrier.clone();
                        async move {
                            if attempt.fetch_add(1, Ordering::SeqCst) == 0 {
                                rejected_barrier.wait().await;
                                Err(Status::unauthenticated("ignored"))
                            } else {
                                Ok(Response::new(()))
                            }
                        }
                    },
                )
                .await
            }));
        }
        for task in tasks {
            task.await.unwrap().unwrap();
        }

        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn non_idempotent_request_is_never_replayed() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::RecordPlayback,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Err::<Response<()>, _>(Status::unauthenticated("ignored")) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 1);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn anonymous_unauthenticated_response_does_not_refresh_or_retry() {
        let (coordinator, auth) = coordinator(None, envelope("unused", "unused", 30_000));
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Err::<Response<()>, _>(Status::unauthenticated("ignored")) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 1);
        assert_eq!(auth.refreshes.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn expired_session_refresh_failure_never_falls_back_to_anonymous() {
        struct FailingAuth;
        #[async_trait::async_trait]
        impl AuthPort for FailingAuth {
            async fn login_password(
                &self,
                _: &str,
                _: &str,
                _: &str,
            ) -> Result<AuthSessionEnvelope, EngineError> {
                unreachable!()
            }
            async fn refresh_session(&self, _: &str) -> Result<AuthSessionEnvelope, EngineError> {
                Err(EngineError::new(
                    EngineErrorType::ServiceUnavailable,
                    "ambiguous refresh",
                    false,
                ))
            }
            async fn logout(&self, _: &str) -> Result<(), EngineError> {
                unreachable!()
            }
        }
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::with_session(envelope(
            "expired",
            "refresh-1",
            500,
        )));
        let coordinator = Arc::new(SessionCoordinator::new(store, Arc::new(FailingAuth)));
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth_at(
            Some(&coordinator),
            CanopyOperation::GetMedia,
            1_000,
            || Request::new(()),
            move |_| {
                counter.fetch_add(1, Ordering::SeqCst);
                async { Ok::<_, Status>(Response::new(())) }
            },
        )
        .await
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::LoginRequired);
        assert_eq!(attempts.load(Ordering::SeqCst), 0);
    }
}
