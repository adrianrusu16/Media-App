package com.adrianrusu.pandawave.appshell.presentation

import com.adrianrusu.pandawave.appshell.navigation.PandaWaveDestination
import com.adrianrusu.pandawave.appshell.navigation.shouldShowMiniPlayer

internal data class AppShellChrome(
    val showNavigationRail: Boolean,
    val showMiniPlayer: Boolean,
    val applyContentPadding: Boolean
)

internal fun resolveAppShellChrome(currentDestination: PandaWaveDestination, ambientVisible: Boolean): AppShellChrome =
    AppShellChrome(
        showNavigationRail = !ambientVisible,
        showMiniPlayer = !ambientVisible && currentDestination.shouldShowMiniPlayer,
        applyContentPadding = !ambientVisible
    )
