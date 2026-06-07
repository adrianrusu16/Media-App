package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.ui.playback.PlaybackDisplayText

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = PlaybackDisplayText.FALLBACK_IDLE_TITLE,
    val artist: String = PlaybackDisplayText.FALLBACK_IDLE_SUBTITLE,
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val primaryActionLabel: String
        get() = if (isPlaying) PlaybackDisplayText.ACTION_PAUSE else PlaybackDisplayText.ACTION_PLAY

    val detailLabel: String
        get() = artist
}
