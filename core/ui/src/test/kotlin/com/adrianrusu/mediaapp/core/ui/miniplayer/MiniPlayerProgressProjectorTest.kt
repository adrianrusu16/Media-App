package com.adrianrusu.mediaapp.core.ui.miniplayer

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
    }
}
