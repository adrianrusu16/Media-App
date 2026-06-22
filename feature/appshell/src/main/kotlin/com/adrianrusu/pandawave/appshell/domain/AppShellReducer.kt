package com.adrianrusu.pandawave.appshell.domain

internal object AppShellReducer {
    fun reduce(state: AppShellState, intent: AppShellIntent): AppShellState = when (intent) {
        is AppShellIntent.SelectDestination ->
            if (intent.destination.isPrimary) {
                state.copy(
                    selectedDestination = intent.destination,
                    previousPrimaryDestination = intent.destination
                )
            } else {
                state
            }

        AppShellIntent.OpenNowPlaying -> state.copy(
            selectedDestination = AppDestination.NowPlaying,
            previousPrimaryDestination = state.selectedDestination.takeIf(AppDestination::isPrimary)
                ?: state.previousPrimaryDestination
        )

        AppShellIntent.OpenProfileSettings -> state.copy(
            selectedDestination = AppDestination.ProfileSettings,
            previousPrimaryDestination = AppDestination.Profile
        )

        AppShellIntent.NavigateBack -> when (state.selectedDestination) {
            AppDestination.NowPlaying -> state.copy(selectedDestination = state.previousPrimaryDestination)
            AppDestination.ProfileSettings -> state.copy(selectedDestination = AppDestination.Profile)
            else -> state
        }

        AppShellIntent.TogglePlayback -> state

        AppShellIntent.SkipPrevious -> state

        AppShellIntent.SkipNext -> state
    }
}
