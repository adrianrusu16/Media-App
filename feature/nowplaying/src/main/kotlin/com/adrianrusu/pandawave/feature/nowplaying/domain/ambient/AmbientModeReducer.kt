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

        is AmbientModeInput.TimeoutElapsed -> reduceTimeout(state = state, token = input.token)

        is AmbientModeInput.UserInteraction -> reduceInteraction(state = state, nowMillis = input.nowMillis)
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
                transition = if (
                    !eligibility.isParked ||
                    !eligibility.isUxUnrestricted ||
                    !eligibility.permissionGranted
                ) {
                    AmbientModeTransition.Immediate
                } else {
                    AmbientModeTransition.Animated
                }
            )
        }
        if (!eligibility.hasUsableAmplitudeSource) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Interactive,
                transition = AmbientModeTransition.Animated
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

            AmbientModeState.AmbientSleeping -> {
                if (eligibility.isPlaying) {
                    AmbientModeReduction(
                        state = AmbientModeState.AmbientVisualizing,
                        effects = listOf(
                            AmbientModeEffect.StopSleepingAnimation,
                            AmbientModeEffect.StartRealVisualizer
                        )
                    )
                } else {
                    AmbientModeReduction(state)
                }
            }

            AmbientModeState.AmbientVisualizing -> {
                if (eligibility.isPlaying) {
                    AmbientModeReduction(state)
                } else {
                    AmbientModeReduction(
                        state = AmbientModeState.AmbientSleeping,
                        effects = listOf(
                            AmbientModeEffect.StopRealVisualizer,
                            AmbientModeEffect.StartSleepingAnimation
                        )
                    )
                }
            }
        }
    }

    private fun reduceTimeout(state: AmbientModeState, token: Long): AmbientModeReduction {
        val waiting = state as? AmbientModeState.WaitingForInactivity
            ?: return AmbientModeReduction(state)
        if (
            waiting.token != token ||
            !currentEligibility.ambientPermitted ||
            !currentEligibility.hasUsableAmplitudeSource
        ) {
            return AmbientModeReduction(state)
        }

        return if (currentEligibility.isPlaying) {
            AmbientModeReduction(
                state = AmbientModeState.AmbientVisualizing,
                effects = listOf(AmbientModeEffect.StartRealVisualizer)
            )
        } else {
            AmbientModeReduction(
                state = AmbientModeState.AmbientSleeping,
                effects = listOf(AmbientModeEffect.StartSleepingAnimation)
            )
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
        if (!currentEligibility.ambientPermitted || !currentEligibility.hasUsableAmplitudeSource) {
            return exitAmbient(
                state = state,
                target = AmbientModeState.Interactive,
                transition = AmbientModeTransition.Immediate
            )
        }

        val effects = buildList {
            when (state) {
                AmbientModeState.AmbientSleeping -> add(AmbientModeEffect.StopSleepingAnimation)
                AmbientModeState.AmbientVisualizing -> add(AmbientModeEffect.StopRealVisualizer)
                is AmbientModeState.WaitingForInactivity -> add(AmbientModeEffect.CancelTimeout)
                else -> Unit
            }
        }
        return waitForInactivity(nowMillis = nowMillis, effectsBeforeSchedule = effects)
    }

    private fun waitForInactivity(
        nowMillis: Long,
        effectsBeforeSchedule: List<AmbientModeEffect> = emptyList()
    ): AmbientModeReduction {
        val token = ++nextToken
        val deadlineMillis = nowMillis + currentEligibility.timeoutMillis.coerceAtLeast(0L)
        return AmbientModeReduction(
            state = AmbientModeState.WaitingForInactivity(deadlineMillis = deadlineMillis, token = token),
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
            when (state) {
                is AmbientModeState.WaitingForInactivity -> add(AmbientModeEffect.CancelTimeout)
                AmbientModeState.AmbientSleeping -> add(AmbientModeEffect.StopSleepingAnimation)
                AmbientModeState.AmbientVisualizing -> add(AmbientModeEffect.StopRealVisualizer)
                else -> Unit
            }
        }
        return AmbientModeReduction(
            state = target,
            effects = effects,
            transition = transition
        )
    }
}
