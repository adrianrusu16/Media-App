use crate::engine::core::PrefetchedOperation;
use crate::model::command::{EngineCommand, EngineCommandType};

use super::super::ids::{CommandId, OperationId};
use super::super::operation::{
    EngineOperation, EngineOperationCompletion, EngineOperationRequest, EngineOperationResult,
    OperationGeneration,
};
use super::super::protocol::{ActorOutcomeStatus, CancellationReason};
use super::{EngineActorState, account_identity, history_identity, playlist_identity};

impl EngineActorState {
    /// Decides whether a command's remote half can run off-actor. Returns
    /// `None` whenever the inline path must own the command instead — either
    /// because it needs no remote call, or because the engine lacks the port
    /// or catalog state the worker would depend on. Keeping that check here
    /// means a launched operation always has a worker that can complete it.
    pub(super) fn prepare_operation(
        &mut self,
        command_id: CommandId,
        command: &EngineCommand,
        _now_epoch_millis: u64,
    ) -> Option<EngineOperation> {
        match &command.command_type {
            EngineCommandType::GetAccount => {
                let identity = account_identity(&self.auth_provider.get())?;
                self.engine.actor_account_port()?;
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
                // Mirror the inline lookup: without a live continuation the
                // engine reports InvalidInput, which only the inline path does.
                let (query, page_size, page_token) =
                    self.engine.actor_catalog_continuation(operation_id)?;
                let page = crate::EnginePageRequest {
                    page_size,
                    page_token: Some(page_token),
                };
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::Search(self.generations.search),
                    EngineOperationRequest::SearchPage {
                        query,
                        page,
                        catalog_operation_id: Some(operation_id.clone()),
                    },
                ))
            }
            EngineCommandType::ListPlaylists { page } => {
                let identity = playlist_identity(&self.auth_provider.get())?;
                self.engine.actor_playlist_port()?;
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
                self.engine.actor_history_port()?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistorySettings { identity },
                ))
            }
            EngineCommandType::PlayMediaById { media_id } => {
                // Only the PlaybackPort branch is remote. Without it the inline
                // path still has its source_uri and AudioSourceClient fallbacks.
                self.engine.actor_playback_port()?;
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

    /// Runs the remote half of an operation on the runtime rather than the
    /// actor. Returns false when no worker could be launched, in which case the
    /// caller must fall back to inline dispatch instead of registering a
    /// pending operation that would never complete.
    pub(super) fn spawn_operation_worker(&self, operation: &EngineOperation) -> bool {
        let operation = operation.clone();
        let completion_tx = self.operation_completion_tx.clone();

        match operation.request.clone() {
            EngineOperationRequest::AccountProjection { identity } => {
                let Some(port) = self.engine.actor_account_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .get_account(&identity)
                        .await
                        .map(EngineOperationResult::AccountProjection);
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::SearchPage {
                query,
                page,
                catalog_operation_id,
            } => {
                let repository = self.engine.actor_repository();
                tokio::spawn(async move {
                    let result = repository.search_catalog(&query, page).await.map(|paged| {
                        EngineOperationResult::SearchPage {
                            catalog_operation_id: catalog_operation_id.unwrap_or_default(),
                            items: paged.items,
                            next_page_token: paged.next_page_token,
                        }
                    });
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::PlaylistPage { identity, page } => {
                let Some(port) = self.engine.actor_playlist_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result =
                        port.list(&identity, page)
                            .await
                            .map(|paged| EngineOperationResult::PlaylistPage {
                                playlists: paged.items,
                                next_page_token: paged.next_page_token,
                            });
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistorySettings { identity } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .get_settings(&identity)
                        .await
                        .map(EngineOperationResult::HistorySettings);
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::PlaybackResolution { media_id } => {
                let Some(port) = self.engine.actor_playback_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .resolve_playback(&media_id)
                        .await
                        .map(EngineOperationResult::PlaybackResolved);
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
        }

        true
    }

    pub(super) async fn process_operation_completion(
        &mut self,
        completion: EngineOperationCompletion,
    ) {
        let Some(pending) = self.pending_operations.remove(&completion.operation_id) else {
            return;
        };
        let sequence = self.next_sequence();

        if pending.operation.generation != completion.generation
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

        // Re-enter the originating command with the remote result injected, so
        // the split path produces the same snapshot, event and effects the
        // inline path would have produced.
        let prefetched = prefetched_from(&pending.operation.request, completion.result);
        self.engine.set_prefetched_operation(prefetched);
        let outcome = self
            .engine
            .dispatch(pending.command, pending.now_epoch_millis)
            .await;
        self.engine.clear_prefetched_operation();

        let snapshot_revision = self.publish_snapshot(outcome.snapshot.clone(), sequence);
        self.emit_outcome(
            completion.command_id,
            sequence,
            snapshot_revision,
            ActorOutcomeStatus::Completed,
            outcome.snapshot,
            Some(outcome.event),
            outcome.effects,
        )
        .await;
    }
}

/// Maps a worker result onto the prefetch slot the matching inline site reads.
fn prefetched_from(
    request: &EngineOperationRequest,
    result: Result<EngineOperationResult, crate::model::error::EngineError>,
) -> PrefetchedOperation {
    match request {
        EngineOperationRequest::AccountProjection { .. } => {
            PrefetchedOperation::Account(match result {
                Ok(EngineOperationResult::AccountProjection(account)) => Ok(account),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::SearchPage { .. } => PrefetchedOperation::Search(match result {
            Ok(EngineOperationResult::SearchPage {
                items,
                next_page_token,
                ..
            }) => Ok(crate::EnginePagedResult {
                items,
                next_page_token,
            }),
            Ok(_) => Err(mismatched_result()),
            Err(error) => Err(error),
        }),
        EngineOperationRequest::PlaylistPage { .. } => {
            PrefetchedOperation::Playlists(match result {
                Ok(EngineOperationResult::PlaylistPage {
                    playlists,
                    next_page_token,
                }) => Ok(crate::EnginePagedResult {
                    items: playlists,
                    next_page_token,
                }),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::HistorySettings { .. } => {
            PrefetchedOperation::HistorySettings(match result {
                Ok(EngineOperationResult::HistorySettings(settings)) => Ok(settings),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::PlaybackResolution { .. } => {
            PrefetchedOperation::Playback(match result {
                Ok(EngineOperationResult::PlaybackResolved(source)) => Ok(source),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
    }
}

fn mismatched_result() -> crate::model::error::EngineError {
    crate::model::error::EngineError::new(
        crate::model::error::EngineErrorType::MappingDefect,
        "operation completion did not match the launched request",
        false,
    )
}
