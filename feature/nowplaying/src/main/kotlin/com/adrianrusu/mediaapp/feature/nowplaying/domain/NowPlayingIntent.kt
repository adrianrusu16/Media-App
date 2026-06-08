package com.adrianrusu.mediaapp.feature.nowplaying.domain

sealed interface NowPlayingIntent {
    data object Refresh : NowPlayingIntent
    data object TogglePlayback : NowPlayingIntent
    data object SkipPrevious : NowPlayingIntent
    data object SkipNext : NowPlayingIntent
}
