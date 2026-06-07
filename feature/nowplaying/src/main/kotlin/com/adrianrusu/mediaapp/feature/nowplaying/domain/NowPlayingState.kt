package com.adrianrusu.mediaapp.feature.nowplaying.domain

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = "Nothing playing",
    val artist: String = "Ready when you are",
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val primaryActionLabel: String
        get() = if (isPlaying) "Pause" else "Play"

    val detailLabel: String
        get() = if (restriction.isRestricted) "Driver-safe metadata" else artist
}
