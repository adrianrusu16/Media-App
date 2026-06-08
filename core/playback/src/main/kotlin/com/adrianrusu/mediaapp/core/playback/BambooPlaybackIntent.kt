package com.adrianrusu.mediaapp.core.playback

sealed interface BambooPlaybackIntent {
    data object Refresh : BambooPlaybackIntent
    data object TogglePlayback : BambooPlaybackIntent
    data object SkipPrevious : BambooPlaybackIntent
    data object SkipNext : BambooPlaybackIntent
}
