package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.ui.playback.BambooEngineConnectionText
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class BambooPlaybackState(
    val mediaId: String? = null,
    val title: String = BambooPlaybackText.FALLBACK_IDLE_TITLE,
    val artist: String = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
    val playbackStatus: BambooPlaybackStatus = BambooPlaybackStatus.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: BambooPlaybackRestrictionState = BambooPlaybackRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L
) {
    val isPlaying: Boolean
        get() = playbackStatus == BambooPlaybackStatus.Playing

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready
}

enum class BambooPlaybackStatus {
    Idle,
    Playing,
    Paused
}

data class BambooEngineConnectionUiState(val label: String, val status: BambooEngineConnectionStatus) {
    companion object {
        val Connecting = BambooEngineConnectionUiState(
            label = BambooEngineConnectionText.CONNECTING,
            status = BambooEngineConnectionStatus.Connecting
        )
        val Ready = BambooEngineConnectionUiState(
            label = BambooEngineConnectionText.READY,
            status = BambooEngineConnectionStatus.Ready
        )
        val Reconnecting = BambooEngineConnectionUiState(
            label = BambooEngineConnectionText.RECONNECTING,
            status = BambooEngineConnectionStatus.Reconnecting
        )
        val Unavailable = BambooEngineConnectionUiState(
            label = BambooEngineConnectionText.UNAVAILABLE,
            status = BambooEngineConnectionStatus.Unavailable
        )
    }
}

enum class BambooEngineConnectionStatus {
    Connecting,
    Ready,
    Reconnecting,
    Unavailable
}

data class BambooPlaybackRestrictionState(val label: String, val isRestricted: Boolean) {
    companion object {
        val Unavailable = BambooPlaybackRestrictionState(
            label = "Safety status unavailable",
            isRestricted = false
        )
    }
}
