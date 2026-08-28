package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.ui.playback.MAX_PROGRESS_INTERPOLATION_GAP_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooPlaybackProgressProjectorTest {
    @Test
    fun `anchor progress advances without a full playback state`() {
        val progress = BambooPlaybackProgressProjector.fromAnchor(
            anchor = BambooPlaybackProgressAnchor(
                playbackSpeed = 2F,
                positionMillis = 4_000L,
                durationMillis = 10_000L,
                updatedAtEpochMillis = 1_000L,
                isPlaying = true
            ),
            nowMillis = 2_000L
        )

        assertEquals(6_000L, progress.positionMillis)
        assertEquals(0.6F, progress.fraction)
    }

    @Test
    fun `playing progress advances from engine anchor using playback speed`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Playing,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 10_000L,
                durationMillis = 40_000L,
                playbackSpeed = 1.5F
            ),
            nowMillis = 3_000L
        )

        assertEquals(13_000L, progress.positionMillis)
        assertEquals(40_000L, progress.durationMillis)
        assertEquals(0.325F, progress.fraction)
        assertTrue(progress.hasKnownDuration)
    }

    @Test
    fun `buffering progress waits for actual playback`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Recovering,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 0L,
                durationMillis = 40_000L
            ),
            nowMillis = 3_000L
        )

        assertEquals(0L, progress.positionMillis)
        assertEquals(0F, progress.fraction)
    }

    @Test
    fun `paused progress keeps the last engine position`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Paused,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 10_000L,
                durationMillis = 40_000L
            ),
            nowMillis = 3_000L
        )

        assertEquals(10_000L, progress.positionMillis)
        assertEquals(0.25F, progress.fraction)
    }

    @Test
    fun `progress clamps to known duration`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Playing,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 39_000L,
                durationMillis = 40_000L
            ),
            nowMillis = 3_000L
        )

        assertEquals(40_000L, progress.positionMillis)
        assertEquals(1F, progress.fraction)
    }

    @Test
    fun `unknown duration keeps projected position without a progress fraction`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Playing,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 10_000L,
                durationMillis = null
            ),
            nowMillis = 3_000L
        )

        assertEquals(12_000L, progress.positionMillis)
        assertEquals(null, progress.durationMillis)
        assertEquals(0F, progress.fraction)
        assertFalse(progress.hasKnownDuration)
    }

    @Test
    fun `future engine anchor does not move progress backwards`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Playing,
                updatedAtEpochMillis = 5_000L,
                positionMillis = 10_000L,
                durationMillis = 40_000L
            ),
            nowMillis = 3_000L
        )

        assertEquals(10_000L, progress.positionMillis)
        assertEquals(0.25F, progress.fraction)
    }

    @Test
    fun `stale progress clock does not run ahead of the next checkpoint`() {
        val progress = BambooPlaybackProgressProjector.fromPlaybackState(
            state = BambooPlaybackState(
                playbackStatus = BambooPlaybackStatus.Playing,
                updatedAtEpochMillis = 1_000L,
                positionMillis = 18_688L,
                durationMillis = 180_000L
            ),
            nowMillis = 50_000L
        )

        assertEquals(18_688L + MAX_PROGRESS_INTERPOLATION_GAP_MILLIS, progress.positionMillis)
    }
}
