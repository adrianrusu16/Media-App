package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.playback.PandaPlaybackContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@androidx.annotation.OptIn(UnstableApi::class)
class PandaMediaLibraryContractTest {
    @Test
    fun `library params select special roots instead of the folder tree`() {
        assertEquals(PandaMediaLibraryIds.ROOT, PandaLibraryBrowseHints().rootId())
        assertEquals(
            PandaMediaLibraryIds.PLATFORM_RECENT,
            PandaLibraryBrowseHints(isRecent = true).rootId()
        )
        assertEquals(
            PandaMediaLibraryIds.PLATFORM_SUGGESTED,
            PandaLibraryBrowseHints(isSuggested = true).rootId()
        )
        assertEquals(
            PandaMediaLibraryIds.PLATFORM_OFFLINE,
            PandaLibraryBrowseHints(isOffline = true).rootId()
        )
        assertEquals(
            PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED,
            PandaLibraryBrowseHints(isSuggested = true, isOffline = true).rootId()
        )
    }

    @Test
    fun `synthetic nodes never fall through as canopy parent ids`() {
        assertEquals("root", PandaMediaLibraryIds.engineParentId(PandaMediaLibraryIds.ROOT))
        assertEquals("albums", PandaMediaLibraryIds.engineParentId(PandaMediaLibraryIds.ALBUMS))
        assertEquals("artists", PandaMediaLibraryIds.engineParentId(PandaMediaLibraryIds.ARTISTS))
        assertEquals(null, PandaMediaLibraryIds.engineParentId(PandaMediaLibraryIds.SAVED))
        assertEquals(null, PandaMediaLibraryIds.engineParentId(PandaMediaLibraryIds.HISTORY))
        assertEquals(PandaMediaLibraryIds.SAVED, PandaMediaLibraryIds.canonicalize("pandawave.library.saved"))
    }

    @Test
    fun `generation changes notify the matching platform folders`() {
        val previous = CatalogGenerations(history = 1L, savedCount = 2, forYouCount = 3, playlistsCount = 4)
        val next = CatalogGenerations(history = 2L, savedCount = 3, forYouCount = 4, playlistsCount = 5)

        assertEquals(
            setOf(
                PandaMediaLibraryIds.HISTORY,
                PandaMediaLibraryIds.PLATFORM_RECENT,
                PandaMediaLibraryIds.SAVED,
                PandaMediaLibraryIds.DOWNLOADS,
                PandaMediaLibraryIds.PLATFORM_OFFLINE,
                PandaMediaLibraryIds.PLATFORM_SUGGESTED,
                PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED,
                PandaMediaLibraryIds.PLAYLISTS
            ),
            PandaMediaLibraryInvalidation.changedParents(previous, next)
        )
        assertTrue(PandaMediaLibraryInvalidation.changedParents(previous, previous).isEmpty())
    }

    @Test
    fun `opaque ids preserve occurrence and decode to engine media ids`() {
        val first = PandaMediaSelectionId.occurrence(PandaMediaLibraryIds.PLATFORM_SUGGESTED, 0, "track-a")
        val second = PandaMediaSelectionId.occurrence(PandaMediaLibraryIds.PLATFORM_SUGGESTED, 2, "track-a")
        val parsed = PandaMediaSelectionId.parse(second)

        assertTrue(first != second)
        assertEquals("track-a", PandaMediaSelectionId.engineMediaId(second))
        assertEquals(PandaPlaybackContext.ForYou, parsed?.context)
        assertEquals("2", parsed?.occurrenceId)
    }

    @Test
    fun `engine errors map onto media3 playback codes`() {
        assertEquals(
            PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED,
            PandaMediaSessionErrorMapper.errorCode("authentication")
        )
        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PandaMediaSessionErrorMapper.errorCode("network")
        )
        assertEquals(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PandaMediaSessionErrorMapper.errorCode("not_found")
        )
    }
}
