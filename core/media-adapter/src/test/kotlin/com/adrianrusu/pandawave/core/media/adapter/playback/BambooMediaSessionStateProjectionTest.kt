package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooMediaSessionStateProjectionTest {
    @Test
    fun `projection clamps volume to the player range`() {
        assertEquals(0F, BambooPlaybackState(volume = -1F).toMediaSessionStateProjection().volume)
        assertEquals(1F, BambooPlaybackState(volume = 2F).toMediaSessionStateProjection().volume)
    }

    @Test
    fun `playing state maps to playable media item`() {
        val parsedUris = mutableListOf<String>()
        val projection = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            album = "Canopy Sessions",
            durationMillis = 222_000L,
            artworkUri = "content://pandawave/art/track-1",
            sourceUri = "https://cdn.pandawave.test/audio/track-1.mp3",
            mimeType = "audio/mpeg",
            playbackStatus = BambooPlaybackStatus.Playing,
            positionMillis = 9_000L
        ).toMediaSessionStateProjection(
            uriParser = BambooUriParser { value ->
                parsedUris += value
                null
            }
        )

        assertEquals("track-1", projection.mediaItem.mediaId)
        assertEquals("Bamboo Drive", projection.mediaItem.mediaMetadata.title)
        assertEquals("PandaWave", projection.mediaItem.mediaMetadata.artist)
        assertEquals("Canopy Sessions", projection.mediaItem.mediaMetadata.albumTitle)
        assertEquals(222_000L, projection.mediaItem.mediaMetadata.durationMs)
        assertEquals(
            listOf(
                "https://cdn.pandawave.test/audio/track-1.mp3",
                "content://pandawave/art/track-1"
            ),
            parsedUris
        )
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

    @Test
    fun `opaque canonical source and expiry project directly to media3`() {
        val opaqueUrl = "http://10.0.2.2:8080/s/opaque?token=a%2Fb"
        val parsedUris = mutableListOf<String>()
        val projection = BambooPlaybackState(
            mediaId = "track-1",
            sourceUri = opaqueUrl,
            mimeType = "audio/flac",
            durationMillis = 42_000,
            playbackExpiresAtEpochMillis = 1_750_000_000_250
        ).toMediaSessionStateProjection(
            uriParser = BambooUriParser { value ->
                parsedUris += value
                null
            }
        )

        assertEquals(listOf(opaqueUrl), parsedUris)
        assertEquals("audio/flac", projection.contentType)
        assertEquals(42_000, projection.mediaItem.mediaMetadata.durationMs)
        assertEquals(1_750_000_000_250, projection.playbackExpiresAtEpochMillis)
        assertFalse(parsedUris.single().startsWith("content://"))
    }
}
