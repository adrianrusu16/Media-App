package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

sealed interface AmbientModeState {
    data object Hidden : AmbientModeState

    data object Interactive : AmbientModeState

    data class WaitingForInactivity(val deadlineMillis: Long, val token: Long) : AmbientModeState

    data object AmbientSleeping : AmbientModeState

    data object AmbientVisualizing : AmbientModeState
}

data class AmbientEligibility(
    val routeVisible: Boolean = false,
    val lifecycleResumed: Boolean = false,
    val isParked: Boolean = false,
    val isUxUnrestricted: Boolean = false,
    val preferenceEnabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val isPlaying: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val realVisualizerReady: Boolean = false
) {
    val presentationVisible: Boolean
        get() = routeVisible && lifecycleResumed

    val ambientPermitted: Boolean
        get() = presentationVisible &&
            isParked &&
            isUxUnrestricted &&
            preferenceEnabled &&
            permissionGranted

    val hasUsableAmplitudeSource: Boolean
        get() = !isPlaying || realVisualizerReady

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}

enum class AmbientModeTransition {
    Animated,
    Immediate
}

data class AmbientModeReduction(
    val state: AmbientModeState,
    val effects: List<AmbientModeEffect> = emptyList(),
    val transition: AmbientModeTransition = AmbientModeTransition.Animated
)
