package com.adrianrusu.mediaapp.appshell.domain

internal object AppShellReducer {
    fun reduce(state: AppShellState, intent: AppShellIntent): AppShellState = when (intent) {
        is AppShellIntent.SelectDestination ->
            state.copy(selectedDestination = intent.destination)

        AppShellIntent.TogglePlayback -> state
    }
}
