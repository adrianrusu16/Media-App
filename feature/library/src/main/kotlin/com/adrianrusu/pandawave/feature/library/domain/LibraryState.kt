package com.adrianrusu.pandawave.feature.library.domain

data class LibraryTrack(
    val relationshipId: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMillis: Long,
    val explicit: Boolean,
    val artworkId: String?,
    val relationshipAtEpochMillis: Long,
)

data class LibraryPlaylist(val id: String, val name: String, val description: String?, val revision: Long)
data class PlaylistConflict(
    val playlistId: String,
    val expectedRevision: Long,
    val serverRevision: Long,
    val serverMembershipIds: List<String>,
    val proposedMembershipIds: List<String>,
)

enum class LibraryTab { SAVED, LIKED, PLAYLISTS }

data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.SAVED,
    val savedTracks: List<LibraryTrack> = emptyList(),
    val likedTracks: List<LibraryTrack> = emptyList(),
    val playlists: List<LibraryPlaylist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val playlistTracks: List<LibraryTrack> = emptyList(),
    val playlistConflict: PlaylistConflict? = null,
    val pendingMediaIds: Set<String> = emptySet(),
    val hasSavedNextPage: Boolean = false,
    val hasLikedNextPage: Boolean = false,
    val hasPlaylistsNextPage: Boolean = false,
    val hasPlaylistTracksNextPage: Boolean = false,
    val isLoading: Boolean = true,
    val isSignedOut: Boolean = false,
    val errorType: String? = null,
    val isRetryableError: Boolean = false,
) {
    val selectedTracks: List<LibraryTrack>
        get() = when (selectedTab) { LibraryTab.SAVED -> savedTracks; LibraryTab.LIKED -> likedTracks; LibraryTab.PLAYLISTS -> playlistTracks }

    val hasSelectedNextPage: Boolean
        get() = when (selectedTab) {
            LibraryTab.SAVED -> hasSavedNextPage
            LibraryTab.LIKED -> hasLikedNextPage
            LibraryTab.PLAYLISTS -> if (selectedPlaylistId == null) hasPlaylistsNextPage else hasPlaylistTracksNextPage
        }
}
