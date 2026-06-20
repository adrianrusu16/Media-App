package com.adrianrusu.mediaapp.appshell.domain

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState

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
