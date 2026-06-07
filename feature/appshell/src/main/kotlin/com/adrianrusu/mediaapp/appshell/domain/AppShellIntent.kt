package com.adrianrusu.mediaapp.appshell.domain

sealed interface AppShellIntent {
    data class SelectDestination(val destination: AppDestination) : AppShellIntent

    data object SkipPrevious : AppShellIntent

    data object SkipNext : AppShellIntent

    data object TogglePlayback : AppShellIntent
}
