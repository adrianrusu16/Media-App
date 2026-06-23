package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

class AmbientModeReducer {
    private var currentEligibility = AmbientEligibility()
    private var nextToken = 0L

    fun reduce(state: AmbientModeState, input: AmbientModeInput): AmbientModeReduction = when (input) {
        is AmbientModeInput.EligibilityChanged -> reduceEligibility(
            state = state,
            eligibility = input.eligibility,
            nowMillis = input.nowMillis
        )

        is AmbientModeInput.TimeoutElapsed -> reduceTimeout(
            state = state,
            token = input.token
        )

        is AmbientModeInput.UserInteraction -> reduceInteraction(
            state = state,
            nowMillis = input.nowMillis
        )
    }

    private fun reduceEligibility(
        state: AmbientModeState,
        eligibility: AmbientEligibility,
        nowMillis: Long
    ): AmbientModeReduction {
        val previousEligibility = currentEligibility
        currentEligibility = eligibility

        if (!eligibility.presentationVisible) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Hidden,
                transition = AmbientModeTransition.Immediate
            )
        }
        if (!eligibility.ambientPermitted) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Interactive,
                transition = if (!eligibility.safetyPermitted) {
                    AmbientModeTransition.Immediate
                } else {
                    AmbientModeTransition.Animated
                }
            )
        }

        return when (state) {
            AmbientModeState.Hidden,
            AmbientModeState.Interactive -> waitForInactivity(nowMillis)

            is AmbientModeState.WaitingForInactivity -> {
                if (eligibility.timeoutMillis != previousEligibility.timeoutMillis) {
                    waitForInactivity(
                        nowMillis = nowMillis,
                        effectsBeforeSchedule = listOf(AmbientModeEffect.CancelTimeout)
                    )
                } else {
                    AmbientModeReduction(state)
                }
            }

            AmbientModeState.AmbientStatic -> {
                if (eligibility.visualizerAvailable) {
                    AmbientModeReduction(
                        state = AmbientModeState.AmbientVisualizing,
                        effects = listOf(AmbientModeEffect.StartVisualizer)
                    )
                } else {
                    AmbientModeReduction(state)
                }
            }

            AmbientModeState.AmbientVisualizing -> {
                if (eligibility.visualizerAvailable) {
                    AmbientModeReduction(state)
                } else {
                    AmbientModeReduction(
                        state = AmbientModeState.AmbientStatic,
                        effects = listOf(AmbientModeEffect.StopVisualizer)
                    )
                }
            }
        }
    }

    private fun reduceTimeout(state: AmbientModeState, token: Long): AmbientModeReduction {
        val waiting = state as? AmbientModeState.WaitingForInactivity
            ?: return AmbientModeReduction(state)
        if (waiting.token != token || !currentEligibility.ambientPermitted) {
            return AmbientModeReduction(state)
        }

        return if (currentEligibility.visualizerAvailable) {
            AmbientModeReduction(
                state = AmbientModeState.AmbientVisualizing,
                effects = listOf(AmbientModeEffect.StartVisualizer)
            )
        } else {
            AmbientModeReduction(state = AmbientModeState.AmbientStatic)
        }
    }

    private fun reduceInteraction(state: AmbientModeState, nowMillis: Long): AmbientModeReduction {
        if (!currentEligibility.presentationVisible) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Hidden,
                transition = AmbientModeTransition.Immediate
            )
        }
        if (!currentEligibility.ambientPermitted) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Interactive,
                transition = AmbientModeTransition.Immediate
            )
        }

        val effects = buildList {
            when (state) {
                AmbientModeState.AmbientVisualizing -> add(AmbientModeEffect.StopVisualizer)
                is AmbientModeState.WaitingForInactivity -> add(AmbientModeEffect.CancelTimeout)
                else -> Unit
            }
        }
        return waitForInactivity(
            nowMillis = nowMillis,
            effectsBeforeSchedule = effects
        )
    }

    private fun waitForInactivity(
        nowMillis: Long,
        effectsBeforeSchedule: List<AmbientModeEffect> = emptyList()
    ): AmbientModeReduction {
        val token = ++nextToken
        val deadlineMillis = nowMillis + currentEligibility.timeoutMillis.coerceAtLeast(0L)
        return AmbientModeReduction(
            state = AmbientModeState.WaitingForInactivity(
                deadlineMillis = deadlineMillis,
                token = token
            ),
            effects = effectsBeforeSchedule + AmbientModeEffect.ScheduleTimeout(
                deadlineMillis = deadlineMillis,
                token = token
            )
        )
    }

    private fun exitAmbient(
        state: AmbientModeState,
        target: AmbientModeState,
        transition: AmbientModeTransition
    ): AmbientModeReduction {
        val effects = buildList {
            if (state is AmbientModeState.WaitingForInactivity) {
                add(AmbientModeEffect.CancelTimeout)
            }
            if (state == AmbientModeState.AmbientVisualizing) {
                add(AmbientModeEffect.StopVisualizer)
            }
        }
        return AmbientModeReduction(
            state = target,
            effects = effects,
            transition = transition
        )
    }
}
