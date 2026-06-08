package com.adrianrusu.mediaapp.appshell.domain

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState

data class AppShellState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val restriction: RestrictionUiState = RestrictionUiState.Unavailable,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val miniPlayer: MiniPlayerState = MiniPlayerState.Empty
) {
    val destinations: List<AppDestination> = AppDestination.entries
    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready

    val shouldShowMiniPlayer: Boolean
        get() = selectedDestination != AppDestination.NowPlaying
}

data class RestrictionUiState(val label: String, val isRestricted: Boolean) {
    companion object {
        val Unavailable = RestrictionUiState(
            label = "Safety status unavailable",
            isRestricted = false
        )
    }
}
