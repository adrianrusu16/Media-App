use serde::Deserialize;

use super::*;
use crate::EngineErrorType;

const DEFAULT_HISTORY_PAGE_SIZE: u32 = 40;
const MAX_HISTORY_PAGE_SIZE: u32 = 50;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct PlaybackCompletedPayload {
    version: u32,
    track_id: String,
    duration_ms: u64,
    completion_ratio: f32,
}

impl Engine {
    pub(super) async fn dispatch_history_command(
        &mut self,
        command: &EngineCommandType,
        snapshot: &mut EngineSnapshot,
    ) {
        match command {
            EngineCommandType::LoadHistorySettings => {
                let result = match Self::history_context(snapshot, self.history_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port.get_settings(&identity.history_identity()).await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                            .map(|settings| (identity, settings))
                    }
                    Err(error) => Err(error),
                };
                match result {
                    Ok((identity, settings)) => {
                        self.history_projection_identity = Some(identity);
                        snapshot.history_settings = Some(settings);
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::UpdateHistorySettings { enabled } => {
                let result = match Self::history_context(snapshot, self.history_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port
                            .update_settings(&identity.history_identity(), *enabled)
                            .await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                            .map(|update| (identity, update))
                    }
                    Err(error) => Err(error),
                };
                match result {
                    Ok((identity, update)) => {
                        self.history_projection_identity = Some(identity);
                        snapshot.history_settings = Some(update.settings);
                        snapshot.history_deleted_count = update.deleted_count;
                        snapshot.history_state.availability =
                            crate::EngineHistoryAvailability::Available;
                        self.invalidate_history_pages(snapshot);
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::ListHistory { page } => {
                self.history_operation = None;
                snapshot.history_entries.clear();
                snapshot.history_next_page_token = None;
                let page = Self::bounded_history_page_request(page.clone());
                let result = match Self::history_context(snapshot, self.history_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port.list(&identity.history_identity(), page.clone()).await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                            .map(|page_result| (identity, page_result))
                    }
                    Err(error) => Err(error),
                };
                match result {
                    Ok((identity, page_result)) => {
                        snapshot.history_entries = page_result.items;
                        snapshot.history_next_page_token = page_result.next_page_token;
                        snapshot.history_state.availability =
                            crate::EngineHistoryAvailability::Available;
                        snapshot.history_state.refresh_state =
                            crate::EngineHistoryRefreshState::Idle;
                        self.history_projection_identity = Some(identity.clone());
                        self.history_operation = Some(HistoryOperation {
                            auth_identity: identity,
                            page_size: page.page_size,
                        });
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {}
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::LoadNextHistoryPage => {
                let result = match (
                    AuthIdentity::from_state(&snapshot.auth_state),
                    self.history_operation.clone(),
                    snapshot.history_next_page_token.clone(),
                    self.history_port.clone(),
                ) {
                    (Some(identity), Some(operation), Some(token), Some(port))
                        if identity == operation.auth_identity =>
                    {
                        let result = port
                            .list(
                                &identity.history_identity(),
                                crate::EnginePageRequest {
                                    page_size: Self::bounded_history_page_size(operation.page_size),
                                    page_token: Some(token),
                                },
                            )
                            .await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                    }
                    (None, _, _, _) => Err(Self::history_login_required()),
                    _ => Err(EngineError::new(
                        EngineErrorType::FailedPrecondition,
                        "no current history page operation",
                        false,
                    )),
                };
                match result {
                    Ok(page_result) => {
                        snapshot.history_entries = page_result.items;
                        snapshot.history_next_page_token = page_result.next_page_token;
                        snapshot.history_state.availability =
                            crate::EngineHistoryAvailability::Available;
                        snapshot.history_state.refresh_state =
                            crate::EngineHistoryRefreshState::Idle;
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::DeleteHistoryEntry { history_id } => {
                let result = match Self::history_context(snapshot, self.history_port.clone()) {
                    Ok((identity, port)) if !history_id.trim().is_empty() => {
                        let result = port
                            .delete_entry(&identity.history_identity(), history_id)
                            .await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                    }
                    Ok(_) => Err(EngineError::new(
                        EngineErrorType::InvalidInput,
                        "history id is required",
                        false,
                    )),
                    Err(error) => Err(error),
                };
                match result {
                    Ok(()) => {
                        snapshot
                            .history_entries
                            .retain(|entry| entry.id != *history_id);
                        self.invalidate_history_pages(snapshot);
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {
                        snapshot
                            .history_entries
                            .retain(|entry| entry.id != *history_id);
                        self.invalidate_history_pages(snapshot);
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            EngineCommandType::ClearHistory => {
                let result = match Self::history_context(snapshot, self.history_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port.clear(&identity.history_identity()).await;
                        self.history_result_for_current_identity(snapshot, &identity, result)
                    }
                    Err(error) => Err(error),
                };
                match result {
                    Ok(deleted_count) => {
                        snapshot.history_deleted_count = deleted_count;
                        snapshot.history_entries.clear();
                        snapshot.history_next_page_token = None;
                        self.invalidate_history_pages(snapshot);
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {
                        snapshot.history_entries.clear();
                        snapshot.history_next_page_token = None;
                        self.invalidate_history_pages(snapshot);
                    }
                    Err(error) => snapshot.last_error = Some(error),
                }
            }
            _ => unreachable!("history dispatcher received a non-history command"),
        }
    }

    pub(super) async fn record_playback_completion(
        &mut self,
        payload: Option<&str>,
        snapshot: &mut EngineSnapshot,
    ) {
        if snapshot.history_settings.is_none() {
            if AuthIdentity::from_state(&snapshot.auth_state).is_none() {
                return;
            }
            let result = match Self::history_context(snapshot, self.history_port.clone()) {
                Ok((identity, port)) => {
                    let result = port.get_settings(&identity.history_identity()).await;
                    self.history_result_for_current_identity(snapshot, &identity, result)
                        .map(|settings| (identity, settings))
                }
                Err(error) => Err(error),
            };
            match result {
                Ok((identity, settings)) => {
                    self.history_projection_identity = Some(identity);
                    snapshot.history_settings = Some(settings);
                    snapshot.history_state.availability =
                        crate::EngineHistoryAvailability::Available;
                }
                Err(error) => {
                    snapshot.last_error = Some(error);
                    return;
                }
            }
        }
        if !snapshot
            .history_settings
            .is_some_and(|settings| settings.enabled)
        {
            return;
        }
        let result = (|| {
            let payload: PlaybackCompletedPayload =
                serde_json::from_str(payload.unwrap_or_default()).map_err(|_| {
                    EngineError::new(
                        EngineErrorType::InvalidInput,
                        "invalid playback completion payload",
                        false,
                    )
                })?;
            if payload.version != 1 {
                return Err(EngineError::new(
                    EngineErrorType::InvalidInput,
                    "unsupported playback completion payload",
                    false,
                ));
            }
            crate::EnginePlaybackRecord::new(
                payload.track_id,
                payload.duration_ms,
                payload.completion_ratio,
            )
        })();
        let record = match result {
            Ok(record) => record,
            Err(error) => {
                snapshot.last_error = Some(error);
                return;
            }
        };
        let result = match Self::history_context(snapshot, self.history_port.clone()) {
            Ok((identity, port)) => {
                let result = port.record(&identity.history_identity(), record).await;
                self.history_result_for_current_identity(snapshot, &identity, result)
            }
            Err(error) => Err(error),
        };
        match result {
            Ok(true) => self.invalidate_history_pages(snapshot),
            Ok(false) => {}
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    fn bounded_history_page_request(
        mut page: crate::EnginePageRequest,
    ) -> crate::EnginePageRequest {
        page.page_size = Self::bounded_history_page_size(page.page_size);
        page
    }

    fn bounded_history_page_size(page_size: u32) -> u32 {
        if page_size == 0 {
            DEFAULT_HISTORY_PAGE_SIZE
        } else {
            page_size.min(MAX_HISTORY_PAGE_SIZE)
        }
    }

    fn invalidate_history_pages(&mut self, snapshot: &mut EngineSnapshot) {
        snapshot.history_state.generation = snapshot.history_state.generation.saturating_add(1);
        snapshot.history_state.refresh_state = crate::EngineHistoryRefreshState::Idle;
        snapshot.history_entries.clear();
        snapshot.history_next_page_token = None;
        self.history_operation = None;
    }

    fn history_context(
        snapshot: &EngineSnapshot,
        port: Option<Arc<dyn crate::HistoryPort>>,
    ) -> Result<(AuthIdentity, Arc<dyn crate::HistoryPort>), EngineError> {
        let identity = AuthIdentity::from_state(&snapshot.auth_state)
            .ok_or_else(Self::history_login_required)?;
        let port = port.ok_or_else(|| {
            EngineError::new(
                EngineErrorType::FailedPrecondition,
                "history service is not configured",
                false,
            )
        })?;
        Ok((identity, port))
    }

    fn history_result_for_current_identity<T>(
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
            self.history_projection_identity = None;
            self.history_operation = None;
            Self::clear_history_projection(snapshot);
            Err(Self::history_login_required())
        }
    }

    fn history_login_required() -> EngineError {
        EngineError::new(
            EngineErrorType::LoginRequired,
            "history operation requires the current authenticated session",
            false,
        )
    }
}
