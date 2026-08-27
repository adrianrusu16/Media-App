package com.adrianrusu.pandawave.core.media.adapter.playback

internal data class PandaLibraryBrowseHints(
    val isRecent: Boolean = false,
    val isSuggested: Boolean = false,
    val isOffline: Boolean = false,
    val childrenLimit: Int? = null,
    val browsableOnly: Boolean = false,
    val ignoreHostPagination: Boolean = false
) {
    fun rootId(): String = when {
        isSuggested && isOffline -> PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED
        isRecent -> PandaMediaLibraryIds.PLATFORM_RECENT
        isSuggested -> PandaMediaLibraryIds.PLATFORM_SUGGESTED
        isOffline -> PandaMediaLibraryIds.PLATFORM_OFFLINE
        else -> PandaMediaLibraryIds.ROOT
    }

    fun pageLimit(pageSize: Int): Int {
        val requested = childrenLimit ?: pageSize
        return if (ignoreHostPagination) {
            requested.coerceIn(1, AAOS_MATERIALIZED_LIMIT)
        } else {
            pageSize.coerceAtLeast(1)
        }
    }

    fun offsetFor(page: Int, pageSize: Int): Int = if (ignoreHostPagination) {
        0
    } else {
        pageOffset(page, pageSize)
    }
}

internal data class CatalogGenerations(
    val history: Long = 0L,
    val savedCount: Int = 0,
    val forYouCount: Int = 0,
    val recommendationsCount: Int = 0,
    val playlistsCount: Int = 0
)

internal object PandaMediaLibraryInvalidation {
    fun changedParents(previous: CatalogGenerations, next: CatalogGenerations): Set<String> = buildSet {
        if (previous.history != next.history) {
            add(PandaMediaLibraryIds.HISTORY)
            add(PandaMediaLibraryIds.PLATFORM_RECENT)
        }
        if (previous.savedCount != next.savedCount) {
            add(PandaMediaLibraryIds.SAVED)
            add(PandaMediaLibraryIds.DOWNLOADS)
            add(PandaMediaLibraryIds.PLATFORM_OFFLINE)
        }
        if (previous.forYouCount != next.forYouCount) {
            add(PandaMediaLibraryIds.PLATFORM_SUGGESTED)
            add(PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED)
        }
        if (previous.playlistsCount != next.playlistsCount) {
            add(PandaMediaLibraryIds.PLAYLISTS)
        }
    }
}

internal const val AAOS_MATERIALIZED_LIMIT = 50
