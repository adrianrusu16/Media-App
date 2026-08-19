use super::*;
use crate::{EngineErrorType, MediaItem};

impl Engine {
    pub(super) async fn dispatch_command(
        &mut self,
        command: EngineCommand,
        now_epoch_millis: u64,
    ) -> EngineOutcome {
        info!(
            "Dispatching command: {:?} at {}",
            command.command_type, now_epoch_millis
        );
        let middleware = Arc::clone(&self.middleware);
        if let Err(error) = middleware.before_dispatch(self, &command) {
            let command_wire = command.command_type.as_wire().to_owned();
            self.snapshot = self
                .snapshot
                .clone()
                .with_error(Some(error.clone()))
                .with_busy(false);
            self.sync_auth_state_projection();

            return EngineOutcome {
                snapshot: self.snapshot.clone(),
                event: EngineEvent::command_applied(Some(format!(
                    "{}:rejected_by_middleware",
                    command_wire
                ))),
                effects: Vec::new(),
            };
        }

        self.sync_auth_state_projection();

        let prev_playback_state = self.snapshot.playback_state;

        let next_playback_state =
            StateMachine::next_state_from_command(prev_playback_state, &command.command_type);

        let mut next_snapshot = self
            .snapshot
            .clone()
            .with_playback_state(next_playback_state, now_epoch_millis)
            .with_error(None)
            .with_busy(false);

        if prev_playback_state != PlaybackState::Playing
            && next_playback_state == PlaybackState::Playing
        {
            next_snapshot = next_snapshot.with_progress_tick(now_epoch_millis);
        }

        let mut effects = Vec::new();
        let mut event_message = None;

        match &command.command_type {
            EngineCommandType::StartSession { user_id } => {
                let session_id = format!("session-{}", now_epoch_millis);
                let session =
                    MediaSession::new(session_id.clone(), user_id.clone(), now_epoch_millis);
                next_snapshot = next_snapshot.with_session(Some(session));
                effects.push(EngineEffect::SessionStarted { session_id });
            }
            EngineCommandType::EndSession => {
                next_snapshot = next_snapshot.with_session(None);
                next_snapshot.theme_preference.session_user_id = None;
                effects.push(EngineEffect::SessionEnded);
            }
            EngineCommandType::RefreshBackendStatus => {
                let result = match self.system_port.clone() {
                    Some(port) => port.get_status().await,
                    None => Err(EngineError::new(
                        crate::model::error::EngineErrorType::ServiceUnavailable,
                        "backend is not configured",
                        false,
                    )),
                };
                match result {
                    Ok(status) => {
                        next_snapshot = next_snapshot.with_backend_status(Some(status));
                    }
                    Err(error) => {
                        next_snapshot = next_snapshot.with_error(Some(error));
                    }
                }
            }
            EngineCommandType::SkipNext => {
                if let Some(next_media) = self.queue.next_item().cloned() {
                    match self.resolve_playback_source(&next_media).await {
                        Ok(media) => {
                            next_snapshot =
                                Self::update_media_state(&media, next_snapshot, &mut effects);
                        }
                        Err(error) => {
                            next_snapshot = next_snapshot.with_error(Some(error)).with_busy(false);
                            next_snapshot.playback_state = PlaybackState::Error;
                        }
                    }
                }
            }
            EngineCommandType::SkipPrevious => {
                if let Some(prev_media) = self.queue.previous_item().cloned() {
                    match self.resolve_playback_source(&prev_media).await {
                        Ok(media) => {
                            next_snapshot =
                                Self::update_media_state(&media, next_snapshot, &mut effects);
                        }
                        Err(error) => {
                            next_snapshot = next_snapshot.with_error(Some(error)).with_busy(false);
                            next_snapshot.playback_state = PlaybackState::Error;
                        }
                    }
                }
            }
            EngineCommandType::Play => {
                if next_snapshot.session.is_some() {
                    if self.snapshot.media_id.is_none() {
                        let selected_media = self
                            .queue
                            .current_item()
                            .cloned()
                            .or_else(|| self.queue.next_item().cloned());
                        if let Some(media) = selected_media {
                            match self.resolve_playback_source(&media).await {
                                Ok(media) => {
                                    next_snapshot = Self::update_media_state(
                                        &media,
                                        next_snapshot,
                                        &mut effects,
                                    );
                                }
                                Err(error) => {
                                    next_snapshot =
                                        next_snapshot.with_error(Some(error)).with_busy(false);
                                    next_snapshot.playback_state = PlaybackState::Error;
                                }
                            }
                        }
                    }
                } else {
                    next_snapshot.playback_state = PlaybackState::Idle;
                }
            }
            EngineCommandType::SearchCatalog { query, page } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search_catalog(query, page.clone()).await;
                match results {
                    Ok(result) => {
                        let operation_id = self.allocate_catalog_operation_id(now_epoch_millis);
                        self.catalog_operations.insert(
                            operation_id.clone(),
                            CatalogOperation::Search {
                                query: query.clone(),
                                page_size: page.page_size,
                                next_page_token: result.next_page_token.clone(),
                                items: result.items.clone(),
                            },
                        );
                        event_message = Some(operation_id);
                        next_snapshot = next_snapshot.with_search_results(result.items);
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::BrowseCatalog {
                parent_id,
                genres,
                page,
            } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self
                    .repository
                    .browse_catalog(parent_id.as_deref(), genres, page.clone())
                    .await;
                match results {
                    Ok(result) => {
                        let operation_id = self.allocate_catalog_operation_id(now_epoch_millis);
                        self.catalog_operations.insert(
                            operation_id.clone(),
                            CatalogOperation::Browse {
                                parent_id: parent_id.clone(),
                                genres: genres.clone(),
                                page_size: page.page_size,
                                next_page_token: result.next_page_token.clone(),
                                items: result.items.clone(),
                            },
                        );
                        event_message = Some(operation_id);
                        next_snapshot = next_snapshot.with_browse_results(result.items);
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::LoadNextCatalogPage { operation_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                let operation = self.catalog_operations.get(operation_id).cloned();
                match operation {
                    Some(CatalogOperation::Search {
                        query,
                        page_size,
                        next_page_token: Some(page_token),
                        items: mut accumulated_items,
                    }) => {
                        match self
                            .repository
                            .search_catalog(
                                &query,
                                crate::EnginePageRequest {
                                    page_size,
                                    page_token: Some(page_token),
                                },
                            )
                            .await
                        {
                            Ok(result) => {
                                accumulated_items.extend(result.items);
                                next_snapshot =
                                    next_snapshot.with_search_results(accumulated_items.clone());
                                if let Some(operation) =
                                    self.catalog_operations.get_mut(operation_id)
                                    && let CatalogOperation::Search {
                                        next_page_token,
                                        items,
                                        ..
                                    } = operation
                                {
                                    *next_page_token = result.next_page_token;
                                    *items = accumulated_items;
                                }
                                event_message = Some(operation_id.clone());
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Some(CatalogOperation::Browse {
                        parent_id,
                        genres,
                        page_size,
                        next_page_token: Some(page_token),
                        items: mut accumulated_items,
                    }) => {
                        match self
                            .repository
                            .browse_catalog(
                                parent_id.as_deref(),
                                &genres,
                                crate::EnginePageRequest {
                                    page_size,
                                    page_token: Some(page_token),
                                },
                            )
                            .await
                        {
                            Ok(result) => {
                                accumulated_items.extend(result.items);
                                next_snapshot =
                                    next_snapshot.with_browse_results(accumulated_items.clone());
                                if let Some(operation) =
                                    self.catalog_operations.get_mut(operation_id)
                                    && let CatalogOperation::Browse {
                                        next_page_token,
                                        items,
                                        ..
                                    } = operation
                                {
                                    *next_page_token = result.next_page_token;
                                    *items = accumulated_items;
                                }
                                event_message = Some(operation_id.clone());
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Some(_) => {
                        next_snapshot = next_snapshot.with_error(Some(EngineError::new(
                            crate::EngineErrorType::FailedPrecondition,
                            "catalog operation has no next page",
                            false,
                        )));
                    }
                    None => {
                        next_snapshot = next_snapshot.with_error(Some(EngineError::new(
                            crate::EngineErrorType::InvalidInput,
                            "unknown catalog operation",
                            false,
                        )));
                    }
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::LoadDiscoveryFeed {
                excluded_track_ids,
                page,
            }
            | EngineCommandType::LoadForYouFeed {
                excluded_track_ids,
                page,
            }
            | EngineCommandType::LoadRecommendations {
                excluded_track_ids,
                page,
            } => {
                let feed = match &command.command_type {
                    EngineCommandType::LoadDiscoveryFeed { .. } => crate::DiscoveryFeed::Discovery,
                    EngineCommandType::LoadForYouFeed { .. } => crate::DiscoveryFeed::ForYou,
                    EngineCommandType::LoadRecommendations { .. } => {
                        crate::DiscoveryFeed::Recommendations
                    }
                    _ => unreachable!("matched a feed command"),
                };
                if feed == crate::DiscoveryFeed::Discovery {
                    self.discovery_operation = None;
                    next_snapshot = next_snapshot.with_discovery_next_page_token(None);
                }
                next_snapshot = next_snapshot.with_feed_results(feed, Vec::new());
                let auth_identity = AuthIdentity::from_state(&next_snapshot.auth_state);
                let result = match (auth_identity.clone(), self.discovery_port.clone()) {
                    (Some(identity), Some(port)) => {
                        next_snapshot = next_snapshot.with_busy(true);
                        self.publish_intermediate_snapshot(next_snapshot.clone());
                        port.get_feed(
                            feed,
                            &identity.discovery_identity(),
                            excluded_track_ids,
                            page.clone(),
                        )
                        .await
                    }
                    (None, _) => Err(EngineError::new(
                        crate::EngineErrorType::LoginRequired,
                        "discovery requires an authenticated session",
                        false,
                    )),
                    (Some(_), None) => Err(EngineError::new(
                        crate::EngineErrorType::FailedPrecondition,
                        "discovery service is not configured",
                        false,
                    )),
                };
                next_snapshot.auth_state = self
                    .auth_state_provider
                    .as_ref()
                    .map(|provider| provider.current_auth_state())
                    .unwrap_or(crate::AuthState::Anonymous);
                let current_identity = AuthIdentity::from_state(&next_snapshot.auth_state);
                if current_identity != auth_identity {
                    if feed == crate::DiscoveryFeed::Discovery {
                        self.discovery_operation = None;
                        next_snapshot = next_snapshot.with_discovery_next_page_token(None);
                    }
                    next_snapshot = next_snapshot
                        .with_feed_results(feed, Vec::new())
                        .with_error(Some(EngineError::new(
                            crate::EngineErrorType::LoginRequired,
                            "discovery session changed while loading",
                            false,
                        )));
                } else {
                    match result {
                        Ok(result) => {
                            let items: Vec<_> = result
                                .items
                                .into_iter()
                                .map(project_discovery_track)
                                .collect();
                            if feed == crate::DiscoveryFeed::Discovery {
                                let next_page_token = result.next_page_token;
                                self.discovery_operation = Some(DiscoveryOperation {
                                    auth_identity: auth_identity
                                        .clone()
                                        .expect("authenticated discovery result"),
                                    excluded_track_ids: excluded_track_ids.clone(),
                                    page_size: page.page_size,
                                    next_page_token: next_page_token.clone(),
                                    items: items.clone(),
                                });
                                next_snapshot =
                                    next_snapshot.with_discovery_next_page_token(next_page_token);
                            }
                            self.feed_projection_identity = auth_identity;
                            next_snapshot = next_snapshot.with_feed_results(feed, items);
                        }
                        Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                    }
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::LoadNextDiscoveryPage => {
                let auth_identity = AuthIdentity::from_state(&next_snapshot.auth_state);
                let operation = self.discovery_operation.clone();
                match (auth_identity, operation) {
                    (None, _) => {
                        self.discovery_operation = None;
                        next_snapshot = next_snapshot
                            .with_discovery_results(Vec::new())
                            .with_discovery_next_page_token(None)
                            .with_error(Some(EngineError::new(
                                crate::EngineErrorType::LoginRequired,
                                "discovery requires an authenticated session",
                                false,
                            )));
                    }
                    (Some(identity), Some(operation))
                        if identity == operation.auth_identity
                            && operation.next_page_token.is_some() =>
                    {
                        let page_token = operation.next_page_token.clone().unwrap();
                        next_snapshot = next_snapshot.with_busy(true);
                        self.publish_intermediate_snapshot(next_snapshot.clone());
                        let result = match self.discovery_port.clone() {
                            Some(port) => {
                                port.get_feed(
                                    crate::DiscoveryFeed::Discovery,
                                    &identity.discovery_identity(),
                                    &operation.excluded_track_ids,
                                    crate::EnginePageRequest {
                                        page_size: operation.page_size,
                                        page_token: Some(page_token),
                                    },
                                )
                                .await
                            }
                            None => Err(EngineError::new(
                                crate::EngineErrorType::FailedPrecondition,
                                "discovery service is not configured",
                                false,
                            )),
                        };
                        next_snapshot.auth_state = self
                            .auth_state_provider
                            .as_ref()
                            .map(|provider| provider.current_auth_state())
                            .unwrap_or(crate::AuthState::Anonymous);
                        let current_identity = AuthIdentity::from_state(&next_snapshot.auth_state);
                        if current_identity.as_ref() != Some(&identity) {
                            self.discovery_operation = None;
                            next_snapshot = next_snapshot
                                .with_discovery_results(Vec::new())
                                .with_discovery_next_page_token(None)
                                .with_error(Some(EngineError::new(
                                    crate::EngineErrorType::LoginRequired,
                                    "discovery session changed while loading",
                                    false,
                                )));
                        } else {
                            match result {
                                Ok(result) => {
                                    let mut accumulated_items = operation.items;
                                    accumulated_items.extend(
                                        result.items.into_iter().map(project_discovery_track),
                                    );
                                    next_snapshot = next_snapshot
                                        .with_discovery_results(accumulated_items.clone())
                                        .with_discovery_next_page_token(
                                            result.next_page_token.clone(),
                                        );
                                    if let Some(active) = self.discovery_operation.as_mut() {
                                        active.next_page_token = result.next_page_token;
                                        active.items = accumulated_items;
                                    }
                                }
                                Err(error) => {
                                    next_snapshot = next_snapshot.with_error(Some(error));
                                }
                            }
                        }
                    }
                    (Some(identity), Some(operation)) if identity == operation.auth_identity => {
                        next_snapshot = next_snapshot.with_error(Some(EngineError::new(
                            crate::EngineErrorType::FailedPrecondition,
                            "discovery operation has no next page",
                            false,
                        )));
                    }
                    (Some(_), Some(_)) => {
                        self.discovery_operation = None;
                        next_snapshot = next_snapshot
                            .with_discovery_results(Vec::new())
                            .with_discovery_next_page_token(None)
                            .with_error(Some(EngineError::new(
                                crate::EngineErrorType::LoginRequired,
                                "discovery session changed",
                                false,
                            )));
                    }
                    (Some(_), None) => {
                        next_snapshot = next_snapshot.with_error(Some(EngineError::new(
                            crate::EngineErrorType::InvalidInput,
                            "no active discovery operation",
                            false,
                        )));
                    }
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            history_command @ (EngineCommandType::LoadHistorySettings
            | EngineCommandType::UpdateHistorySettings { .. }
            | EngineCommandType::ListHistory { .. }
            | EngineCommandType::LoadNextHistoryPage
            | EngineCommandType::DeleteHistoryEntry { .. }
            | EngineCommandType::ClearHistory) => {
                self.dispatch_history_command(history_command, &mut next_snapshot)
                    .await;
            }
            library_command @ (EngineCommandType::SaveTrack { .. }
            | EngineCommandType::RemoveSavedTrack { .. }
            | EngineCommandType::ListSavedTracks { .. }
            | EngineCommandType::LoadNextSavedTracksPage
            | EngineCommandType::LikeTrack { .. }
            | EngineCommandType::UnlikeTrack { .. }
            | EngineCommandType::ListLikedTracks { .. }
            | EngineCommandType::LoadNextLikedTracksPage) => {
                self.dispatch_library_command(library_command, &mut next_snapshot)
                    .await;
            }
            playlist_command @ (EngineCommandType::CreatePlaylist { .. }
            | EngineCommandType::UpdatePlaylist { .. }
            | EngineCommandType::DeletePlaylist { .. }
            | EngineCommandType::ListPlaylists { .. }
            | EngineCommandType::LoadNextPlaylistsPage
            | EngineCommandType::AddPlaylistTrack { .. }
            | EngineCommandType::RemovePlaylistTrack { .. }
            | EngineCommandType::ListPlaylistTracks { .. }
            | EngineCommandType::LoadNextPlaylistTracksPage
            | EngineCommandType::ReorderPlaylistTracks { .. }) => {
                self.dispatch_playlist_command(playlist_command, &mut next_snapshot)
                    .await;
            }
            account_command @ (EngineCommandType::GetAccount
            | EngineCommandType::DeleteAccount
            | EngineCommandType::ListDeviceSessions { .. }
            | EngineCommandType::LoadNextDeviceSessionsPage
            | EngineCommandType::RevokeDeviceSession { .. }) => {
                self.dispatch_account_command(account_command, &mut next_snapshot)
                    .await;
            }
            EngineCommandType::SetSpeed { speed } => {
                next_snapshot = next_snapshot.with_speed(*speed);
                effects.push(EngineEffect::SetSpeed(*speed));
            }
            EngineCommandType::Seek { position_millis } => {
                next_snapshot = next_snapshot
                    .with_position(*position_millis)
                    .with_progress_tick(now_epoch_millis);
                effects.push(EngineEffect::Seek(*position_millis));
            }
            EngineCommandType::UpdateConfig { config } => {
                self.config = config.clone();
                info!("Engine configuration updated: {:?}", config);
            }
            EngineCommandType::HydrateThemePreference { theme } => {
                let current = &next_snapshot.theme_preference;
                if !current.is_initialized() || current.theme != *theme {
                    next_snapshot.theme_preference =
                        crate::model::preferences::ThemePreferenceState {
                            theme: *theme,
                            source: crate::model::preferences::PreferenceSource::LocalCache,
                            revision: current.revision.saturating_add(1),
                            session_user_id: None,
                        };
                }
            }
            EngineCommandType::SetThemePreference { theme } => {
                let current = &next_snapshot.theme_preference;
                if current.theme != *theme
                    || current.source != crate::model::preferences::PreferenceSource::LocalUser
                {
                    next_snapshot.theme_preference =
                        crate::model::preferences::ThemePreferenceState {
                            theme: *theme,
                            source: crate::model::preferences::PreferenceSource::LocalUser,
                            revision: current.revision.saturating_add(1),
                            session_user_id: next_snapshot
                                .session
                                .as_ref()
                                .map(|session| session.user_id.clone()),
                        };
                }
            }
            EngineCommandType::ApplyRemoteThemePreference {
                theme,
                user_id,
                baseline_revision,
            } => {
                let active_user_id = next_snapshot
                    .session
                    .as_ref()
                    .map(|session| session.user_id.as_str());
                let current = &next_snapshot.theme_preference;
                let session_is_current = active_user_id == Some(user_id.as_str());
                let revision_is_current = current.revision == *baseline_revision;
                let value_changed = current.theme != *theme
                    || current.source != crate::model::preferences::PreferenceSource::RemoteProfile
                    || current.session_user_id.as_deref() != Some(user_id.as_str());

                if session_is_current && revision_is_current && value_changed {
                    next_snapshot.theme_preference =
                        crate::model::preferences::ThemePreferenceState {
                            theme: *theme,
                            source: crate::model::preferences::PreferenceSource::RemoteProfile,
                            revision: current.revision.saturating_add(1),
                            session_user_id: Some(user_id.clone()),
                        };
                }
            }
            EngineCommandType::UpsertProfile { display_name } => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port.upsert(display_name.as_deref()).await;
                        match self.profile_result_for_current_identity(
                            &mut next_snapshot,
                            &identity,
                            result,
                        ) {
                            Ok(profile) => {
                                match Self::validate_profile_owner(&identity, &profile) {
                                    Ok(()) => {
                                        self.profile_projection_identity = Some(identity);
                                        next_snapshot.profile = Some(profile);
                                    }
                                    Err(error) => {
                                        next_snapshot = next_snapshot.with_error(Some(error))
                                    }
                                }
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::GetProfile => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let result = port.get().await;
                        match self.profile_result_for_current_identity(
                            &mut next_snapshot,
                            &identity,
                            result,
                        ) {
                            Ok(profile) => {
                                match Self::validate_profile_owner(&identity, &profile) {
                                    Ok(()) => {
                                        self.profile_projection_identity = Some(identity);
                                        next_snapshot.profile = Some(profile);
                                    }
                                    Err(error) if error.error_type == EngineErrorType::NotFound => {
                                        self.profile_projection_identity = None;
                                        Self::clear_profile_projection(&mut next_snapshot);
                                    }
                                    Err(error) => {
                                        next_snapshot = next_snapshot.with_error(Some(error))
                                    }
                                }
                            }
                            Err(error) if error.error_type == EngineErrorType::NotFound => {
                                self.profile_projection_identity = None;
                                Self::clear_profile_projection(&mut next_snapshot);
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {
                        self.profile_projection_identity = None;
                        Self::clear_profile_projection(&mut next_snapshot);
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::UpdateProfile { update } => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let current = port.get().await;
                        let result = self
                            .profile_result_for_current_identity(
                                &mut next_snapshot,
                                &identity,
                                current,
                            )
                            .and_then(|current| {
                                Self::validate_profile_owner(&identity, &current).map(|()| current)
                            });
                        match result {
                            Ok(_) => {
                                let updated = port.update(update.clone()).await;
                                match self.profile_result_for_current_identity(
                                    &mut next_snapshot,
                                    &identity,
                                    updated,
                                ) {
                                    Ok(profile) => {
                                        match Self::validate_profile_owner(&identity, &profile) {
                                            Ok(()) => {
                                                self.profile_projection_identity = Some(identity);
                                                next_snapshot.profile = Some(profile);
                                            }
                                            Err(error) => {
                                                next_snapshot =
                                                    next_snapshot.with_error(Some(error));
                                            }
                                        }
                                    }
                                    Err(error) => {
                                        next_snapshot = next_snapshot.with_error(Some(error));
                                    }
                                }
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::DeleteProfile => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let current = port.get().await;
                        let result = self
                            .profile_result_for_current_identity(
                                &mut next_snapshot,
                                &identity,
                                current,
                            )
                            .and_then(|profile| Self::validate_profile_owner(&identity, &profile));
                        match result {
                            Ok(()) => {
                                let deleted = port.delete().await;
                                match self.profile_result_for_current_identity(
                                    &mut next_snapshot,
                                    &identity,
                                    deleted,
                                ) {
                                    Ok(()) => {
                                        self.profile_projection_identity = None;
                                        Self::clear_profile_projection(&mut next_snapshot);
                                    }
                                    Err(error) => {
                                        next_snapshot = next_snapshot.with_error(Some(error));
                                    }
                                }
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::LoadProfilePreferences => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let current = port.get().await;
                        let result = match self.profile_result_for_current_identity(
                            &mut next_snapshot,
                            &identity,
                            current,
                        ) {
                            Ok(profile) => {
                                match Self::validate_profile_owner(&identity, &profile) {
                                    Ok(()) => {
                                        let preferences = port.get_preferences().await;
                                        self.profile_result_for_current_identity(
                                            &mut next_snapshot,
                                            &identity,
                                            preferences,
                                        )
                                        .map(|values| (profile, values))
                                    }
                                    Err(error) => Err(error),
                                }
                            }
                            Err(error) => Err(error),
                        };
                        match result {
                            Ok((profile, values)) => {
                                Self::project_profile_preferences(
                                    &mut next_snapshot,
                                    &identity,
                                    profile,
                                    values,
                                );
                                self.profile_projection_identity = Some(identity);
                            }
                            Err(error) if error.error_type == EngineErrorType::NotFound => {
                                self.profile_projection_identity = None;
                                Self::clear_profile_projection(&mut next_snapshot);
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) if error.error_type == EngineErrorType::NotFound => {
                        self.profile_projection_identity = None;
                        Self::clear_profile_projection(&mut next_snapshot);
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::UpdateProfilePreferences { values } => {
                match Self::profile_context(&next_snapshot, self.profile_port.clone()) {
                    Ok((identity, port)) => {
                        let current_profile = port.get().await;
                        let result = match self.profile_result_for_current_identity(
                            &mut next_snapshot,
                            &identity,
                            current_profile,
                        ) {
                            Ok(profile) => {
                                match Self::validate_profile_owner(&identity, &profile) {
                                    Ok(()) => {
                                        let current_preferences = port.get_preferences().await;
                                        match self.profile_result_for_current_identity(
                                            &mut next_snapshot,
                                            &identity,
                                            current_preferences,
                                        ) {
                                            Ok(current) => {
                                                let merged =
                                                    crate::model::preferences::merge_preferences(
                                                        serde_json::Value::Object(current),
                                                        serde_json::Value::Object(values.clone()),
                                                    );
                                                let merged =
                                                    merged.as_object().cloned().unwrap_or_default();
                                                let updated = port.update_preferences(merged).await;
                                                self.profile_result_for_current_identity(
                                                    &mut next_snapshot,
                                                    &identity,
                                                    updated,
                                                )
                                                .map(|updated| (profile, updated))
                                            }
                                            Err(error) => Err(error),
                                        }
                                    }
                                    Err(error) => Err(error),
                                }
                            }
                            Err(error) => Err(error),
                        };
                        match result {
                            Ok((profile, updated)) => {
                                Self::project_profile_preferences(
                                    &mut next_snapshot,
                                    &identity,
                                    profile,
                                    updated,
                                );
                                self.profile_projection_identity = Some(identity);
                            }
                            Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                        }
                    }
                    Err(error) => next_snapshot = next_snapshot.with_error(Some(error)),
                }
            }
            EngineCommandType::StartVoiceInteraction => {
                if let Some(ve) = &mut self.voice_engine {
                    ve.reset();
                    let context = if let (Some(title), Some(artist)) =
                        (&self.snapshot.title, &self.snapshot.artist)
                    {
                        Some((title.clone(), artist.clone()))
                    } else {
                        None
                    };
                    ve.set_context(context);
                }
                effects.push(EngineEffect::DuckAudio);
                effects.push(EngineEffect::StartAudioCapture);
                info!("Voice interaction started (audio ducked)");
            }
            EngineCommandType::StopVoiceInteraction => {
                let mut resolved_cmd = None;
                if let Some(ve) = &mut self.voice_engine {
                    match ve.finish() {
                        Ok(VoiceInteractionResult::Command(cmd)) => {
                            info!("Voice command determined: {:?}", cmd);
                            resolved_cmd = Some(cmd);
                        }
                        Ok(VoiceInteractionResult::Error(err)) => {
                            warn!("Voice interaction error: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                        Ok(VoiceInteractionResult::NoMatch) => {
                            info!("No voice command match found");
                            effects.push(EngineEffect::NotifyUser {
                                message: "I didn't catch that. Could you repeat?".to_string(),
                            });
                        }
                        Err(err) => {
                            warn!("Voice engine failure: {}", err);
                            effects.push(EngineEffect::NotifyUser { message: err });
                        }
                    }
                } else {
                    info!("Voice interaction stopped (no internal engine)");
                }

                if let Some(cmd) = resolved_cmd {
                    let outcome = Box::pin(self.dispatch(cmd, now_epoch_millis)).await;
                    effects.extend(outcome.effects);
                    next_snapshot = outcome.snapshot;
                }

                effects.push(EngineEffect::StopAudioCapture);
                effects.push(EngineEffect::UnduckAudio);
                next_snapshot = next_snapshot.with_busy(false).with_voice_hypothesis(None);
            }
            EngineCommandType::ProcessVoiceAudio { chunk } => {
                if let Some(ve) = &mut self.voice_engine {
                    let _ = ve.process_audio_chunk(chunk).map_err(|e| {
                        warn!("Failed to process voice audio chunk: {}", e);
                    });
                    let hypothesis = ve.get_partial_hypothesis();
                    next_snapshot = next_snapshot.with_voice_hypothesis(if hypothesis.is_empty() {
                        None
                    } else {
                        Some(hypothesis)
                    });
                }
            }
            EngineCommandType::VoicePlay { query } => {
                next_snapshot = next_snapshot.with_busy(true);
                let results = self.repository.search(query).await.unwrap_or_default();
                if let Some(first) = results.first() {
                    let media = first.clone();
                    match self.resolve_playback_source(&media).await {
                        Ok(media) => {
                            next_snapshot =
                                Self::update_media_state(&media, next_snapshot, &mut effects);
                            next_snapshot.playback_state = PlaybackState::Buffering;
                        }
                        Err(error) => {
                            next_snapshot = next_snapshot.with_error(Some(error));
                            next_snapshot.playback_state = PlaybackState::Error;
                        }
                    }
                } else {
                    info!("Voice search found no results for: {}", query);
                    effects.push(EngineEffect::NotifyUser {
                        message: format!("No results found for {}", query),
                    });
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::PlayMediaById { media_id } => {
                next_snapshot = next_snapshot.with_busy(true);
                if let Some(media) = self
                    .repository
                    .get_by_id(media_id)
                    .or_else(|| discovery_track_by_id(&next_snapshot, media_id))
                {
                    match self.resolve_playback_source(&media).await {
                        Ok(media) => {
                            next_snapshot =
                                Self::update_media_state(&media, next_snapshot, &mut effects);
                            next_snapshot.playback_state = PlaybackState::Buffering;
                        }
                        Err(error) => {
                            next_snapshot = next_snapshot.with_error(Some(error));
                            next_snapshot.playback_state = PlaybackState::Error;
                        }
                    }
                }
                next_snapshot = next_snapshot.with_busy(false);
            }
            EngineCommandType::SetSleepTimer { duration_millis } => {
                use crate::services::service::SleepTimerService;
                if let Some(timer_service) =
                    self.service_manager.find_service::<SleepTimerService>()
                {
                    match duration_millis {
                        Some(duration) => {
                            let fire_at = now_epoch_millis + duration;
                            timer_service.set_timer(fire_at);
                            info!("Sleep timer set to fire in {}ms", duration);
                        }
                        None => {
                            timer_service.set_timer(0);
                            info!("Sleep timer cancelled");
                        }
                    }
                }
            }
            _ => {}
        }

        match (prev_playback_state, next_snapshot.playback_state) {
            (prev, next) if prev != next => match next {
                PlaybackState::Buffering => {
                    effects.push(EngineEffect::RequestAudioFocus);
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Playing => {
                    effects.push(EngineEffect::Play);
                }
                PlaybackState::Paused => {
                    effects.push(EngineEffect::Pause);
                }
                PlaybackState::Idle => {
                    effects.push(EngineEffect::Stop);
                    effects.push(EngineEffect::AbandonAudioFocus);
                }
                _ => {}
            },
            _ => {}
        }

        self.snapshot = next_snapshot;
        self.snapshot.controls = self.derive_controls(&self.snapshot);

        if self.snapshot.playback_state != prev_playback_state {
            info!(
                "Playback state transition: {:?} -> {:?}",
                prev_playback_state, self.snapshot.playback_state
            );
        }

        let mut outcome = EngineOutcome {
            snapshot: self.snapshot.clone(),
            event: EngineEvent::command_applied(
                event_message.or_else(|| Some(command.command_type.as_wire().to_owned())),
            ),
            effects,
        };

        let middleware = Arc::clone(&self.middleware);
        middleware.after_dispatch(self, &mut outcome);

        self.execute_effects(&outcome.effects);

        outcome
    }
    fn profile_context(
        snapshot: &EngineSnapshot,
        port: Option<Arc<dyn crate::ProfilePort>>,
    ) -> Result<(AuthIdentity, Arc<dyn crate::ProfilePort>), EngineError> {
        let identity = AuthIdentity::from_state(&snapshot.auth_state).ok_or_else(|| {
            EngineError::new(
                crate::EngineErrorType::LoginRequired,
                "profile operation requires an authenticated session",
                false,
            )
        })?;
        let port = port.ok_or_else(|| {
            EngineError::new(
                crate::EngineErrorType::FailedPrecondition,
                "profile service is not configured",
                false,
            )
        })?;
        Ok((identity, port))
    }

    fn profile_result_for_current_identity<T>(
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
            self.profile_projection_identity = None;
            Self::clear_profile_projection(snapshot);
            Err(EngineError::new(
                EngineErrorType::LoginRequired,
                "profile session changed while the operation was in flight",
                false,
            ))
        }
    }
    fn validate_profile_owner(
        identity: &AuthIdentity,
        profile: &crate::EngineProfile,
    ) -> Result<(), EngineError> {
        if profile.external_user_id == identity.account_id {
            Ok(())
        } else {
            Err(EngineError::new(
                crate::EngineErrorType::Forbidden,
                "profile does not belong to the authenticated account",
                false,
            ))
        }
    }

    fn project_profile_preferences(
        snapshot: &mut EngineSnapshot,
        identity: &AuthIdentity,
        profile: crate::EngineProfile,
        values: serde_json::Map<String, serde_json::Value>,
    ) {
        if let Some(theme) = values
            .get("theme")
            .and_then(|value| value.as_str())
            .and_then(crate::ThemePreference::from_wire)
        {
            let current = &snapshot.theme_preference;
            snapshot.theme_preference = crate::ThemePreferenceState {
                theme,
                source: crate::PreferenceSource::RemoteProfile,
                revision: current.revision.saturating_add(1),
                session_user_id: Some(identity.account_id.clone()),
            };
        }
        snapshot.profile = Some(profile);
        snapshot.profile_preferences = values;
    }
}

fn discovery_track_by_id(snapshot: &EngineSnapshot, media_id: &str) -> Option<MediaItem> {
    snapshot
        .for_you_results
        .iter()
        .chain(snapshot.recommendations_results.iter())
        .chain(snapshot.discovery_results.iter())
        .find(|media| media.id == media_id)
        .cloned()
}
