package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgress
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgressAnchor
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgressProjector

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L,
    val progressAnchor: BambooPlaybackProgressAnchor = BambooPlaybackProgressAnchor()
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready

    fun progressAt(nowMillis: Long): BambooPlaybackProgress = BambooPlaybackProgressProjector.fromAnchor(
        anchor = progressAnchor,
        nowMillis = nowMillis
    )
}
