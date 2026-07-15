package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BambooMediaSessionAndroidUriTest {
    @Test
    fun `android media3 preserves opaque escaped playback url`() {
        val opaqueUrl = "http://127.0.0.1:8080/stream/capability?token=a%2Fb%3Dc"

        val projection = BambooPlaybackState(
            mediaId = "track-live",
            sourceUri = opaqueUrl,
            mimeType = "audio/mpeg"
        ).toMediaSessionStateProjection()

        assertEquals(opaqueUrl, projection.mediaItem.localConfiguration?.uri.toString())
        assertEquals("audio/mpeg", projection.mediaItem.localConfiguration?.mimeType)
    }
}
