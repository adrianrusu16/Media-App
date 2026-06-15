package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.ui.playback.BambooEngineConnectionText
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class BambooPlaybackState(
    val mediaId: String? = null,
    val title: String = BambooPlaybackText.FALLBACK_IDLE_TITLE,
    val artist: String = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
    val album: String? = null,
    val durationMillis: Long? = null,
    val artworkUri: String? = null,
    val playbackStatus: BambooPlaybackStatus = BambooPlaybackStatus.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: BambooPlaybackRestrictionState = BambooPlaybackRestrictionState.Unavailable,
    val updatedAtEpochMillis: Long = 0L,
    val positionMillis: Long = 0L,
    val playbackSpeed: Float = 1F,
    val hasActiveSession: Boolean = false,
    val hasError: Boolean = false,
    val errorType: String = "none",
    val searchResultsCount: Int = 0,
    val browseResultsCount: Int = 0,
    val isBusy: Boolean = false,
    val canDispatch: Boolean = true,
    val controls: BambooPlaybackControls = BambooPlaybackControls.default()
) {
    val isPlaying: Boolean
        get() = playbackStatus == BambooPlaybackStatus.Playing

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready && canDispatch
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

data class BambooControlState(val isVisible: Boolean, val isEnabled: Boolean, val isActive: Boolean) {
    companion object {
        fun hidden(): BambooControlState = BambooControlState(
            isVisible = false,
            isEnabled = false,
            isActive = false
        )

        fun enabled(): BambooControlState = BambooControlState(
            isVisible = true,
            isEnabled = true,
            isActive = false
        )
    }
}

data class BambooPlaybackControls(
    val playPause: BambooControlState,
    val skipNext: BambooControlState,
    val skipPrevious: BambooControlState,
    val showPlayIcon: Boolean
) {
    companion object {
        fun default(): BambooPlaybackControls = BambooPlaybackControls(
            playPause = BambooControlState.hidden(),
            skipNext = BambooControlState.hidden(),
            skipPrevious = BambooControlState.hidden(),
            showPlayIcon = true
        )
    }
}
