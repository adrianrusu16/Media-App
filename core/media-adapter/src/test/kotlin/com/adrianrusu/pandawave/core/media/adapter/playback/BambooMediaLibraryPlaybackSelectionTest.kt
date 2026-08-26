package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.PandawaveTestUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BambooMediaLibraryPlaybackSelectionTest {
    @Test
    fun `queue selection strips local configuration so media3 cannot resolve urls`() {
        val sourced = MediaItem.Builder()
            .setMediaId("track-1")
            .setUri(PandawaveTestUri)
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
    fun `single id maps to play media and many ids map to play queue`() {
        assertEquals(
            BambooPlaybackIntent.PlayMedia("track-1"),
            BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("track-1"), startIndex = 0)
        )
        assertEquals(
            BambooPlaybackIntent.PlayQueue(listOf("a", "b"), startIndex = 1),
            BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("a", "b"), startIndex = 1)
        )
        assertNull(BambooMediaLibraryPlaybackSelection.playbackIntent(listOf("  ", ""), startIndex = 0))
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
}
