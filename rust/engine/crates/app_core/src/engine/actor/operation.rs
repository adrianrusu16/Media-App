use super::ids::{
    AccountGeneration, CommandId, HistoryGeneration, LibraryGeneration, OperationId,
    PlaybackInstanceId, PlaylistGeneration, SearchGeneration,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OperationGeneration {
    Account(AccountGeneration),
    Search(SearchGeneration),
    Playlist(PlaylistGeneration),
    History(HistoryGeneration),
    Library(LibraryGeneration),
    Playback(PlaybackInstanceId),
}

/// Immutable request data for work performed outside the state-owning actor.
#[derive(Clone, Debug, PartialEq)]
pub enum EngineOperationRequest {
    AccountProjection {
        identity: crate::EngineAccountIdentity,
    },
    SearchPage {
        query: String,
        page: crate::EnginePageRequest,
        catalog_operation_id: Option<String>,
    },
    PlaylistPage {
        identity: crate::EnginePlaylistIdentity,
        page: crate::EnginePageRequest,
    },
    HistorySettings {
        identity: crate::EngineHistoryIdentity,
        pending_anonymous: Vec<crate::EngineHistoryEntry>,
    },
    HistorySettingsUpdate {
        identity: crate::EngineHistoryIdentity,
        enabled: bool,
    },
    HistoryPage {
        identity: crate::EngineHistoryIdentity,
        page: crate::EnginePageRequest,
        pending_anonymous: Vec<crate::EngineHistoryEntry>,
        settings_enabled: Option<bool>,
    },
    HistoryDelete {
        identity: crate::EngineHistoryIdentity,
        history_id: String,
    },
    HistoryClear {
        identity: crate::EngineHistoryIdentity,
    },
    PlaybackResolution {
        media_id: String,
    },
    LibraryPage {
        identity: crate::EngineLibraryIdentity,
        page: crate::EnginePageRequest,
        saved: bool,
    },
    LibraryMutation {
        identity: crate::EngineLibraryIdentity,
        track_id: String,
        mutation: crate::EngineLibraryMutation,
    },
}

impl EngineOperationRequest {
    pub(crate) fn telemetry_name(&self) -> &'static str {
        match self {
            Self::AccountProjection { .. } => "account_projection",
            Self::SearchPage { .. } => "search_page",
            Self::PlaylistPage { .. } => "playlist_page",
            Self::HistorySettings { .. } => "history_settings",
            Self::HistorySettingsUpdate { .. } => "history_settings_update",
            Self::HistoryPage { .. } => "history_page",
            Self::HistoryDelete { .. } => "history_delete",
            Self::HistoryClear { .. } => "history_clear",
            Self::PlaybackResolution { .. } => "playback_resolution",
            Self::LibraryPage { saved: true, .. } => "library_saved_page",
            Self::LibraryPage { saved: false, .. } => "library_liked_page",
            Self::LibraryMutation { .. } => "library_mutation",
        }
    }
}

/// Internal asynchronous work envelope. A worker receives this immutable value
/// rather than `Engine` or any engine lock.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineOperation {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub request: EngineOperationRequest,
}

#[derive(Clone, Debug, PartialEq)]
pub enum EngineOperationResult {
    AccountProjection(crate::Account),
    SearchPage {
        catalog_operation_id: String,
        items: Vec<crate::MediaItem>,
        next_page_token: Option<crate::EnginePageToken>,
    },
    PlaylistPage {
        playlists: Vec<crate::EnginePlaylist>,
        next_page_token: Option<crate::EnginePageToken>,
    },
    HistorySettings {
        settings: crate::EngineHistorySettings,
        reconciliation: crate::HistoryReconciliation,
    },
    HistorySettingsUpdate(crate::EngineHistorySettingsUpdate),
    HistoryPage {
        items: Vec<crate::EngineHistoryEntry>,
        next_page_token: Option<crate::EnginePageToken>,
        reconciliation: crate::HistoryReconciliation,
    },
    HistoryDeleted,
    HistoryCleared(u64),
    PlaybackResolved(crate::EnginePlaybackSource),
    LibraryPage {
        items: Vec<crate::EngineLibraryTrack>,
        next_page_token: Option<crate::EnginePageToken>,
    },
    LibraryMutation(Option<crate::EngineLibraryTrack>),
}

/// Typed completion returned through the reliable completion ingress. The
/// generation captured at launch remains attached through validation.
#[derive(Clone, Debug, PartialEq)]
pub struct EngineOperationCompletion {
    pub operation_id: OperationId,
    pub command_id: CommandId,
    pub generation: OperationGeneration,
    pub result: Result<EngineOperationResult, crate::model::error::EngineError>,
}

impl EngineOperation {
    pub fn completion(
        &self,
        result: Result<EngineOperationResult, crate::model::error::EngineError>,
    ) -> EngineOperationCompletion {
        EngineOperationCompletion {
            operation_id: self.operation_id,
            command_id: self.command_id,
            generation: self.generation,
            result,
        }
    }
}

/// Actor-owned generation snapshot used to reject late operation completions.
/// Snapshot revision is intentionally absent because it is never a stale-result
/// correctness token.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct DomainGenerations {
    pub account: AccountGeneration,
    pub search: SearchGeneration,
    pub playlist: PlaylistGeneration,
    pub history: HistoryGeneration,
    pub library: LibraryGeneration,
    pub playback: PlaybackInstanceId,
}

impl DomainGenerations {
    pub fn is_current(self, candidate: OperationGeneration) -> bool {
        match candidate {
            OperationGeneration::Account(generation) => self.account == generation,
            OperationGeneration::Search(generation) => self.search == generation,
            OperationGeneration::Playlist(generation) => self.playlist == generation,
            OperationGeneration::History(generation) => self.history == generation,
            OperationGeneration::Library(generation) => self.library == generation,
            OperationGeneration::Playback(generation) => self.playback == generation,
        }
    }
}
