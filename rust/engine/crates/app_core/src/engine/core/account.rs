use super::*;
use crate::{AccountPort, EngineError, EngineErrorType, EnginePageRequest};

impl Engine {
    pub(super) async fn dispatch_account_command(
        &mut self,
        command: &EngineCommandType,
        snapshot: &mut EngineSnapshot,
    ) {
        let result = match command {
            EngineCommandType::GetAccount => self.get_account(snapshot).await,
            EngineCommandType::ListDeviceSessions { page } => {
                snapshot.device_sessions.clear();
                snapshot.device_sessions_next_page_token = None;
                self.device_sessions_operation = None;
                self.list_device_sessions(snapshot, page.clone(), false)
                    .await
            }
            EngineCommandType::LoadNextDeviceSessionsPage => {
                let Some(operation) = self.device_sessions_operation.clone() else {
                    return Self::set_account_error(
                        snapshot,
                        invalid_input("no active device-session page operation"),
                    );
                };
                let Some(token) = snapshot.device_sessions_next_page_token.clone() else {
                    return;
                };
                if Self::current_identity(self.auth_state_provider.as_ref())
                    != Some(operation.auth_identity.clone())
                {
                    self.clear_account_operation(snapshot);
                    Err(login_required())
                } else {
                    self.list_device_sessions(
                        snapshot,
                        EnginePageRequest {
                            page_size: operation.page_size,
                            page_token: Some(token),
                        },
                        true,
                    )
                    .await
                }
            }
            EngineCommandType::RevokeDeviceSession { session_id } => {
                self.revoke_device_session(snapshot, session_id).await
            }
            EngineCommandType::DeleteAccount => self.delete_account(snapshot).await,
            _ => return,
        };
        if let Err(error) = result {
            Self::set_account_error(snapshot, error);
        }
    }

    async fn get_account(&mut self, snapshot: &mut EngineSnapshot) -> Result<(), EngineError> {
        let (identity, port) = self.account_context(snapshot)?;
        let account = match self.take_prefetched_account() {
            Some(prefetched) => prefetched,
            None => port.get_account(&identity.account_identity()).await,
        };
        let account = self.account_result_for_current_identity(snapshot, &identity, account)?;
        if account.id != identity.account_id {
            return Err(EngineError::new(
                EngineErrorType::Forbidden,
                "account response does not belong to authenticated identity",
                false,
            ));
        }
        snapshot.protected_account = Some(account);
        self.account_projection_identity = Some(identity);
        Ok(())
    }

    async fn list_device_sessions(
        &mut self,
        snapshot: &mut EngineSnapshot,
        page: EnginePageRequest,
        append: bool,
    ) -> Result<(), EngineError> {
        let (identity, port) = self.account_context(snapshot)?;
        let result = port
            .list_sessions(&identity.account_identity(), page.clone())
            .await;
        let result = self.account_result_for_current_identity(snapshot, &identity, result)?;
        if append {
            snapshot.device_sessions.extend(result.items);
        } else {
            snapshot.device_sessions = result.items;
        }
        snapshot.device_sessions_next_page_token = result.next_page_token;
        self.account_projection_identity = Some(identity.clone());
        self.device_sessions_operation = Some(DeviceSessionsOperation {
            auth_identity: identity,
            page_size: page.page_size,
        });
        Ok(())
    }

    async fn revoke_device_session(
        &mut self,
        snapshot: &mut EngineSnapshot,
        session_id: &str,
    ) -> Result<(), EngineError> {
        let (identity, port) = self.account_context(snapshot)?;
        if session_id == identity.session_id {
            return Err(invalid_input(
                "the current authenticated session must be ended through logout",
            ));
        }
        let result = port
            .revoke_session(&identity.account_identity(), session_id)
            .await;
        self.account_result_for_current_identity(snapshot, &identity, result)?;
        snapshot
            .device_sessions
            .retain(|session| session.id != session_id);
        self.account_projection_identity = Some(identity);
        Ok(())
    }

    async fn delete_account(&mut self, snapshot: &mut EngineSnapshot) -> Result<(), EngineError> {
        let (identity, port) = self.account_context(snapshot)?;
        let result = port.delete_account(&identity.account_identity()).await;
        let auth_state = self
            .auth_state_provider
            .as_ref()
            .map(|provider| provider.current_auth_state())
            .unwrap_or(crate::AuthState::Anonymous);
        let current = AuthIdentity::from_state(&auth_state);
        let is_anonymous = matches!(auth_state, crate::AuthState::Anonymous);
        snapshot.auth_state = auth_state;
        match current {
            None if result.is_ok() && is_anonymous => {
                self.clear_all_protected_operations(snapshot);
                Ok(())
            }
            Some(current) if current == identity => {
                result?;
                Err(EngineError::new(
                    EngineErrorType::SessionStorage,
                    "account deleted but local session was not cleared",
                    false,
                ))
            }
            None | Some(_) => {
                self.clear_all_protected_operations(snapshot);
                Err(login_required())
            }
        }
    }

    fn account_context(
        &self,
        snapshot: &EngineSnapshot,
    ) -> Result<(AuthIdentity, Arc<dyn AccountPort>), EngineError> {
        let identity = AuthIdentity::from_state(&snapshot.auth_state).ok_or_else(login_required)?;
        let port = self.account_port.clone().ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "account service is not configured",
                false,
            )
        })?;
        Ok((identity, port))
    }

    fn account_result_for_current_identity<T>(
        &mut self,
        snapshot: &mut EngineSnapshot,
        expected: &AuthIdentity,
        result: Result<T, EngineError>,
    ) -> Result<T, EngineError> {
        let auth_state = self
            .auth_state_provider
            .as_ref()
            .map(|provider| provider.current_auth_state())
            .unwrap_or(crate::AuthState::Anonymous);
        let current = AuthIdentity::from_state(&auth_state);
        snapshot.auth_state = auth_state;
        if current.as_ref() == Some(expected) {
            result
        } else {
            self.clear_account_operation(snapshot);
            Err(login_required())
        }
    }

    fn current_identity(provider: Option<&Arc<dyn AuthStateProvider>>) -> Option<AuthIdentity> {
        provider.and_then(|provider| AuthIdentity::from_state(&provider.current_auth_state()))
    }

    fn clear_account_operation(&mut self, snapshot: &mut EngineSnapshot) {
        self.account_projection_identity = None;
        self.device_sessions_operation = None;
        Self::clear_account_projection(snapshot);
    }

    fn clear_all_protected_operations(&mut self, snapshot: &mut EngineSnapshot) {
        self.clear_account_operation(snapshot);
        self.profile_projection_identity = None;
        Self::clear_profile_projection(snapshot);
        self.history_projection_owner = None;
        self.history_operation = None;
        Self::clear_history_projection(snapshot);
        self.library_projection_identity = None;
        self.saved_library_operation = None;
        self.liked_library_operation = None;
        Self::clear_library_projection(snapshot);
        self.playlist_projection_identity = None;
        self.playlists_operation = None;
        self.playlist_tracks_operation = None;
        Self::clear_playlist_projection(snapshot);
        self.feed_projection_identity = None;
        self.discovery_operation = None;
        snapshot.discovery_results.clear();
        snapshot.for_you_results.clear();
        snapshot.recommendations_results.clear();
        snapshot.discovery_next_page_token = None;
    }

    fn set_account_error(snapshot: &mut EngineSnapshot, error: EngineError) {
        snapshot.last_error = Some(error);
    }
}

fn login_required() -> EngineError {
    EngineError::new(
        EngineErrorType::LoginRequired,
        "account operation requires the current authenticated session",
        false,
    )
}
fn invalid_input(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        Account, AccountPort, AuthSession, AuthState, AuthStateProvider, EngineAccountIdentity,
        EnginePagedResult,
    };
    use std::sync::{Arc, Mutex};

    struct MutableAuth(Mutex<AuthState>);
    impl AuthStateProvider for MutableAuth {
        fn current_auth_state(&self) -> AuthState {
            self.0.lock().unwrap().clone()
        }
    }

    struct TransitioningPort {
        auth: Arc<MutableAuth>,
        transition: AuthState,
        delete_error: bool,
    }
    #[async_trait::async_trait]
    impl AccountPort for TransitioningPort {
        async fn get_account(&self, _: &EngineAccountIdentity) -> Result<Account, EngineError> {
            *self.auth.0.lock().unwrap() = self.transition.clone();
            Ok(account("account-1"))
        }
        async fn list_sessions(
            &self,
            _: &EngineAccountIdentity,
            _: EnginePageRequest,
        ) -> Result<EnginePagedResult<AuthSession>, EngineError> {
            *self.auth.0.lock().unwrap() = self.transition.clone();
            Ok(EnginePagedResult {
                items: vec![match authenticated("account-1", "session-2", false) {
                    AuthState::Authenticated { session, .. } => session,
                    _ => unreachable!(),
                }],
                next_page_token: None,
            })
        }
        async fn revoke_session(
            &self,
            _: &EngineAccountIdentity,
            _: &str,
        ) -> Result<(), EngineError> {
            *self.auth.0.lock().unwrap() = self.transition.clone();
            Ok(())
        }
        async fn delete_account(&self, _: &EngineAccountIdentity) -> Result<(), EngineError> {
            *self.auth.0.lock().unwrap() = self.transition.clone();
            if self.delete_error {
                Err(EngineError::new(
                    EngineErrorType::NetworkError,
                    "delete failed",
                    true,
                ))
            } else {
                Ok(())
            }
        }
    }

    fn account(id: &str) -> Account {
        Account {
            id: id.into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 1,
        }
    }
    fn authenticated(account_id: &str, session_id: &str, current: bool) -> AuthState {
        AuthState::Authenticated {
            account: account(account_id),
            session: AuthSession {
                id: session_id.into(),
                device_label: "car".into(),
                created_at_epoch_millis: 2,
                last_used_at_epoch_millis: 3,
                expires_at_epoch_millis: 4,
                current,
            },
        }
    }

    fn engine(transition: AuthState, delete_error: bool) -> Engine {
        let auth = Arc::new(MutableAuth(Mutex::new(authenticated(
            "account-1",
            "session-1",
            true,
        ))));
        let mut engine = Engine::new(0);
        engine.set_auth_state_provider(auth.clone());
        engine.set_account_port(Arc::new(TransitioningPort {
            auth,
            transition,
            delete_error,
        }));
        engine.snapshot.protected_account = Some(account("account-1"));
        engine.snapshot.device_sessions =
            vec![match authenticated("account-1", "session-1", true) {
                AuthState::Authenticated { session, .. } => session,
                _ => unreachable!(),
            }];
        engine.account_projection_identity =
            AuthIdentity::from_state(&authenticated("account-1", "session-1", true));
        engine
    }

    #[tokio::test]
    async fn in_flight_account_result_clears_for_every_identity_transition() {
        for transition in [
            authenticated("account-2", "session-1", true),
            authenticated("account-1", "session-2", true),
            authenticated("account-1", "session-1", false),
            AuthState::Anonymous,
        ] {
            let outcome = engine(transition, false)
                .dispatch(EngineCommand::new(EngineCommandType::GetAccount, None), 1)
                .await;
            assert_eq!(
                outcome.snapshot.last_error.unwrap().error_type,
                EngineErrorType::LoginRequired
            );
            assert!(outcome.snapshot.protected_account.is_none());
            assert!(outcome.snapshot.device_sessions.is_empty());
        }
    }

    #[tokio::test]
    async fn in_flight_session_list_and_revoke_clear_for_every_identity_transition() {
        for transition in [
            authenticated("account-2", "session-1", true),
            authenticated("account-1", "session-2", true),
            authenticated("account-1", "session-1", false),
            AuthState::Anonymous,
        ] {
            for command in [
                EngineCommandType::ListDeviceSessions {
                    page: EnginePageRequest::default(),
                },
                EngineCommandType::RevokeDeviceSession {
                    session_id: "session-other".into(),
                },
            ] {
                let outcome = engine(transition.clone(), false)
                    .dispatch(EngineCommand::new(command, None), 1)
                    .await;
                assert_eq!(
                    outcome.snapshot.last_error.unwrap().error_type,
                    EngineErrorType::LoginRequired
                );
                assert!(outcome.snapshot.protected_account.is_none());
                assert!(outcome.snapshot.device_sessions.is_empty());
            }
        }
    }

    #[tokio::test]
    async fn delete_failure_keeps_local_account_projection() {
        let outcome = engine(authenticated("account-1", "session-1", true), true)
            .dispatch(
                EngineCommand::new(EngineCommandType::DeleteAccount, None),
                1,
            )
            .await;
        assert_eq!(
            outcome.snapshot.last_error.unwrap().error_type,
            EngineErrorType::NetworkError
        );
        assert!(outcome.snapshot.protected_account.is_some());
        assert_eq!(outcome.snapshot.device_sessions.len(), 1);
    }

    #[tokio::test]
    async fn delete_success_clears_all_local_account_state() {
        let outcome = engine(AuthState::Anonymous, false)
            .dispatch(
                EngineCommand::new(EngineCommandType::DeleteAccount, None),
                1,
            )
            .await;
        assert!(outcome.snapshot.last_error.is_none());
        assert!(outcome.snapshot.protected_account.is_none());
        assert!(outcome.snapshot.device_sessions.is_empty());
        assert_eq!(outcome.snapshot.auth_state, AuthState::Anonymous);
    }

    #[tokio::test]
    async fn delete_result_never_publishes_or_clears_a_replacement_identity() {
        for delete_error in [false, true] {
            for transition in [
                authenticated("account-2", "session-1", true),
                authenticated("account-1", "session-2", true),
                authenticated("account-1", "session-1", false),
                AuthState::Anonymous,
            ] {
                if !delete_error && transition == AuthState::Anonymous {
                    continue;
                }
                let outcome = engine(transition.clone(), delete_error)
                    .dispatch(
                        EngineCommand::new(EngineCommandType::DeleteAccount, None),
                        1,
                    )
                    .await;
                assert_eq!(
                    outcome
                        .snapshot
                        .last_error
                        .as_ref()
                        .map(|error| error.error_type.clone()),
                    Some(EngineErrorType::LoginRequired),
                    "delete_error={delete_error}, transition={transition:?}",
                );
                assert!(outcome.snapshot.protected_account.is_none());
                assert!(outcome.snapshot.device_sessions.is_empty());
                assert_eq!(outcome.snapshot.auth_state, transition);
            }
        }
    }

    #[tokio::test]
    async fn current_session_cannot_be_revoked_through_the_engine() {
        let outcome = engine(authenticated("account-1", "session-1", true), false)
            .dispatch(
                EngineCommand::new(
                    EngineCommandType::RevokeDeviceSession {
                        session_id: "session-1".into(),
                    },
                    None,
                ),
                1,
            )
            .await;

        assert_eq!(
            outcome.snapshot.last_error.unwrap().error_type,
            EngineErrorType::InvalidInput
        );
        assert_eq!(outcome.snapshot.device_sessions.len(), 1);
    }

    #[tokio::test]
    async fn another_session_can_be_revoked_idempotently() {
        let mut engine = engine(authenticated("account-1", "session-1", true), false);
        engine.snapshot.device_sessions.push(
            match authenticated("account-1", "session-2", false) {
                AuthState::Authenticated { session, .. } => session,
                _ => unreachable!(),
            },
        );

        let outcome = engine
            .dispatch(
                EngineCommand::new(
                    EngineCommandType::RevokeDeviceSession {
                        session_id: "session-2".into(),
                    },
                    None,
                ),
                1,
            )
            .await;

        assert!(outcome.snapshot.last_error.is_none());
        assert_eq!(outcome.snapshot.device_sessions.len(), 1);
        assert_eq!(outcome.snapshot.device_sessions[0].id, "session-1");
    }
}
