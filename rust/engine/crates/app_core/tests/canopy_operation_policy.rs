use panda_engine_core::RetryClass;
use panda_engine_core::networking::canopy::{AuthRequirement, CanopyOperation};

use AuthRequirement::{AccessAuthenticated, Anonymous, OptionalAccess, RefreshCredential};
use CanopyOperation::*;
use RetryClass::{IdempotentMutation, NonReplayableMutation, Read, Refresh};

#[test]
fn every_generated_rpc_has_the_expected_explicit_policy() {
    let expected = [
        (Browse, Read, OptionalAccess),
        (Search, Read, OptionalAccess),
        (GetMedia, Read, OptionalAccess),
        (ResolvePlayback, Read, OptionalAccess),
        (GetDiscoveryFeed, Read, AccessAuthenticated),
        (GetForYouFeed, Read, AccessAuthenticated),
        (GetRecommendations, Read, AccessAuthenticated),
        (UpsertProfile, IdempotentMutation, AccessAuthenticated),
        (GetProfile, Read, AccessAuthenticated),
        (UpdateProfile, NonReplayableMutation, AccessAuthenticated),
        (DeleteProfile, NonReplayableMutation, AccessAuthenticated),
        (GetPreferences, Read, AccessAuthenticated),
        (UpdatePreferences, IdempotentMutation, AccessAuthenticated),
        (GetHistorySettings, Read, AccessAuthenticated),
        (
            UpdateHistorySettings,
            IdempotentMutation,
            AccessAuthenticated,
        ),
        (RecordPlayback, NonReplayableMutation, AccessAuthenticated),
        (ListHistory, Read, AccessAuthenticated),
        (DeleteHistoryEntry, IdempotentMutation, AccessAuthenticated),
        (ClearHistory, IdempotentMutation, AccessAuthenticated),
        (SaveTrack, IdempotentMutation, AccessAuthenticated),
        (RemoveSavedTrack, IdempotentMutation, AccessAuthenticated),
        (ListSavedTracks, Read, AccessAuthenticated),
        (LikeTrack, IdempotentMutation, AccessAuthenticated),
        (UnlikeTrack, IdempotentMutation, AccessAuthenticated),
        (ListLikedTracks, Read, AccessAuthenticated),
        (CreatePlaylist, NonReplayableMutation, AccessAuthenticated),
        (GetPlaylist, Read, AccessAuthenticated),
        (UpdatePlaylist, NonReplayableMutation, AccessAuthenticated),
        (DeletePlaylist, IdempotentMutation, AccessAuthenticated),
        (ListPlaylists, Read, AccessAuthenticated),
        (AddPlaylistTrack, NonReplayableMutation, AccessAuthenticated),
        (RemovePlaylistTrack, IdempotentMutation, AccessAuthenticated),
        (
            ReorderPlaylistTracks,
            NonReplayableMutation,
            AccessAuthenticated,
        ),
        (ListPlaylistTracks, Read, AccessAuthenticated),
        (RegisterPassword, NonReplayableMutation, Anonymous),
        (ResendVerification, NonReplayableMutation, Anonymous),
        (VerifyEmail, NonReplayableMutation, Anonymous),
        (LoginPassword, NonReplayableMutation, Anonymous),
        (RequestPasswordReset, NonReplayableMutation, Anonymous),
        (CompletePasswordReset, NonReplayableMutation, Anonymous),
        (ChangePassword, NonReplayableMutation, AccessAuthenticated),
        (BeginGoogleLogin, NonReplayableMutation, Anonymous),
        (CompleteGoogleLogin, NonReplayableMutation, Anonymous),
        (LinkGoogle, NonReplayableMutation, AccessAuthenticated),
        (UnlinkGoogle, NonReplayableMutation, AccessAuthenticated),
        (RefreshSession, Refresh, RefreshCredential),
        (Logout, IdempotentMutation, AccessAuthenticated),
        (LogoutAll, NonReplayableMutation, AccessAuthenticated),
        (ListSessions, Read, AccessAuthenticated),
        (RevokeSession, IdempotentMutation, AccessAuthenticated),
        (GetAccount, Read, AccessAuthenticated),
        (DeleteAccount, NonReplayableMutation, AccessAuthenticated),
        (GetStatus, Read, Anonymous),
    ];

    assert_eq!(
        CanopyOperation::ALL,
        expected.map(|(operation, _, _)| operation)
    );
    for (operation, retry_class, auth_requirement) in expected {
        assert_eq!(operation.retry_class(), retry_class, "{operation:?}");
        assert_eq!(
            operation.auth_requirement(),
            auth_requirement,
            "{operation:?}"
        );
    }
}
