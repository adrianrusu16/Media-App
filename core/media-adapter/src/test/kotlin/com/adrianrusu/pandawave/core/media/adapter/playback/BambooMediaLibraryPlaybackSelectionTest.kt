package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.PandaPlaybackContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BambooMediaLibraryPlaybackSelectionTest {
    @Test
    fun `queue selection strips local configuration so media3 cannot resolve urls`() {
        val sourced = MediaItem.Builder()
            .setMediaId("track-1")
            .setUri(android.net.PandawaveTestUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Bamboo")
                    .setIsPlayable(true)
                    .build()
            )
            .build()

        val resolved = BambooMediaLibraryPlaybackSelection.withoutLocalConfiguration(sourced)

        assertEquals("track-1", resolved.mediaId)
        assertEquals("Bamboo", resolved.mediaMetadata.title.toString())
        assertNull(resolved.localConfiguration)
    }

    @Test
    fun `ids map to play from context so engine owns the queue`() {
        val single = BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("track-1"), startIndex = 0)
        assertEquals(
            BambooPlaybackIntent.PlayFromContext(
                context = PandaPlaybackContext.Browse("track-1"),
                selectedMediaId = "track-1",
                mediaIds = listOf("track-1")
            ),
            single
        )
        val queue = BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("a", "b"), startIndex = 1)
        assertEquals(
            BambooPlaybackIntent.PlayFromContext(
                context = PandaPlaybackContext.Browse("b"),
                selectedMediaId = "b",
                mediaIds = listOf("a", "b")
            ),
            queue
        )
        assertNull(BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("  ", ""), startIndex = 0))
    }

    @Test
    fun `history tokens preserve occurrence`() {
        val platformId = PandaMediaSelectionId.history("history-1", "track-1")
        val intent = BambooMediaLibraryPlaybackSelection.playbackIntent(listOf(platformId), startIndex = 0)

        assertEquals(
            BambooPlaybackIntent.PlayFromContext(
                context = PandaPlaybackContext.History,
                selectedMediaId = "track-1",
                occurrenceId = "history-1",
                mediaIds = listOf("track-1")
            ),
            intent
        )
    }

    @Test
    fun `blank media ids are dropped before dispatch`() {
        val ids = BambooMediaLibraryPlaybackSelection.mediaIds(
            listOf(
                MediaItem.Builder().setMediaId(" track-1 ").build(),
                MediaItem.Builder().setMediaId(" ").build(),
                MediaItem.Builder().setMediaId("track-2").build()
            )
        )

        assertEquals(listOf("track-1", "track-2"), ids)
        assertTrue(ids.none(String::isBlank))
    }

    @Test
    fun `search query and empty voice are classified without inventing ids`() {
        val search = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setSearchQuery("bamboo radio").build()
            )
            .build()
        val empty = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().build())
            .build()

        assertEquals(
            Media3PlaybackRequest.Search("bamboo radio"),
            BambooMediaLibraryPlaybackSelection.classify(listOf(search), startIndex = 0)
        )
        assertEquals(
            Media3PlaybackRequest.EmptyVoice,
            BambooMediaLibraryPlaybackSelection.classify(listOf(empty), startIndex = 0)
        )
        assertEquals(
            Media3PlaybackRequest.EmptyVoice,
            BambooMediaLibraryPlaybackSelection.classify(emptyList(), startIndex = 0)
        )
    }
}
