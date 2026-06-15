package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooMediaSessionStateProjectionTest {
    @Test
    fun `playing state maps to playable media item`() {
        var parsedArtworkUri: String? = null
        val projection = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            album = "Canopy Sessions",
            durationMillis = 222_000L,
            artworkUri = "content://pandawave/art/track-1",
            playbackStatus = BambooPlaybackStatus.Playing,
            positionMillis = 9_000L
        ).toMediaSessionStateProjection(
            artworkUriParser = BambooArtworkUriParser { value ->
                parsedArtworkUri = value
                null
            }
        )

        assertEquals("track-1", projection.mediaItem.mediaId)
        assertEquals("Bamboo Drive", projection.mediaItem.mediaMetadata.title)
        assertEquals("PandaWave", projection.mediaItem.mediaMetadata.artist)
        assertEquals("Canopy Sessions", projection.mediaItem.mediaMetadata.albumTitle)
        assertEquals(222_000L, projection.mediaItem.mediaMetadata.durationMs)
        assertEquals("content://pandawave/art/track-1", parsedArtworkUri)
        assertEquals(false, projection.mediaItem.mediaMetadata.isBrowsable)
        assertEquals(true, projection.mediaItem.mediaMetadata.isPlayable)
        assertTrue(projection.playWhenReady)
        assertEquals(9_000L, projection.positionMillis)
    }

    @Test
    fun `idle state uses stable fallback media id`() {
        val projection = BambooPlaybackState(
            mediaId = null,
            playbackStatus = BambooPlaybackStatus.Idle
        ).toMediaSessionStateProjection()

        assertEquals("pandawave.playback.current", projection.mediaItem.mediaId)
        assertFalse(projection.playWhenReady)
    }

    @Test
    fun `negative playback position is clamped for media3`() {
        val projection = BambooPlaybackState(
            positionMillis = -1L
        ).toMediaSessionStateProjection()

        assertEquals(0L, projection.positionMillis)
    }
}
