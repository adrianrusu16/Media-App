package com.adrianrusu.pandawave.feature.nowplaying

import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingModeTest {
    @Test
    fun `ambient reducer states project to reusable presentation modes`() {
        assertEquals(NowPlayingMode.Interactive, AmbientModeState.Hidden.toNowPlayingMode())
        assertEquals(NowPlayingMode.Interactive, AmbientModeState.Interactive.toNowPlayingMode())
        assertEquals(
            NowPlayingMode.Interactive,
            AmbientModeState.WaitingForInactivity(deadlineMillis = 1L, token = 1L).toNowPlayingMode()
        )
        assertEquals(NowPlayingMode.SleepingAmbient, AmbientModeState.AmbientSleeping.toNowPlayingMode())
        assertEquals(NowPlayingMode.VisualizingAmbient, AmbientModeState.AmbientVisualizing.toNowPlayingMode())
    }
}
