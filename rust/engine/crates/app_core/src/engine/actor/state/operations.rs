use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::snapshot::EngineSnapshot;

use super::super::ids::{CommandId, OperationId};
use super::super::operation::{
    EngineOperation, EngineOperationCompletion, EngineOperationRequest, EngineOperationResult,
    OperationGeneration,
};
use super::super::protocol::{ActorOutcomeStatus, CancellationReason};
use super::{EngineActorState, account_identity, history_identity, playlist_identity};

impl EngineActorState {
    pub(super) fn prepare_operation(
        &mut self,
        command_id: CommandId,
        command: &EngineCommand,
        _now_epoch_millis: u64,
    ) -> Option<EngineOperation> {
        match &command.command_type {
            EngineCommandType::GetAccount => {
                let identity = account_identity(&self.auth_provider.get())?;
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Account(self.generations.account),
                    EngineOperationRequest::AccountProjection { identity },
                ))
            }
            EngineCommandType::SearchCatalog { query, page } => {
                let generation = self.bump_search_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Search(generation),
                    EngineOperationRequest::SearchPage {
                        query: query.clone(),
                        page: page.clone(),
                        catalog_operation_id: None,
                    },
                ))
            }
            EngineCommandType::LoadNextCatalogPage { operation_id } => {
                let page = crate::EnginePageRequest {
                    page_size: 20,
                    page_token: self.current_catalog_next_page_token.clone(),
                };
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Search(self.generations.search),
                    EngineOperationRequest::SearchPage {
                        query: String::new(),
                        page,
                        catalog_operation_id: Some(operation_id.clone()),
                    },
                ))
            }
            EngineCommandType::ListPlaylists { page } => {
                let identity = playlist_identity(&self.auth_provider.get())?;
                let generation = self.bump_playlist_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Playlist(generation),
                    EngineOperationRequest::PlaylistPage {
                        identity,
                        page: page.clone(),
                    },
                ))
            }
            EngineCommandType::LoadHistorySettings => {
                let identity = history_identity(&self.auth_provider.get())?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistorySettings { identity },
                ))
            }
            EngineCommandType::PlayMediaById { media_id } => {
                let playback = self.bump_playback_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Playback(playback),
                    EngineOperationRequest::PlaybackResolution {
                        media_id: media_id.clone(),
                    },
                ))
            }
            _ => None,
        }
    }

    pub(super) fn allocate_operation(
        &mut self,
        command_id: CommandId,
        generation: OperationGeneration,
        request: EngineOperationRequest,
    ) -> EngineOperation {
        let operation_id = OperationId::new(self.next_operation_id);
        self.next_operation_id = self.next_operation_id.saturating_add(1);
        EngineOperation {
            operation_id,
            command_id,
            generation,
            request,
        }
    }

    pub(super) fn spawn_operation_worker(&self, operation: &EngineOperation) {
        let EngineOperationRequest::HistorySettings { identity } = operation.request.clone() else {
            return;
        };
        let Some(port) = self.engine.actor_history_port() else {
            return;
        };
        let operation = operation.clone();
        let completion_tx = self.operation_completion_tx.clone();
        tokio::spawn(async move {
            let result = port
                .get_settings(&identity)
                .await
                .map(EngineOperationResult::HistorySettings);
            let _ = completion_tx.send(operation.completion(result)).await;
        });
    }

    pub(super) async fn process_operation_completion(
        &mut self,
        completion: EngineOperationCompletion,
    ) {
        let Some(operation) = self.pending_operations.remove(&completion.operation_id) else {
            return;
        };
        let sequence = self.next_sequence();

        if operation.generation != completion.generation
            || !self.generations.is_current(completion.generation)
        {
            let snapshot_revision = self.current_revision();
            self.emit_outcome(
                completion.command_id,
                sequence,
                snapshot_revision,
                ActorOutcomeStatus::Cancelled(CancellationReason::Superseded),
                self.current_snapshot(),
                None,
                Vec::new(),
            )
            .await;
            return;
        }

        let mut snapshot = self.current_snapshot();
        if let Ok(result) = completion.result {
            self.apply_operation_result(&operation.request, result, &mut snapshot);
        }
        let snapshot_revision = self.publish_snapshot(snapshot.clone(), sequence);
        self.emit_outcome(
            completion.command_id,
            sequence,
            snapshot_revision,
            ActorOutcomeStatus::Completed,
            snapshot,
            None,
            Vec::new(),
        )
        .await;
    }

    pub(super) fn apply_operation_result(
        &mut self,
        request: &EngineOperationRequest,
        result: EngineOperationResult,
        snapshot: &mut EngineSnapshot,
    ) {
        match (request, result) {
            (
                EngineOperationRequest::AccountProjection { .. },
                EngineOperationResult::AccountProjection(account),
            ) => {
                snapshot.protected_account = Some(account);
            }
            (
                EngineOperationRequest::SearchPage { page, .. },
                EngineOperationResult::SearchPage {
                    catalog_operation_id,
                    items,
                    next_page_token,
                },
            ) => {
                if page.page_token.is_some() {
                    snapshot.search_results.extend(items);
                } else {
                    snapshot.search_results = items;
                }
                self.current_catalog_operation_id = Some(catalog_operation_id);
                self.current_catalog_next_page_token = next_page_token;
            }
            (
                EngineOperationRequest::PlaylistPage { .. },
                EngineOperationResult::PlaylistPage {
                    playlists,
                    next_page_token,
                },
            ) => {
                snapshot.playlists = playlists;
                snapshot.playlists_next_page_token = next_page_token;
            }
            (
                EngineOperationRequest::HistorySettings { .. },
                EngineOperationResult::HistorySettings(settings),
            ) => {
                snapshot.history_settings = Some(settings);
                snapshot.history_state.availability = crate::EngineHistoryAvailability::Available;
            }
            (
                EngineOperationRequest::PlaybackResolution { .. },
                EngineOperationResult::PlaybackResolved(source),
            ) => {
                snapshot.media_id = Some(source.track_id.clone());
                snapshot.source_uri = Some(source.url);
                snapshot.mime_type = Some(source.content_type);
                snapshot.duration_millis = Some(source.duration_millis);
                snapshot.playback_expires_at_epoch_millis = Some(source.expires_at_epoch_millis);
            }
            _ => {}
        }
    }
}
