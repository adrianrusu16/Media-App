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

enum class LibraryTab { SAVED, LIKED }

data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.SAVED,
    val savedTracks: List<LibraryTrack> = emptyList(),
    val likedTracks: List<LibraryTrack> = emptyList(),
    val pendingMediaIds: Set<String> = emptySet(),
    val hasSavedNextPage: Boolean = false,
    val hasLikedNextPage: Boolean = false,
    val isLoading: Boolean = true,
    val isSignedOut: Boolean = false,
    val errorType: String? = null,
    val isRetryableError: Boolean = false,
) {
    val selectedTracks: List<LibraryTrack>
        get() = if (selectedTab == LibraryTab.SAVED) savedTracks else likedTracks

    val hasSelectedNextPage: Boolean
        get() = if (selectedTab == LibraryTab.SAVED) hasSavedNextPage else hasLikedNextPage
}
