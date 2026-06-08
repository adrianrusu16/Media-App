package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.ui.playback.BambooEngineConnectionText
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class NowPlayingState(
    val mediaId: String? = null,
    val title: String = BambooPlaybackText.FALLBACK_IDLE_TITLE,
    val artist: String = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val engineConnection: NowPlayingEngineConnectionUiState = NowPlayingEngineConnectionUiState.Connecting,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val primaryActionLabel: String
        get() = if (isPlaying) BambooPlaybackText.ACTION_PAUSE else BambooPlaybackText.ACTION_PLAY

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == NowPlayingEngineConnectionStatus.Ready

    val detailLabel: String
        get() = artist
}

data class NowPlayingEngineConnectionUiState(val label: String, val status: NowPlayingEngineConnectionStatus) {
    companion object {
        val Connecting = NowPlayingEngineConnectionUiState(
            label = BambooEngineConnectionText.CONNECTING,
            status = NowPlayingEngineConnectionStatus.Connecting
        )
        val Ready = NowPlayingEngineConnectionUiState(
            label = BambooEngineConnectionText.READY,
            status = NowPlayingEngineConnectionStatus.Ready
        )
        val Reconnecting = NowPlayingEngineConnectionUiState(
            label = BambooEngineConnectionText.RECONNECTING,
            status = NowPlayingEngineConnectionStatus.Reconnecting
        )
        val Unavailable = NowPlayingEngineConnectionUiState(
            label = BambooEngineConnectionText.UNAVAILABLE,
            status = NowPlayingEngineConnectionStatus.Unavailable
        )
    }
}

enum class NowPlayingEngineConnectionStatus {
    Connecting,
    Ready,
    Reconnecting,
    Unavailable
}
