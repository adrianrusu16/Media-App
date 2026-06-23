package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

sealed interface AmbientModeEffect {
    data class ScheduleTimeout(val deadlineMillis: Long, val token: Long) : AmbientModeEffect

    data object CancelTimeout : AmbientModeEffect

    data object StartVisualizer : AmbientModeEffect

    data object StopVisualizer : AmbientModeEffect
}
