package com.adrianrusu.mediaapp.appshell.domain

import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState

data class AppShellState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val restriction: RestrictionUiState = RestrictionUiState.Unavailable,
    val miniPlayer: MiniPlayerState = MiniPlayerState.Empty,
) {
    val destinations: List<AppDestination> = AppDestination.entries
}

data class RestrictionUiState(
    val label: String,
    val isRestricted: Boolean,
) {
    companion object {
        val Unavailable = RestrictionUiState(
            label = "Safety status unavailable",
            isRestricted = false,
        )
    }
}
