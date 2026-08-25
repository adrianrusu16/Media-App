use super::ids::{
    AccountGeneration, CommandId, HistoryGeneration, OperationId, PlaybackInstanceId,
    PlaylistGeneration, SearchGeneration,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OperationGeneration {
    Account(AccountGeneration),
    Search(SearchGeneration),
    Playlist(PlaylistGeneration),
    History(HistoryGeneration),
    Playback(PlaybackInstanceId),
}

/// Immutable request data for work performed outside the state-owning actor.
#[derive(Clone, Debug, Eq, PartialEq)]
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
    },
    PlaybackResolution {
        media_id: String,
    },
}

/// Internal asynchronous work envelope. A worker receives this immutable value
/// rather than `Engine` or any engine lock.
#[derive(Clone, Debug, Eq, PartialEq)]
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
    HistorySettings(crate::EngineHistorySettings),
    PlaybackResolved(crate::EnginePlaybackSource),
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
    pub playback: PlaybackInstanceId,
}

impl DomainGenerations {
    pub fn is_current(self, candidate: OperationGeneration) -> bool {
        match candidate {
            OperationGeneration::Account(generation) => self.account == generation,
            OperationGeneration::Search(generation) => self.search == generation,
            OperationGeneration::Playlist(generation) => self.playlist == generation,
            OperationGeneration::History(generation) => self.history == generation,
            OperationGeneration::Playback(generation) => self.playback == generation,
        }
    }
}
