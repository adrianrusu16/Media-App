package com.adrianrusu.pandawave.core.ui.miniplayer

import com.adrianrusu.pandawave.core.ui.playback.MAX_PROGRESS_INTERPOLATION_GAP_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals

class MiniPlayerProgressProjectorTest {
    @Test
    fun `playing progress advances from mini player anchor`() {
        val progress = MiniPlayerProgressProjector.fromAnchor(
            anchor = MiniPlayerProgressAnchor(
                positionMillis = 10_000L,
                durationMillis = 40_000L,
                updatedAtEpochMillis = 1_000L,
                playbackSpeed = 1.5F,
                isPlaying = true
            ),
            nowMillis = 3_000L
        )

        assertEquals(0.325F, progress.fraction)
        assertEquals(13_000L, progress.positionMillis)
        assertEquals(40_000L, progress.durationMillis)
    }

    @Test
    fun `progress clamps at the end of the duration`() {
        val progress = MiniPlayerProgressProjector.fromAnchor(
            anchor = MiniPlayerProgressAnchor(
                positionMillis = 39_000L,
                durationMillis = 40_000L,
                updatedAtEpochMillis = 1_000L,
                isPlaying = true
            ),
            nowMillis = 3_000L
        )

        assertEquals(1F, progress.fraction)
        assertEquals(40_000L, progress.positionMillis)
        assertEquals(40_000L, progress.durationMillis)
    }

    @Test
    fun `unknown duration uses empty progress`() {
        val progress = MiniPlayerProgressProjector.fromAnchor(
            anchor = MiniPlayerProgressAnchor(
                positionMillis = 10_000L,
                durationMillis = null,
                updatedAtEpochMillis = 1_000L,
                isPlaying = true
            ),
            nowMillis = 3_000L
        )

        assertEquals(0F, progress.fraction)
        assertEquals(10_000L, progress.positionMillis)
        assertEquals(null, progress.durationMillis)
    }

    @Test
    fun `stale progress clock does not complete the bar before the next checkpoint`() {
        val progress = MiniPlayerProgressProjector.fromAnchor(
            anchor = MiniPlayerProgressAnchor(
                positionMillis = 18_688L,
                durationMillis = 252_395L,
                updatedAtEpochMillis = 1_000L,
                isPlaying = true
            ),
            nowMillis = 150_000L
        )

        assertEquals(18_688L + MAX_PROGRESS_INTERPOLATION_GAP_MILLIS, progress.positionMillis)
        assertEquals(false, progress.fraction >= 1F)
    }
}
