use std::sync::Arc;

use crate::engine::core::{PrefetchedHistoryPage, PrefetchedHistorySettings, PrefetchedOperation};
use crate::model::command::{EngineCommand, EngineCommandType};
use tracing::info;

use super::super::ids::{CommandId, OperationId};
use super::super::operation::{
    EngineOperation, EngineOperationCompletion, EngineOperationRequest, EngineOperationResult,
    OperationGeneration,
};
use super::super::protocol::{ActorOutcomeStatus, CancellationReason};
use super::{
    EngineActorState, OutcomeParts, account_identity, history_identity, library_identity,
    playlist_identity,
};

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
                    EngineOperationRequest::HistorySettings {
                        identity,
                        pending_anonymous: self.engine.actor_pending_anonymous_history(),
                    },
                ))
            }
            EngineCommandType::UpdateHistorySettings { enabled } => {
                let identity = history_identity(&self.auth_provider.get())?;
                self.engine.actor_history_port()?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistorySettingsUpdate {
                        identity,
                        enabled: *enabled,
                    },
                ))
            }
            EngineCommandType::ListHistory { page } => {
                let identity = history_identity(&self.auth_provider.get())?;
                self.engine.actor_history_port()?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistoryPage {
                        identity,
                        page: self.engine.actor_bounded_history_page(page.clone()),
                        pending_anonymous: self.engine.actor_pending_anonymous_history(),
                        settings_enabled: self.engine.actor_history_settings_enabled(),
                    },
                ))
            }
            EngineCommandType::LoadNextHistoryPage => {
                let identity = history_identity(&self.auth_provider.get())?;
                self.engine.actor_history_port()?;
                let (continuation_identity, page_size, page_token) =
                    self.engine.actor_history_continuation()?;
                if identity != continuation_identity {
                    return None;
                }
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(self.generations.history),
                    EngineOperationRequest::HistoryPage {
                        identity,
                        page: crate::EnginePageRequest {
                            page_size,
                            page_token: Some(page_token),
                        },
                        pending_anonymous: Vec::new(),
                        settings_enabled: self.engine.actor_history_settings_enabled(),
                    },
                ))
            }
            EngineCommandType::DeleteHistoryEntry { history_id } => {
                if history_id.trim().is_empty() {
                    return None;
                }
                let identity = history_identity(&self.auth_provider.get())?;
                self.engine.actor_history_port()?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistoryDelete {
                        identity,
                        history_id: history_id.clone(),
                    },
                ))
            }
            EngineCommandType::ClearHistory => {
                let identity = history_identity(&self.auth_provider.get())?;
                self.engine.actor_history_port()?;
                let generation = self.bump_history_generation();
                Some(self.allocate_operation(
                    command_id,
                    OperationGeneration::History(generation),
                    EngineOperationRequest::HistoryClear { identity },
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
            EngineCommandType::ListSavedTracks { page } => {
                self.prepare_library_page(command_id, page.clone(), true, true)
            }
            EngineCommandType::ListLikedTracks { page } => {
                self.prepare_library_page(command_id, page.clone(), false, true)
            }
            EngineCommandType::LoadNextSavedTracksPage => {
                self.prepare_library_continuation(command_id, true)
            }
            EngineCommandType::LoadNextLikedTracksPage => {
                self.prepare_library_continuation(command_id, false)
            }
            EngineCommandType::SaveTrack { track_id } => self.prepare_library_mutation(
                command_id,
                track_id,
                crate::EngineLibraryMutation::Save,
            ),
            EngineCommandType::RemoveSavedTrack { track_id } => self.prepare_library_mutation(
                command_id,
                track_id,
                crate::EngineLibraryMutation::RemoveSaved,
            ),
            EngineCommandType::LikeTrack { track_id } => self.prepare_library_mutation(
                command_id,
                track_id,
                crate::EngineLibraryMutation::Like,
            ),
            EngineCommandType::UnlikeTrack { track_id } => self.prepare_library_mutation(
                command_id,
                track_id,
                crate::EngineLibraryMutation::Unlike,
            ),
            _ => None,
        }
    }

    fn prepare_library_page(
        &mut self,
        command_id: CommandId,
        page: crate::EnginePageRequest,
        saved: bool,
        bump: bool,
    ) -> Option<EngineOperation> {
        let identity = library_identity(&self.auth_provider.get())?;
        self.engine.actor_library_port()?;
        let generation = if bump {
            self.bump_library_generation()
        } else {
            self.generations.library
        };
        Some(self.allocate_operation(
            command_id,
            OperationGeneration::Library(generation),
            EngineOperationRequest::LibraryPage {
                identity,
                page,
                saved,
            },
        ))
    }

    fn prepare_library_continuation(
        &mut self,
        command_id: CommandId,
        saved: bool,
    ) -> Option<EngineOperation> {
        let identity = library_identity(&self.auth_provider.get())?;
        self.engine.actor_library_port()?;
        let (continuation_identity, page_size, page_token) =
            self.engine.actor_library_continuation(saved)?;
        if identity != continuation_identity {
            return None;
        }
        self.prepare_library_page(
            command_id,
            crate::EnginePageRequest {
                page_size,
                page_token: Some(page_token),
            },
            saved,
            false,
        )
    }

    fn prepare_library_mutation(
        &mut self,
        command_id: CommandId,
        track_id: &str,
        mutation: crate::EngineLibraryMutation,
    ) -> Option<EngineOperation> {
        if track_id.trim().is_empty() {
            return None;
        }
        let identity = library_identity(&self.auth_provider.get())?;
        self.engine.actor_library_port()?;
        let generation = self.bump_library_generation();
        Some(self.allocate_operation(
            command_id,
            OperationGeneration::Library(generation),
            EngineOperationRequest::LibraryMutation {
                identity,
                track_id: track_id.to_owned(),
                mutation,
            },
        ))
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
                    let result = port.list(&identity, page).await.map(|paged| {
                        EngineOperationResult::PlaylistPage {
                            playlists: paged.items,
                            next_page_token: paged.next_page_token,
                        }
                    });
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistorySettings {
                identity,
                pending_anonymous,
            } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = match port.get_settings(&identity).await {
                        Ok(settings) => {
                            let reconciliation = if settings.enabled {
                                reconcile_anonymous_entries(
                                    port,
                                    &identity,
                                    pending_anonymous,
                                    Some(true),
                                )
                                .await
                            } else {
                                crate::HistoryReconciliation::default()
                            };
                            Ok(EngineOperationResult::HistorySettings {
                                settings,
                                reconciliation,
                            })
                        }
                        Err(error) => Err(error),
                    };
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistorySettingsUpdate { identity, enabled } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .update_settings(&identity, enabled)
                        .await
                        .map(EngineOperationResult::HistorySettingsUpdate);
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistoryPage {
                identity,
                page,
                pending_anonymous,
                settings_enabled,
            } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let reconciliation = reconcile_anonymous_entries(
                        Arc::clone(&port),
                        &identity,
                        pending_anonymous,
                        settings_enabled,
                    )
                    .await;
                    let result = port.list(&identity, page).await.map(|paged| {
                        EngineOperationResult::HistoryPage {
                            items: paged.items,
                            next_page_token: paged.next_page_token,
                            reconciliation,
                        }
                    });
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistoryDelete {
                identity,
                history_id,
            } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .delete_entry(&identity, &history_id)
                        .await
                        .map(|()| EngineOperationResult::HistoryDeleted);
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::HistoryClear { identity } => {
                let Some(port) = self.engine.actor_history_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = port
                        .clear(&identity)
                        .await
                        .map(EngineOperationResult::HistoryCleared);
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
            EngineOperationRequest::LibraryPage {
                identity,
                page,
                saved,
            } => {
                let Some(port) = self.engine.actor_library_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let listed = if saved {
                        port.list_saved(&identity, page).await
                    } else {
                        port.list_liked(&identity, page).await
                    };
                    let result = listed.map(|paged| EngineOperationResult::LibraryPage {
                        items: paged.items,
                        next_page_token: paged.next_page_token,
                    });
                    let _ = completion_tx.send(operation.completion(result)).await;
                });
            }
            EngineOperationRequest::LibraryMutation {
                identity,
                track_id,
                mutation,
            } => {
                let Some(port) = self.engine.actor_library_port() else {
                    return false;
                };
                tokio::spawn(async move {
                    let result = match mutation {
                        crate::EngineLibraryMutation::Save => port
                            .save(&identity, &track_id)
                            .await
                            .map(|item| EngineOperationResult::LibraryMutation(Some(item))),
                        crate::EngineLibraryMutation::RemoveSaved => port
                            .remove_saved(&identity, &track_id)
                            .await
                            .map(|()| EngineOperationResult::LibraryMutation(None)),
                        crate::EngineLibraryMutation::Like => port
                            .like(&identity, &track_id)
                            .await
                            .map(|item| EngineOperationResult::LibraryMutation(Some(item))),
                        crate::EngineLibraryMutation::Unlike => port
                            .unlike(&identity, &track_id)
                            .await
                            .map(|()| EngineOperationResult::LibraryMutation(None)),
                    };
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
        info!(
            operation_id = completion.operation_id.get(),
            command_id = completion.command_id.get(),
            command_type = pending.command.command_type.as_wire(),
            ok = completion.result.is_ok(),
            "engine.operation.complete"
        );
        let sequence = self.next_sequence();

        if pending.operation.generation != completion.generation
            || !self.generations.is_current(completion.generation)
        {
            let snapshot_revision = self.current_revision();
            self.emit_outcome(OutcomeParts {
                command_id: completion.command_id,
                message_sequence: sequence,
                snapshot_revision,
                status: ActorOutcomeStatus::Cancelled(CancellationReason::Superseded),
                snapshot: self.current_snapshot(),
                event: None,
                effects: Vec::new(),
            })
            .await;
            return;
        }

        // Re-enter the originating command with the remote result injected, so
        // the split path produces the same snapshot, event and effects the
        // inline path would have produced. Use the latest actor clock, not the
        // original submit time: a stale now_epoch_millis rebases progress
        // interpolation and can reload the previous track timestamp.
        let now_epoch_millis = pending.now_epoch_millis.max(self.latest_epoch_millis);
        info!(
            command_type = pending.command.command_type.as_wire(),
            pending_now_epoch_millis = pending.now_epoch_millis,
            dispatch_now_epoch_millis = now_epoch_millis,
            operation_id = pending.operation.operation_id.get(),
            "engine.operation.redispatch"
        );
        let prefetched = prefetched_from(&pending.operation.request, completion.result);
        self.engine.set_prefetched_operation(prefetched);
        let outcome = self
            .engine
            .dispatch(pending.command, now_epoch_millis)
            .await;
        self.engine.clear_prefetched_operation();

        let snapshot_revision = self.publish_snapshot(outcome.snapshot.clone(), sequence);
        self.emit_outcome(OutcomeParts {
            command_id: completion.command_id,
            message_sequence: sequence,
            snapshot_revision,
            status: ActorOutcomeStatus::Completed,
            snapshot: outcome.snapshot,
            event: Some(outcome.event),
            effects: outcome.effects,
        })
        .await;
    }
}

async fn reconcile_anonymous_entries(
    port: Arc<dyn crate::HistoryPort>,
    identity: &crate::EngineHistoryIdentity,
    pending: Vec<crate::EngineHistoryEntry>,
    known_enabled: Option<bool>,
) -> crate::HistoryReconciliation {
    if pending.is_empty() {
        return crate::HistoryReconciliation::default();
    }
    let enabled = match known_enabled {
        Some(enabled) => enabled,
        None => match port.get_settings(identity).await {
            Ok(settings) => settings.enabled,
            Err(error) => {
                return crate::HistoryReconciliation {
                    error: Some(error),
                    ..crate::HistoryReconciliation::default()
                };
            }
        },
    };
    if !enabled {
        return crate::HistoryReconciliation {
            clear_anonymous: true,
            ..crate::HistoryReconciliation::default()
        };
    }
    let mut promoted_entry_ids = Vec::new();
    for entry in pending {
        let Some(record) = entry.to_playback_record() else {
            promoted_entry_ids.push(entry.id);
            continue;
        };
        match port.record(identity, record).await {
            Ok(true) => promoted_entry_ids.push(entry.id),
            Ok(false) => {}
            Err(error) => {
                return crate::HistoryReconciliation {
                    promoted_entry_ids,
                    clear_anonymous: false,
                    error: Some(error),
                };
            }
        }
    }
    crate::HistoryReconciliation {
        promoted_entry_ids,
        clear_anonymous: false,
        error: None,
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
                Ok(EngineOperationResult::HistorySettings {
                    settings,
                    reconciliation,
                }) => PrefetchedHistorySettings {
                    settings: Ok(settings),
                    reconciliation,
                },
                Ok(_) => PrefetchedHistorySettings {
                    settings: Err(mismatched_result()),
                    reconciliation: crate::HistoryReconciliation::default(),
                },
                Err(error) => PrefetchedHistorySettings {
                    settings: Err(error),
                    reconciliation: crate::HistoryReconciliation::default(),
                },
            })
        }
        EngineOperationRequest::HistorySettingsUpdate { .. } => {
            PrefetchedOperation::HistorySettingsUpdate(match result {
                Ok(EngineOperationResult::HistorySettingsUpdate(update)) => Ok(update),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::HistoryPage { .. } => {
            PrefetchedOperation::HistoryPage(match result {
                Ok(EngineOperationResult::HistoryPage {
                    items,
                    next_page_token,
                    reconciliation,
                }) => PrefetchedHistoryPage {
                    page: Ok(crate::EnginePagedResult {
                        items,
                        next_page_token,
                    }),
                    reconciliation,
                },
                Ok(_) => PrefetchedHistoryPage {
                    page: Err(mismatched_result()),
                    reconciliation: crate::HistoryReconciliation::default(),
                },
                Err(error) => PrefetchedHistoryPage {
                    page: Err(error),
                    reconciliation: crate::HistoryReconciliation::default(),
                },
            })
        }
        EngineOperationRequest::HistoryDelete { .. } => {
            PrefetchedOperation::HistoryDelete(match result {
                Ok(EngineOperationResult::HistoryDeleted) => Ok(()),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::HistoryClear { .. } => {
            PrefetchedOperation::HistoryClear(match result {
                Ok(EngineOperationResult::HistoryCleared(deleted)) => Ok(deleted),
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
        EngineOperationRequest::LibraryPage { .. } => {
            PrefetchedOperation::LibraryPage(match result {
                Ok(EngineOperationResult::LibraryPage {
                    items,
                    next_page_token,
                }) => Ok(crate::EnginePagedResult {
                    items,
                    next_page_token,
                }),
                Ok(_) => Err(mismatched_result()),
                Err(error) => Err(error),
            })
        }
        EngineOperationRequest::LibraryMutation { .. } => {
            PrefetchedOperation::LibraryMutation(match result {
                Ok(EngineOperationResult::LibraryMutation(item)) => Ok(item),
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
