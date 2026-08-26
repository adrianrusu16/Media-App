use serde::Deserialize;

use super::*;
use crate::EngineErrorType;

const DEFAULT_HISTORY_PAGE_SIZE: u32 = 40;
const MAX_HISTORY_PAGE_SIZE: u32 = 50;

#[derive(Deserialize)]
struct PlaybackCompletedPayload {
    version: u32,
    #[serde(default)]
    playback_instance_id: Option<u64>,
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
                self.load_history_settings(snapshot).await;
            }
            EngineCommandType::UpdateHistorySettings { enabled } => {
                self.update_history_settings(*enabled, snapshot).await;
            }
            EngineCommandType::ListHistory { page } => {
                self.list_history(page.clone(), snapshot).await;
            }
            EngineCommandType::LoadNextHistoryPage => {
                self.load_next_history_page(snapshot).await;
            }
            EngineCommandType::DeleteHistoryEntry { history_id } => {
                self.delete_history_entry(history_id, snapshot).await;
            }
            EngineCommandType::ClearHistory => {
                self.clear_history(snapshot).await;
            }
            _ => unreachable!("history dispatcher received a non-history command"),
        }
    }

    async fn load_history_settings(&mut self, snapshot: &mut EngineSnapshot) {
        let Some(identity) = AuthIdentity::from_state(&snapshot.auth_state) else {
            self.history_projection_owner = Some(HistoryProjectionOwner::Anonymous);
            snapshot.history_settings = Some(crate::EngineHistorySettings {
                enabled: self.anonymous_history.enabled,
            });
            snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
            return;
        };
        let result = match self.history_port.clone() {
            Some(port) => {
                let result = match self.take_prefetched_history_settings() {
                    Some(prefetched) => prefetched,
                    None => port.get_settings(&identity.history_identity()).await,
                };
                self.history_result_for_current_identity(snapshot, &identity, result)
                    .map(|settings| (identity, port, settings))
            }
            None => Err(Self::history_service_unconfigured()),
        };
        match result {
            Ok((identity, port, settings)) => {
                self.history_projection_owner =
                    Some(HistoryProjectionOwner::Authenticated(identity.clone()));
                snapshot.history_settings = Some(settings);
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
                if settings.enabled {
                    self.reconcile_anonymous_history(&identity, port, snapshot)
                        .await;
                } else {
                    self.clear_anonymous_history_for_authenticated_disabled(snapshot);
                }
            }
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    async fn update_history_settings(&mut self, enabled: bool, snapshot: &mut EngineSnapshot) {
        let Some(identity) = AuthIdentity::from_state(&snapshot.auth_state) else {
            self.anonymous_history.enabled = enabled;
            snapshot.history_deleted_count = if enabled {
                0
            } else {
                self.anonymous_history.clear()
            };
            self.history_projection_owner = Some(HistoryProjectionOwner::Anonymous);
            snapshot.history_settings = Some(crate::EngineHistorySettings { enabled });
            snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
            self.invalidate_history_pages(snapshot);
            return;
        };
        let result = match self.history_port.clone() {
            Some(port) => {
                let result = port
                    .update_settings(&identity.history_identity(), enabled)
                    .await;
                self.history_result_for_current_identity(snapshot, &identity, result)
                    .map(|update| (identity, update))
            }
            None => Err(Self::history_service_unconfigured()),
        };
        match result {
            Ok((identity, update)) => {
                self.history_projection_owner =
                    Some(HistoryProjectionOwner::Authenticated(identity));
                snapshot.history_settings = Some(update.settings);
                snapshot.history_deleted_count = update.deleted_count;
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
                if !update.settings.enabled {
                    self.clear_anonymous_history_for_authenticated_disabled(snapshot);
                }
                self.invalidate_history_pages(snapshot);
            }
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    async fn list_history(
        &mut self,
        page: crate::EnginePageRequest,
        snapshot: &mut EngineSnapshot,
    ) {
        self.history_operation = None;
        snapshot.history_entries.clear();
        snapshot.history_next_page_token = None;
        let page = Self::bounded_history_page_request(page);
        let Some(identity) = AuthIdentity::from_state(&snapshot.auth_state) else {
            match self.anonymous_history_page(page.clone()) {
                Ok(page_result) => {
                    snapshot.history_entries = page_result.items;
                    snapshot.history_next_page_token = page_result.next_page_token;
                    snapshot.history_state.availability =
                        crate::EngineHistoryAvailability::Available;
                    snapshot.history_state.refresh_state = crate::EngineHistoryRefreshState::Idle;
                    self.history_projection_owner = Some(HistoryProjectionOwner::Anonymous);
                    self.history_operation = Some(HistoryOperation {
                        owner: HistoryProjectionOwner::Anonymous,
                        page_size: page.page_size,
                    });
                    if snapshot.history_settings.is_none() {
                        snapshot.history_settings = Some(crate::EngineHistorySettings {
                            enabled: self.anonymous_history.enabled,
                        });
                    }
                }
                Err(error) => snapshot.last_error = Some(error),
            }
            return;
        };
        let result = match self.history_port.clone() {
            Some(port) => {
                self.reconcile_anonymous_history(&identity, port.clone(), snapshot)
                    .await;
                let result = port.list(&identity.history_identity(), page.clone()).await;
                self.history_result_for_current_identity(snapshot, &identity, result)
                    .map(|page_result| (identity, page_result))
            }
            None => Err(Self::history_service_unconfigured()),
        };
        match result {
            Ok((identity, page_result)) => {
                snapshot.history_entries = page_result.items;
                snapshot.history_next_page_token = page_result.next_page_token;
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
                snapshot.history_state.refresh_state = crate::EngineHistoryRefreshState::Idle;
                let owner = HistoryProjectionOwner::Authenticated(identity);
                self.history_projection_owner = Some(owner.clone());
                self.history_operation = Some(HistoryOperation {
                    owner,
                    page_size: page.page_size,
                });
            }
            Err(error) if error.error_type == EngineErrorType::NotFound => {}
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    async fn load_next_history_page(&mut self, snapshot: &mut EngineSnapshot) {
        let owner = HistoryProjectionOwner::current(&snapshot.auth_state);
        let result = match (
            owner.clone(),
            self.history_operation.clone(),
            snapshot.history_next_page_token.clone(),
            self.history_port.clone(),
        ) {
            (
                HistoryProjectionOwner::Authenticated(identity),
                Some(operation),
                Some(token),
                Some(port),
            ) if operation.owner == HistoryProjectionOwner::Authenticated(identity.clone()) => {
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
            (HistoryProjectionOwner::Anonymous, Some(operation), Some(token), _)
                if operation.owner == HistoryProjectionOwner::Anonymous =>
            {
                self.anonymous_history_page(crate::EnginePageRequest {
                    page_size: Self::bounded_history_page_size(operation.page_size),
                    page_token: Some(token),
                })
            }
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
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
                snapshot.history_state.refresh_state = crate::EngineHistoryRefreshState::Idle;
            }
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    async fn delete_history_entry(&mut self, history_id: &str, snapshot: &mut EngineSnapshot) {
        if history_id.trim().is_empty() {
            snapshot.last_error = Some(EngineError::new(
                EngineErrorType::InvalidInput,
                "history id is required",
                false,
            ));
            return;
        }
        if AuthIdentity::from_state(&snapshot.auth_state).is_none() {
            self.delete_anonymous_history_entry(history_id, snapshot);
            return;
        }
        let result = match Self::history_context(snapshot, self.history_port.clone()) {
            Ok((identity, port)) => {
                let result = port
                    .delete_entry(&identity.history_identity(), history_id)
                    .await;
                self.history_result_for_current_identity(snapshot, &identity, result)
            }
            Err(error) => Err(error),
        };
        match result {
            Ok(()) => {
                snapshot
                    .history_entries
                    .retain(|entry| entry.id != history_id);
                self.invalidate_history_pages(snapshot);
            }
            Err(error) if error.error_type == EngineErrorType::NotFound => {
                snapshot
                    .history_entries
                    .retain(|entry| entry.id != history_id);
                self.invalidate_history_pages(snapshot);
            }
            Err(error) => snapshot.last_error = Some(error),
        }
    }

    async fn clear_history(&mut self, snapshot: &mut EngineSnapshot) {
        if AuthIdentity::from_state(&snapshot.auth_state).is_none() {
            snapshot.history_deleted_count = self.anonymous_history.clear();
            self.invalidate_history_pages(snapshot);
            return;
        }
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

    pub(super) async fn record_playback_completion(
        &mut self,
        payload: Option<&str>,
        snapshot: &mut EngineSnapshot,
    ) {
        let record = match Self::qualified_history_record(payload) {
            Ok(record) => record,
            Err(error) => {
                warn!(
                    error_type = ?error.error_type,
                    "Rejected malformed playback completion history payload"
                );
                snapshot.last_error = Some(error);
                return;
            }
        };
        if AuthIdentity::from_state(&snapshot.auth_state).is_none() {
            if snapshot.history_settings.is_none() {
                snapshot.history_settings = Some(crate::EngineHistorySettings {
                    enabled: self.anonymous_history.enabled,
                });
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
            }
            if !self.anonymous_history.enabled {
                info!("Skipping anonymous playback history record because history is disabled");
                return;
            }
            self.record_anonymous_history_event(record, snapshot);
            return;
        }
        if snapshot.history_settings.is_none() {
            let result = match Self::history_context(snapshot, self.history_port.clone()) {
                Ok((identity, port)) => {
                    let result = port.get_settings(&identity.history_identity()).await;
                    self.history_result_for_current_identity(snapshot, &identity, result)
                        .map(|settings| (identity, port, settings))
                }
                Err(error) => Err(error),
            };
            match result {
                Ok((identity, port, settings)) => {
                    self.history_projection_owner =
                        Some(HistoryProjectionOwner::Authenticated(identity.clone()));
                    snapshot.history_settings = Some(settings);
                    snapshot.history_state.availability =
                        crate::EngineHistoryAvailability::Available;
                    if settings.enabled {
                        self.reconcile_anonymous_history(&identity, port, snapshot)
                            .await;
                    } else {
                        self.clear_anonymous_history_for_authenticated_disabled(snapshot);
                    }
                }
                Err(error) => {
                    warn!(
                        error_type = ?error.error_type,
                        "Could not load history settings before recording playback completion"
                    );
                    snapshot.last_error = Some(error);
                    return;
                }
            }
        }
        if !snapshot
            .history_settings
            .is_some_and(|settings| settings.enabled)
        {
            self.clear_anonymous_history_for_authenticated_disabled(snapshot);
            info!("Skipping playback history record because history is disabled");
            return;
        }
        debug!(
            duration_millis = record.duration_millis,
            completion_ratio = %record.completion_ratio,
            "Recording playback completion in history"
        );
        let result = match Self::history_context(snapshot, self.history_port.clone()) {
            Ok((identity, port)) => {
                self.reconcile_anonymous_history(&identity, port.clone(), snapshot)
                    .await;
                let result = port.record(&identity.history_identity(), record).await;
                self.history_result_for_current_identity(snapshot, &identity, result)
            }
            Err(error) => Err(error),
        };
        match result {
            Ok(true) => {
                self.invalidate_history_pages(snapshot);
                info!(
                    history_generation = snapshot.history_state.generation,
                    "Playback history recorded and history projection invalidated"
                );
            }
            Ok(false) => {
                info!("Playback history backend declined the completion record");
            }
            Err(error) => {
                warn!(
                    error_type = ?error.error_type,
                    "Playback history record failed"
                );
                snapshot.last_error = Some(error);
            }
        }
    }

    fn qualified_history_record(
        payload: Option<&str>,
    ) -> Result<crate::EnginePlaybackRecord, EngineError> {
        let payload: PlaybackCompletedPayload = serde_json::from_str(payload.unwrap_or_default())
            .map_err(|_| {
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
        let _playback_instance_id = payload.playback_instance_id;
        crate::EnginePlaybackRecord::new(
            payload.track_id,
            payload.duration_ms,
            payload.completion_ratio,
        )
    }

    fn record_anonymous_history_event(
        &mut self,
        record: crate::EnginePlaybackRecord,
        snapshot: &mut EngineSnapshot,
    ) {
        let sequence = self.anonymous_history.next_sequence;
        self.anonymous_history.next_sequence =
            self.anonymous_history.next_sequence.saturating_add(1);
        let played_at_epoch_millis = snapshot.updated_at_epoch_millis;
        let entry = crate::EngineHistoryEntry {
            id: format!("anonymous-history-{played_at_epoch_millis}-{sequence}"),
            played_at_epoch_millis: Some(played_at_epoch_millis),
            duration_millis: record.duration_millis,
            completion_ratio: record.completion_ratio,
            track: Some(Self::anonymous_history_track(record, snapshot)),
        };
        self.anonymous_history.entries.push_front(entry);
        while self.anonymous_history.entries.len() > self.anonymous_history.max_entries {
            self.anonymous_history.entries.pop_back();
        }
        self.history_projection_owner = Some(HistoryProjectionOwner::Anonymous);
        self.invalidate_history_pages(snapshot);
        info!(
            history_generation = snapshot.history_state.generation,
            "Anonymous playback history recorded and history projection invalidated"
        );
    }

    fn anonymous_history_track(
        record: crate::EnginePlaybackRecord,
        snapshot: &EngineSnapshot,
    ) -> crate::EngineTrack {
        let artist_name = snapshot
            .artist
            .clone()
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| "Unknown artist".to_owned());
        crate::EngineTrack {
            id: record.track_id.clone(),
            title: snapshot
                .title
                .clone()
                .filter(|value| !value.trim().is_empty())
                .unwrap_or(record.track_id),
            artist: crate::EngineArtist {
                id: artist_name.clone(),
                name: artist_name,
            },
            album: snapshot.album.as_ref().map(|title| crate::EngineAlbum {
                id: title.clone(),
                title: title.clone(),
            }),
            duration_millis: snapshot.duration_millis.unwrap_or(record.duration_millis),
            explicit: false,
            artwork_id: snapshot.thumbnail_url.clone(),
            genres: Vec::new(),
        }
    }

    async fn reconcile_anonymous_history(
        &mut self,
        identity: &AuthIdentity,
        port: Arc<dyn crate::HistoryPort>,
        snapshot: &mut EngineSnapshot,
    ) {
        if self.anonymous_history_reconciliation_in_flight || self.anonymous_history.is_empty() {
            return;
        }
        self.anonymous_history_reconciliation_in_flight = true;
        let result = self
            .reconcile_anonymous_history_impl(identity, port, snapshot)
            .await;
        self.anonymous_history_reconciliation_in_flight = false;
        if let Err(error) = result {
            warn!(
                error_type = ?error.error_type,
                "Anonymous history reconciliation failed; pending entries retained"
            );
            snapshot.last_error = Some(error);
        }
    }

    async fn reconcile_anonymous_history_impl(
        &mut self,
        identity: &AuthIdentity,
        port: Arc<dyn crate::HistoryPort>,
        snapshot: &mut EngineSnapshot,
    ) -> Result<(), EngineError> {
        let settings = match snapshot.history_settings {
            Some(settings) => settings,
            None => {
                let result = port.get_settings(&identity.history_identity()).await;
                let settings =
                    self.history_result_for_current_identity(snapshot, identity, result)?;
                snapshot.history_settings = Some(settings);
                settings
            }
        };
        if !settings.enabled {
            self.clear_anonymous_history_for_authenticated_disabled(snapshot);
            return Ok(());
        }
        let pending: Vec<_> = self
            .anonymous_history
            .entries
            .iter()
            .cloned()
            .rev()
            .collect();
        let mut promoted_ids = Vec::new();
        for entry in pending {
            let Some(record) = Self::record_from_history_entry(&entry) else {
                promoted_ids.push(entry.id);
                continue;
            };
            let result = port.record(&identity.history_identity(), record).await;
            if self.history_result_for_current_identity(snapshot, identity, result)? {
                promoted_ids.push(entry.id);
            }
        }
        if !promoted_ids.is_empty() {
            self.anonymous_history
                .entries
                .retain(|entry| !promoted_ids.contains(&entry.id));
            self.invalidate_history_pages(snapshot);
        }
        Ok(())
    }

    fn record_from_history_entry(
        entry: &crate::EngineHistoryEntry,
    ) -> Option<crate::EnginePlaybackRecord> {
        let track_id = entry.track.as_ref()?.id.clone();
        crate::EnginePlaybackRecord::new(track_id, entry.duration_millis, entry.completion_ratio)
            .ok()
    }

    fn clear_anonymous_history_for_authenticated_disabled(
        &mut self,
        snapshot: &mut EngineSnapshot,
    ) {
        if self.anonymous_history.clear() > 0 {
            self.invalidate_history_pages(snapshot);
        }
    }

    fn anonymous_history_page(
        &self,
        page: crate::EnginePageRequest,
    ) -> Result<crate::EnginePagedResult<crate::EngineHistoryEntry>, EngineError> {
        let page_size = Self::bounded_history_page_size(page.page_size) as usize;
        let offset = match page.page_token {
            Some(token) => Self::anonymous_history_offset(token)?,
            None => 0,
        };
        let items = self
            .anonymous_history
            .entries
            .iter()
            .skip(offset)
            .take(page_size)
            .cloned()
            .collect::<Vec<_>>();
        let next_offset = offset.saturating_add(items.len());
        let next_page_token = (next_offset < self.anonymous_history.entries.len())
            .then(|| crate::EnginePageToken::new(format!("anonymous:{next_offset}")))
            .transpose()?;
        Ok(crate::EnginePagedResult {
            items,
            next_page_token,
        })
    }

    fn anonymous_history_offset(token: crate::EnginePageToken) -> Result<usize, EngineError> {
        token
            .as_str()
            .strip_prefix("anonymous:")
            .and_then(|value| value.parse::<usize>().ok())
            .ok_or_else(|| {
                EngineError::new(
                    EngineErrorType::InvalidInput,
                    "invalid anonymous history page token",
                    false,
                )
            })
    }

    fn delete_anonymous_history_entry(&mut self, history_id: &str, snapshot: &mut EngineSnapshot) {
        let before = self.anonymous_history.entries.len();
        self.anonymous_history
            .entries
            .retain(|entry| entry.id != history_id);
        snapshot
            .history_entries
            .retain(|entry| entry.id != history_id);
        if self.anonymous_history.entries.len() != before {
            self.invalidate_history_pages(snapshot);
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
        let port = port.ok_or_else(Self::history_service_unconfigured)?;
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
            self.history_projection_owner = None;
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

    fn history_service_unconfigured() -> EngineError {
        EngineError::new(
            EngineErrorType::FailedPrecondition,
            "history service is not configured",
            false,
        )
    }
}
