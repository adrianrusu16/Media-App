package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgress
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgressAnchor
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgressProjector
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = BambooPlaybackText.FALLBACK_IDLE_TITLE,
    val artist: String = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L,
    val progressAnchor: BambooPlaybackProgressAnchor = BambooPlaybackProgressAnchor()
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val primaryActionLabel: String
        get() = if (isPlaying) BambooPlaybackText.ACTION_PAUSE else BambooPlaybackText.ACTION_PLAY

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready

    val detailLabel: String
        get() = artist

    fun progressAt(nowMillis: Long): BambooPlaybackProgress = BambooPlaybackProgressProjector.fromAnchor(
        anchor = progressAnchor,
        nowMillis = nowMillis
    )
}
