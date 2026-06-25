package com.adrianrusu.pandawave.feature.nowplaying

import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState

enum class NowPlayingMode {
    Interactive,
    SleepingAmbient,
    VisualizingAmbient
}

internal fun AmbientModeState.toNowPlayingMode(): NowPlayingMode = when (this) {
    AmbientModeState.AmbientSleeping -> NowPlayingMode.SleepingAmbient

    AmbientModeState.AmbientVisualizing -> NowPlayingMode.VisualizingAmbient

    AmbientModeState.Hidden,
    AmbientModeState.Interactive,
    is AmbientModeState.WaitingForInactivity -> NowPlayingMode.Interactive
}
