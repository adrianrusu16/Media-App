package com.adrianrusu.mediaapp.appshell.domain

internal object AppShellReducer {
    fun reduce(
        state: AppShellState,
        intent: AppShellIntent,
    ): AppShellState =
        when (intent) {
            is AppShellIntent.SelectDestination ->
                state.copy(selectedDestination = intent.destination)

            AppShellIntent.TogglePlayback ->
                state.copy(
                    miniPlayer = state.miniPlayer.copy(
                        title = if (state.miniPlayer.isPlaying) {
                            "Paused"
                        } else {
                            "Sample station"
                        },
                        subtitle = if (state.miniPlayer.isPlaying) {
                            "Ready to resume"
                        } else {
                            "Preview queue"
                        },
                        isPlaying = !state.miniPlayer.isPlaying,
                    ),
                )
        }
}
