package com.adrianrusu.pandawave.appshell.domain

sealed interface AppShellIntent {
    data object SkipPrevious : AppShellIntent

    data object SkipNext : AppShellIntent

    data object TogglePlayback : AppShellIntent
}
