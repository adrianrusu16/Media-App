package com.adrianrusu.mediaapp.appshell.domain

import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState

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
            label = ENGINE_CONNECTING_LABEL,
            status = EngineConnectionStatus.Connecting
        )
        val Ready = EngineConnectionUiState(
            label = ENGINE_READY_LABEL,
            status = EngineConnectionStatus.Ready
        )
        val Reconnecting = EngineConnectionUiState(
            label = ENGINE_RECONNECTING_LABEL,
            status = EngineConnectionStatus.Reconnecting
        )
        val Unavailable = EngineConnectionUiState(
            label = ENGINE_UNAVAILABLE_LABEL,
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

private const val ENGINE_CONNECTING_LABEL = "PandaEngine connecting"
private const val ENGINE_READY_LABEL = "PandaEngine ready"
private const val ENGINE_RECONNECTING_LABEL = "PandaEngine reconnecting"
private const val ENGINE_UNAVAILABLE_LABEL = "PandaEngine unavailable"
