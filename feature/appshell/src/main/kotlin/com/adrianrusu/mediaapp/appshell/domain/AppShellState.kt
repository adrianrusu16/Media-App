package com.adrianrusu.mediaapp.appshell.domain

import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState
import com.adrianrusu.mediaapp.core.ui.playback.BambooEngineConnectionText

data class AppShellState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val restriction: RestrictionUiState = RestrictionUiState.Unavailable,
    val engineConnection: EngineConnectionUiState = EngineConnectionUiState.Connecting,
    val miniPlayer: MiniPlayerState = MiniPlayerState.Empty
) {
    val destinations: List<AppDestination> = AppDestination.entries
    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == EngineConnectionStatus.Ready
}

data class EngineConnectionUiState(val label: String, val status: EngineConnectionStatus) {
    companion object {
        val Connecting = EngineConnectionUiState(
            label = BambooEngineConnectionText.CONNECTING,
            status = EngineConnectionStatus.Connecting
        )
        val Ready = EngineConnectionUiState(
            label = BambooEngineConnectionText.READY,
            status = EngineConnectionStatus.Ready
        )
        val Reconnecting = EngineConnectionUiState(
            label = BambooEngineConnectionText.RECONNECTING,
            status = EngineConnectionStatus.Reconnecting
        )
        val Unavailable = EngineConnectionUiState(
            label = BambooEngineConnectionText.UNAVAILABLE,
            status = EngineConnectionStatus.Unavailable
        )
    }
}

enum class EngineConnectionStatus {
    Connecting,
    Ready,
    Reconnecting,
    Unavailable
}

data class RestrictionUiState(val label: String, val isRestricted: Boolean) {
    companion object {
        val Unavailable = RestrictionUiState(
            label = "Safety status unavailable",
            isRestricted = false
        )
    }
}
