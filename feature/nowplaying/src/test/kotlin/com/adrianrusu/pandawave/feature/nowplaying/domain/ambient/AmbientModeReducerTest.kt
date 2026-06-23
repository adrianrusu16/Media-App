package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmbientModeReducerTest {
    @Test
    fun `complete eligibility enters waiting then visualizing ambient mode`() {
        val reducer = AmbientModeReducer()
        val waiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(),
                nowMillis = 1_000L
            )
        )

        assertEquals(
            AmbientModeState.WaitingForInactivity(
                deadlineMillis = 16_000L,
                token = 1L
            ),
            waiting.state
        )
        assertEquals(
            listOf(AmbientModeEffect.ScheduleTimeout(deadlineMillis = 16_000L, token = 1L)),
            waiting.effects
        )

        val ambient = reducer.reduce(
            state = waiting.state,
            input = AmbientModeInput.TimeoutElapsed(token = 1L)
        )

        assertEquals(AmbientModeState.AmbientVisualizing, ambient.state)
        assertEquals(listOf(AmbientModeEffect.StartVisualizer), ambient.effects)
    }

    @Test
    fun `missing presentation or ambient eligibility never schedules ambient mode`() {
        val cases = listOf(
            eligible().copy(routeVisible = false),
            eligible().copy(lifecycleResumed = false),
            eligible().copy(safetyPermitted = false),
            eligible().copy(preferenceEnabled = false),
            eligible().copy(isPlaying = false)
        )

        cases.forEach { eligibility ->
            val reduction = AmbientModeReducer().reduce(
                state = AmbientModeState.Interactive,
                input = AmbientModeInput.EligibilityChanged(
                    eligibility = eligibility,
                    nowMillis = 1_000L
                )
            )

            assertTrue(
                reduction.state == AmbientModeState.Hidden ||
                    reduction.state == AmbientModeState.Interactive
            )
            assertTrue(reduction.effects.none { it is AmbientModeEffect.ScheduleTimeout })
        }
    }

    @Test
    fun `unavailable visualizer enters static ambient mode after inactivity`() {
        val reducer = AmbientModeReducer()
        val waiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(visualizerAvailable = false),
                nowMillis = 1_000L
            )
        )

        val ambient = reducer.reduce(
            state = waiting.state,
            input = AmbientModeInput.TimeoutElapsed(
                token = assertIs<AmbientModeState.WaitingForInactivity>(waiting.state).token
            )
        )

        assertEquals(AmbientModeState.AmbientStatic, ambient.state)
        assertTrue(ambient.effects.none { it == AmbientModeEffect.StartVisualizer })
    }

    @Test
    fun `stale timeout token cannot enter ambient mode`() {
        val reducer = AmbientModeReducer()
        val waiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(),
                nowMillis = 1_000L
            )
        )

        val reduction = reducer.reduce(
            state = waiting.state,
            input = AmbientModeInput.TimeoutElapsed(token = 99L)
        )

        assertEquals(waiting.state, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `interaction exits ambient mode and resets the full timeout`() {
        val reducer = AmbientModeReducer()
        val firstWaiting = reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(),
                nowMillis = 1_000L
            )
        )
        reducer.reduce(
            state = firstWaiting.state,
            input = AmbientModeInput.TimeoutElapsed(token = 1L)
        )

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientVisualizing,
            input = AmbientModeInput.UserInteraction(nowMillis = 20_000L)
        )

        assertEquals(
            AmbientModeState.WaitingForInactivity(
                deadlineMillis = 35_000L,
                token = 2L
            ),
            reduction.state
        )
        assertEquals(
            listOf(
                AmbientModeEffect.StopVisualizer,
                AmbientModeEffect.ScheduleTimeout(deadlineMillis = 35_000L, token = 2L)
            ),
            reduction.effects
        )
    }

    @Test
    fun `safety loss exits ambient mode immediately`() {
        val reducer = AmbientModeReducer()
        reducer.reduce(
            state = AmbientModeState.Interactive,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible(),
                nowMillis = 1_000L
            )
        )

        val reduction = reducer.reduce(
            state = AmbientModeState.AmbientVisualizing,
            input = AmbientModeInput.EligibilityChanged(
                eligibility = eligible().copy(safetyPermitted = false),
                nowMillis = 2_000L
            )
        )

        assertEquals(AmbientModeState.Interactive, reduction.state)
        assertEquals(listOf(AmbientModeEffect.StopVisualizer), reduction.effects)
        assertEquals(AmbientModeTransition.Immediate, reduction.transition)
    }

    private fun eligible() = AmbientEligibility(
        routeVisible = true,
        lifecycleResumed = true,
        safetyPermitted = true,
        preferenceEnabled = true,
        isPlaying = true,
        timeoutMillis = 15_000L,
        visualizerAvailable = true
    )
}
