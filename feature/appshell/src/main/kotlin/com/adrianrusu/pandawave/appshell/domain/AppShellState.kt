package com.adrianrusu.pandawave.appshell.domain

import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.ui.miniplayer.MiniPlayerState

data class AppShellState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val previousPrimaryDestination: AppDestination = AppDestination.Home,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val miniPlayer: MiniPlayerState = MiniPlayerState.Empty
) {
    val destinations: List<AppDestination> = AppDestination.railDestinations
    val selectedRailDestination: AppDestination?
        get() = when {
            selectedDestination == AppDestination.ProfileSettings -> AppDestination.Profile
            selectedDestination.isPrimary -> selectedDestination
            else -> null
        }

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready

    val shouldShowMiniPlayer: Boolean
        get() = selectedDestination != AppDestination.NowPlaying
}
