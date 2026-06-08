package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BambooMediaSessionStateProjectionTest {
    @Test
    fun playingStateMapsToPlayableMediaItem() {
        val projection = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            playbackStatus = BambooPlaybackStatus.Playing
        ).toMediaSessionStateProjection()

        assertEquals("track-1", projection.mediaItem.mediaId)
        assertEquals("Bamboo Drive", projection.mediaItem.mediaMetadata.title)
        assertEquals("PandaWave", projection.mediaItem.mediaMetadata.artist)
        assertEquals(false, projection.mediaItem.mediaMetadata.isBrowsable)
        assertEquals(true, projection.mediaItem.mediaMetadata.isPlayable)
        assertTrue(projection.playWhenReady)
    }

    @Test
    fun idleStateUsesStableFallbackMediaId() {
        val projection = BambooPlaybackState(
            mediaId = null,
            playbackStatus = BambooPlaybackStatus.Idle
        ).toMediaSessionStateProjection()

        assertEquals("pandawave.playback.current", projection.mediaItem.mediaId)
        assertFalse(projection.playWhenReady)
    }
}
