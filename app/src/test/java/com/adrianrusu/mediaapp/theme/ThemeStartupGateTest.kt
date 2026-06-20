package com.adrianrusu.mediaapp.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

class ThemeStartupGateTest {
    @Test
    fun `keeps splash visible while local theme is loading`() {
        val state = MutableStateFlow<ThemePreferenceState>(ThemePreferenceState.Loading)
        val clock = RecordingClock()
        val gate = ThemeStartupGate(state = state, nowMillis = clock::now)

        assertTrue(gate.shouldKeepSplashVisible())

        state.value = ThemePreferenceState.Ready(PandaWaveThemePreference.ForestTechDark)

        assertFalse(gate.shouldKeepSplashVisible())
    }

    @Test
    fun `fails open when local theme loading exceeds timeout`() {
        val state = MutableStateFlow<ThemePreferenceState>(ThemePreferenceState.Loading)
        val clock = RecordingClock()
        val gate = ThemeStartupGate(
            state = state,
            nowMillis = clock::now,
            timeoutMillis = 1_500L
        )

        clock.advanceBy(1_501L)

        assertFalse(gate.shouldKeepSplashVisible())
    }
}

private class RecordingClock {
    private var currentTimeMillis = 0L

    fun now(): Long = currentTimeMillis

    fun advanceBy(durationMillis: Long) {
        currentTimeMillis += durationMillis
    }
}
