use crate::RetryClass;

/// Access material, if any, that a canonical Canopy RPC requires.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AuthRequirement {
    Anonymous,
    OptionalAccess,
    AccessAuthenticated,
    RefreshCredential,
}

/// Every RPC in the pinned `canopy.v1` Catalog, Playback, Discovery, Profile,
/// History, Library, Playlist, Auth, and System services.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CanopyOperation {
    Browse,
    Search,
    GetMedia,
    ResolvePlayback,
    GetDiscoveryFeed,
    GetForYouFeed,
    GetRecommendations,
    UpsertProfile,
    GetProfile,
    UpdateProfile,
    DeleteProfile,
    GetPreferences,
    UpdatePreferences,
    GetHistorySettings,
    UpdateHistorySettings,
    RecordPlayback,
    ListHistory,
    DeleteHistoryEntry,
    ClearHistory,
    SaveTrack,
    RemoveSavedTrack,
    ListSavedTracks,
    LikeTrack,
    UnlikeTrack,
    ListLikedTracks,
    CreatePlaylist,
    GetPlaylist,
    UpdatePlaylist,
    DeletePlaylist,
    ListPlaylists,
    AddPlaylistTrack,
    RemovePlaylistTrack,
    ReorderPlaylistTracks,
    ListPlaylistTracks,
    RegisterPassword,
    ResendVerification,
    VerifyEmail,
    LoginPassword,
    RequestPasswordReset,
    CompletePasswordReset,
    ChangePassword,
    BeginGoogleLogin,
    CompleteGoogleLogin,
    LinkGoogle,
    UnlinkGoogle,
    RefreshSession,
    Logout,
    LogoutAll,
    ListSessions,
    RevokeSession,
    GetAccount,
    DeleteAccount,
    GetStatus,
}

impl CanopyOperation {
    pub const ALL: [Self; 53] = [
        Self::Browse,
        Self::Search,
        Self::GetMedia,
        Self::ResolvePlayback,
        Self::GetDiscoveryFeed,
        Self::GetForYouFeed,
        Self::GetRecommendations,
        Self::UpsertProfile,
        Self::GetProfile,
        Self::UpdateProfile,
        Self::DeleteProfile,
        Self::GetPreferences,
        Self::UpdatePreferences,
        Self::GetHistorySettings,
        Self::UpdateHistorySettings,
        Self::RecordPlayback,
        Self::ListHistory,
        Self::DeleteHistoryEntry,
        Self::ClearHistory,
        Self::SaveTrack,
        Self::RemoveSavedTrack,
        Self::ListSavedTracks,
        Self::LikeTrack,
        Self::UnlikeTrack,
        Self::ListLikedTracks,
        Self::CreatePlaylist,
        Self::GetPlaylist,
        Self::UpdatePlaylist,
        Self::DeletePlaylist,
        Self::ListPlaylists,
        Self::AddPlaylistTrack,
        Self::RemovePlaylistTrack,
        Self::ReorderPlaylistTracks,
        Self::ListPlaylistTracks,
        Self::RegisterPassword,
        Self::ResendVerification,
        Self::VerifyEmail,
        Self::LoginPassword,
        Self::RequestPasswordReset,
        Self::CompletePasswordReset,
        Self::ChangePassword,
        Self::BeginGoogleLogin,
        Self::CompleteGoogleLogin,
        Self::LinkGoogle,
        Self::UnlinkGoogle,
        Self::RefreshSession,
        Self::Logout,
        Self::LogoutAll,
        Self::ListSessions,
        Self::RevokeSession,
        Self::GetAccount,
        Self::DeleteAccount,
        Self::GetStatus,
    ];

    pub const fn retry_class(self) -> RetryClass {
        match self {
            Self::Browse
            | Self::Search
            | Self::GetMedia
            | Self::ResolvePlayback
            | Self::GetDiscoveryFeed
            | Self::GetForYouFeed
            | Self::GetRecommendations
            | Self::GetProfile
            | Self::GetPreferences
            | Self::GetHistorySettings
            | Self::ListHistory
            | Self::ListSavedTracks
            | Self::ListLikedTracks
            | Self::GetPlaylist
            | Self::ListPlaylists
            | Self::ListPlaylistTracks
            | Self::ListSessions
            | Self::GetAccount
            | Self::GetStatus => RetryClass::Read,
            Self::UpsertProfile
            | Self::UpdatePreferences
            | Self::UpdateHistorySettings
            | Self::DeleteHistoryEntry
            | Self::ClearHistory
            | Self::SaveTrack
            | Self::RemoveSavedTrack
            | Self::LikeTrack
            | Self::UnlikeTrack
            | Self::DeletePlaylist
            | Self::RemovePlaylistTrack
            | Self::Logout
            | Self::RevokeSession => RetryClass::IdempotentMutation,
            Self::UpdateProfile
            | Self::DeleteProfile
            | Self::RecordPlayback
            | Self::CreatePlaylist
            | Self::UpdatePlaylist
            | Self::AddPlaylistTrack
            | Self::ReorderPlaylistTracks
            | Self::RegisterPassword
            | Self::ResendVerification
            | Self::VerifyEmail
            | Self::LoginPassword
            | Self::RequestPasswordReset
            | Self::CompletePasswordReset
            | Self::ChangePassword
            | Self::BeginGoogleLogin
            | Self::CompleteGoogleLogin
            | Self::LinkGoogle
            | Self::UnlinkGoogle
            | Self::LogoutAll
            | Self::DeleteAccount => RetryClass::NonReplayableMutation,
            Self::RefreshSession => RetryClass::Refresh,
        }
    }

    pub const fn auth_requirement(self) -> AuthRequirement {
        match self {
            Self::Browse | Self::Search | Self::GetMedia | Self::ResolvePlayback => {
                AuthRequirement::OptionalAccess
            }
            Self::RegisterPassword
            | Self::ResendVerification
            | Self::VerifyEmail
            | Self::LoginPassword
            | Self::RequestPasswordReset
            | Self::CompletePasswordReset
            | Self::BeginGoogleLogin
            | Self::CompleteGoogleLogin
            | Self::GetStatus => AuthRequirement::Anonymous,
            Self::GetDiscoveryFeed
            | Self::GetForYouFeed
            | Self::GetRecommendations
            | Self::UpsertProfile
            | Self::GetProfile
            | Self::UpdateProfile
            | Self::DeleteProfile
            | Self::GetPreferences
            | Self::UpdatePreferences
            | Self::GetHistorySettings
            | Self::UpdateHistorySettings
            | Self::RecordPlayback
            | Self::ListHistory
            | Self::DeleteHistoryEntry
            | Self::ClearHistory
            | Self::SaveTrack
            | Self::RemoveSavedTrack
            | Self::ListSavedTracks
            | Self::LikeTrack
            | Self::UnlikeTrack
            | Self::ListLikedTracks
            | Self::CreatePlaylist
            | Self::GetPlaylist
            | Self::UpdatePlaylist
            | Self::DeletePlaylist
            | Self::ListPlaylists
            | Self::AddPlaylistTrack
            | Self::RemovePlaylistTrack
            | Self::ReorderPlaylistTracks
            | Self::ListPlaylistTracks
            | Self::ChangePassword
            | Self::LinkGoogle
            | Self::UnlinkGoogle
            | Self::Logout
            | Self::LogoutAll
            | Self::ListSessions
            | Self::RevokeSession
            | Self::GetAccount
            | Self::DeleteAccount => AuthRequirement::AccessAuthenticated,
            Self::RefreshSession => AuthRequirement::RefreshCredential,
        }
    }
}
