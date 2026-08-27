package com.adrianrusu.pandawave.core.media.adapter.playback

/**
 * Canonical Media3 browse IDs. These are platform tokens; they are never sent
 * to Canopy unless [engineParentId] says the node is a real engine browse parent.
 */
internal object PandaMediaLibraryIds {
    const val ROOT = "pw.root"
    const val LIBRARY = "pw.library"
    const val SAVED = "pw.saved"
    const val DOWNLOADS = "pw.downloads"
    const val HISTORY = "pw.history"
    const val PLAYLISTS = "pw.playlists"
    const val ALBUMS = "pw.albums"
    const val ARTISTS = "pw.artists"
    const val PLATFORM_RECENT = "pw.platform.recent"
    const val PLATFORM_SUGGESTED = "pw.platform.suggested"
    const val PLATFORM_OFFLINE = "pw.platform.offline"
    const val PLATFORM_OFFLINE_SUGGESTED = "pw.platform.offline.suggested"

    const val ENGINE_ROOT_PARENT_ID = "root"
    const val ENGINE_ALBUMS_PARENT_ID = "albums"
    const val ENGINE_ARTISTS_PARENT_ID = "artists"

    const val LEGACY_ROOT = "pandawave.library.root"
    const val LEGACY_SAVED = "pandawave.library.saved"
    const val LEGACY_DOWNLOADS = "pandawave.library.downloads"
    const val LEGACY_HISTORY = "pandawave.library.recent"

    fun canonicalize(mediaId: String): String = when (mediaId) {
        LEGACY_ROOT, ROOT -> ROOT
        LEGACY_SAVED, SAVED -> SAVED
        LEGACY_DOWNLOADS, DOWNLOADS -> DOWNLOADS
        LEGACY_HISTORY, HISTORY -> HISTORY
        else -> mediaId
    }

    fun isSynthetic(mediaId: String): Boolean = canonicalize(mediaId) in SyntheticIds

    fun engineParentId(mediaId: String): String? = when (canonicalize(mediaId)) {
        ROOT -> ENGINE_ROOT_PARENT_ID
        ALBUMS -> ENGINE_ALBUMS_PARENT_ID
        ARTISTS -> ENGINE_ARTISTS_PARENT_ID
        else -> null
    }
}

private val SyntheticIds = setOf(
    PandaMediaLibraryIds.ROOT,
    PandaMediaLibraryIds.LIBRARY,
    PandaMediaLibraryIds.SAVED,
    PandaMediaLibraryIds.DOWNLOADS,
    PandaMediaLibraryIds.HISTORY,
    PandaMediaLibraryIds.PLAYLISTS,
    PandaMediaLibraryIds.ALBUMS,
    PandaMediaLibraryIds.ARTISTS,
    PandaMediaLibraryIds.PLATFORM_RECENT,
    PandaMediaLibraryIds.PLATFORM_SUGGESTED,
    PandaMediaLibraryIds.PLATFORM_OFFLINE,
    PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED
)
