package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmbientModeReducerTest {
    @Test
    fun `idle inactivity enters sleeping ambient mode`() {
        val reducer = AmbientModeReducer()
        val waiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(isPlaying = false, realVisualizerReady = false),
                nowMillis = 1_000L
            )
        )

        val ambient = reducer.reduce(
            state = waiting.state,
            input = AmbientModeInput.TimeoutElapsed(
                token = assertIs<AmbientModeState.WaitingForInactivity>(waiting.state).token
            )
        )

        assertEquals(AmbientModeState.AmbientSleeping, ambient.state)
        assertEquals(listOf(AmbientModeEffect.StartSleepingAnimation), ambient.effects)
    }

    @Test
    fun `playing inactivity enters visualizing ambient mode only when real source is ready`() {
        val reducer = AmbientModeReducer()
        val waiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(eligible(), nowMillis = 1_000L)
        )

        val ambient = reducer.reduce(
            state = waiting.state,
            input = AmbientModeInput.TimeoutElapsed(token = 1L)
        )

        assertEquals(AmbientModeState.AmbientVisualizing, ambient.state)
        assertEquals(listOf(AmbientModeEffect.StartRealVisualizer), ambient.effects)
    }

    @Test
    fun `playing without a real source stays interactive until recovery starts a fresh timeout`() {
        val reducer = AmbientModeReducer()
        val unavailable = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(realVisualizerReady = false),
                nowMillis = 1_000L
            )
        )

        assertEquals(AmbientModeState.Interactive, unavailable.state)
        assertTrue(unavailable.effects.isEmpty())

        val recovered = reducer.reduce(
            state = unavailable.state,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(realVisualizerReady = true),
                nowMillis = 9_000L
            )
        )

        assertEquals(
            AmbientModeState.WaitingForInactivity(deadlineMillis = 24_000L, token = 1L),
            recovered.state
        )
    }

    @Test
    fun `playback stop switches real ambient directly to sleeping`() {
        val reducer = reducerWith(eligible())

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientVisualizing,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(isPlaying = false, realVisualizerReady = false),
                nowMillis = 2_000L
            )
        )

        assertEquals(AmbientModeState.AmbientSleeping, reduction.state)
        assertEquals(
            listOf(AmbientModeEffect.StopRealVisualizer, AmbientModeEffect.StartSleepingAnimation),
            reduction.effects
        )
    }

    @Test
    fun `playback start switches sleeping ambient to real source when ready`() {
        val reducer = reducerWith(eligible().copy(isPlaying = false, realVisualizerReady = false))

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientSleeping,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(),
                nowMillis = 2_000L
            )
        )

        assertEquals(AmbientModeState.AmbientVisualizing, reduction.state)
        assertEquals(
            listOf(AmbientModeEffect.StopSleepingAnimation, AmbientModeEffect.StartRealVisualizer),
            reduction.effects
        )
    }

    @Test
    fun `playback start without a real source exits sleeping ambient`() {
        val reducer = reducerWith(eligible().copy(isPlaying = false, realVisualizerReady = false))

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientSleeping,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(realVisualizerReady = false),
                nowMillis = 2_000L
            )
        )

        assertEquals(AmbientModeState.Interactive, reduction.state)
        assertEquals(listOf(AmbientModeEffect.StopSleepingAnimation), reduction.effects)
    }

    @Test
    fun `interaction exits either ambient source and resets the full timeout`() {
        val reducer = reducerWith(eligible().copy(isPlaying = false, realVisualizerReady = false))

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientSleeping,
            input = AmbientModeInput.UserInteraction(nowMillis = 20_000L)
        )

        assertEquals(
            AmbientModeState.WaitingForInactivity(deadlineMillis = 35_000L, token = 2L),
            reduction.state
        )
        assertEquals(
            listOf(
                AmbientModeEffect.StopSleepingAnimation,
                AmbientModeEffect.ScheduleTimeout(deadlineMillis = 35_000L, token = 2L)
            ),
            reduction.effects
        )
    }

    @Test
    fun `missing safety permission preference route or lifecycle always stays interactive`() {
        val cases = listOf(
            eligible().copy(routeVisible = false),
            eligible().copy(lifecycleResumed = false),
            eligible().copy(isParked = false),
            eligible().copy(isUxUnrestricted = false),
            eligible().copy(preferenceEnabled = false),
            eligible().copy(permissionGranted = false)
        )

        cases.forEach { eligibility ->
            val reduction = AmbientModeReducer().reduce(
                state = AmbientModeState.Interactive,
                input = AmbientModeInput.EligibilityChanged(eligibility, nowMillis = 1_000L)
            )

            assertTrue(
                reduction.state == AmbientModeState.Hidden ||
                    reduction.state == AmbientModeState.Interactive
            )
            assertTrue(reduction.effects.none { it is AmbientModeEffect.ScheduleTimeout })
        }
    }

    private fun reducerWith(eligibility: AmbientEligibility): AmbientModeReducer =
        AmbientModeReducer().also { reducer ->
            reducer.reduce(
                state = AmbientModeState.Interactive,
                input = AmbientModeInput.EligibilityChanged(eligibility, nowMillis = 1_000L)
            )
        }

    private fun eligible(realVisualizerReady: Boolean = true) = AmbientEligibility(
        routeVisible = true,
        lifecycleResumed = true,
        isParked = true,
        isUxUnrestricted = true,
        preferenceEnabled = true,
        permissionGranted = true,
        isPlaying = true,
        timeoutMillis = 15_000L,
        realVisualizerReady = realVisualizerReady
    )
}
