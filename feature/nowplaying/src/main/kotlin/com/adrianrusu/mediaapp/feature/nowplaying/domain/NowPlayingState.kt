package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = BambooPlaybackText.FALLBACK_IDLE_TITLE,
    val artist: String = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val primaryActionLabel: String
        get() = if (isPlaying) BambooPlaybackText.ACTION_PAUSE else BambooPlaybackText.ACTION_PLAY

    val detailLabel: String
        get() = artist
}
