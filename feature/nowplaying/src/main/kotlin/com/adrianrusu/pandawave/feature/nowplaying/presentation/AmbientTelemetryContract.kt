package com.adrianrusu.pandawave.feature.nowplaying.presentation

import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeInput
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState

internal object AmbientTelemetryEvents {
    const val ENTERED = "ambient.entered"
    const val EXITED = "ambient.exited"
    const val VISUALIZER_UNAVAILABLE = "ambient.visualizer.unavailable"
}

internal object AmbientTelemetryAttributes {
    const val MODE = "mode"
    const val REASON = "reason"
}

internal object AmbientTelemetryModes {
    const val STATIC = "static"
    const val VISUALIZING = "visualizing"
}

internal object AmbientTelemetryExitReasons {
    const val INTERACTION = "interaction"
    const val PLAYBACK_INACTIVE = "playback_inactive"
    const val SAFETY_LOST = "safety_lost"
    const val ROUTE_HIDDEN = "route_hidden"
    const val DISABLED = "disabled"
    const val STATE_CHANGED = "state_changed"

    fun from(input: AmbientModeInput): String = when (input) {
        is AmbientModeInput.UserInteraction -> INTERACTION

        is AmbientModeInput.EligibilityChanged -> with(input.eligibility) {
            when {
                !presentationVisible -> ROUTE_HIDDEN
                !safetyPermitted -> SAFETY_LOST
                !preferenceEnabled -> DISABLED
                !isPlaying -> PLAYBACK_INACTIVE
                else -> STATE_CHANGED
            }
        }

        is AmbientModeInput.TimeoutElapsed -> STATE_CHANGED
    }
}

internal object AmbientTelemetryVisualizerReasons {
    const val PERMISSION_DENIED = "permission_denied"
    const val UNSUPPORTED = "unsupported"
    const val INVALID_SESSION = "invalid_session"
    const val INITIALIZATION_FAILED = "initialization_failed"
    const val RUNTIME_FAILED = "runtime_failed"

    fun from(reason: AmbientVisualizerAvailability.Reason): String = when (reason) {
        AmbientVisualizerAvailability.Reason.PermissionDenied -> PERMISSION_DENIED
        AmbientVisualizerAvailability.Reason.Unsupported -> UNSUPPORTED
        AmbientVisualizerAvailability.Reason.InvalidSession -> INVALID_SESSION
        AmbientVisualizerAvailability.Reason.InitializationFailed -> INITIALIZATION_FAILED
        AmbientVisualizerAvailability.Reason.RuntimeFailed -> RUNTIME_FAILED
    }
}

internal val AmbientModeState.isAmbientPresentation: Boolean
    get() = this == AmbientModeState.AmbientStatic || this == AmbientModeState.AmbientVisualizing

internal val AmbientModeState.telemetryMode: String
    get() = when (this) {
        AmbientModeState.AmbientVisualizing -> AmbientTelemetryModes.VISUALIZING
        else -> AmbientTelemetryModes.STATIC
    }
