package com.adrianrusu.pandawave.feature.nowplaying.domain

sealed interface NowPlayingIntent {
    data object Refresh : NowPlayingIntent
    data object TogglePlayback : NowPlayingIntent
    data object SkipPrevious : NowPlayingIntent
    data object SkipNext : NowPlayingIntent
    data class SetVolume(val volume: Float) : NowPlayingIntent
}
