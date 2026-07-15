use std::future::Future;
use std::time::{SystemTime, UNIX_EPOCH};

use tonic_014::metadata::MetadataValue;
use tonic_014::{Code, Request, Response, Status};

use crate::{EngineError, EngineErrorType};

use super::error::map_status;
use super::session::{AccessSnapshot, SessionCoordinator};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum ReplayPolicy {
    Safe,
    /// Reserved for mutation adapters: authentication failure is terminal.
    #[allow(dead_code)]
    NonIdempotent,
}

pub(crate) fn current_epoch_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .try_into()
        .unwrap_or(u64::MAX)
}

pub(crate) async fn execute_with_auth<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    coordinator: Option<&SessionCoordinator>,
    policy: ReplayPolicy,
    now_epoch_millis: u64,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture: Future<Output = Result<Response<TResponse>, Status>>,
{
    let snapshot = match coordinator {
        Some(coordinator) => coordinator.fresh_access_snapshot(now_epoch_millis).await?,
        None => AccessSnapshot::Anonymous,
    };

    let first = execute(authorized_request(make_request(), &snapshot)?).await;
    let status = match first {
        Ok(response) => return Ok(response),
        Err(status) => status,
    };
    if status.code() != Code::Unauthenticated
        || policy != ReplayPolicy::Safe
        || snapshot == AccessSnapshot::Anonymous
    {
        return Err(map_status(status));
    }

    let replacement = coordinator
        .expect("an authenticated snapshot requires a coordinator")
        .refresh_after_rejection(&snapshot, now_epoch_millis)
        .await?;
    match execute(authorized_request(make_request(), &replacement)?).await {
        Ok(response) => Ok(response),
        Err(status) if status.code() == Code::Unauthenticated => {
            coordinator
                .expect("an authenticated snapshot requires a coordinator")
                .invalidate_if_current(&replacement)
                .await?;
            Err(map_status(status))
        }
        Err(status) => Err(map_status(status)),
    }
}

fn authorized_request<T>(
    mut request: Request<T>,
    snapshot: &AccessSnapshot,
) -> Result<Request<T>, EngineError> {
    if let AccessSnapshot::Authenticated { token } = snapshot {
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

    use super::{ReplayPolicy, execute_with_auth};
    use crate::{
        Account, AuthPort, AuthSession, AuthSessionEnvelope, EngineError, EngineErrorType,
        InMemorySessionStore, SessionCoordinator, SessionStore,
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
                current: true,
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
    async fn anonymous_request_omits_authorization_metadata() {
        let (coordinator, _) = coordinator(None, envelope("unused", "unused", 20_000));
        let seen = Arc::new(Mutex::new(Vec::new()));
        let capture = seen.clone();

        execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
    async fn authenticated_request_attaches_lowercase_bearer_metadata() {
        let (coordinator, _) = coordinator(
            Some(envelope("opaque-access", "refresh-1", 20_000)),
            envelope("unused", "unused", 30_000),
        );
        let seen = Arc::new(Mutex::new(Vec::new()));
        let capture = seen.clone();

        execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
    async fn safe_authenticated_request_refreshes_and_retries_exactly_once() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let seen = Arc::new(Mutex::new(Vec::new()));
        let attempt_counter = attempts.clone();
        let capture = seen.clone();

        execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
    async fn rejected_retry_invalidates_the_still_current_rotated_session() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
        let later = execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
                execute_with_auth(
                    Some(&coordinator),
                    ReplayPolicy::Safe,
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
    async fn non_idempotent_request_is_never_replayed() {
        let (coordinator, auth) = coordinator(
            Some(envelope("rejected-access", "refresh-1", 20_000)),
            envelope("rotated-access", "refresh-2", 30_000),
        );
        let attempts = Arc::new(AtomicUsize::new(0));
        let counter = attempts.clone();

        let error = execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::NonIdempotent,
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

        let error = execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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

        let error = execute_with_auth(
            Some(&coordinator),
            ReplayPolicy::Safe,
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
