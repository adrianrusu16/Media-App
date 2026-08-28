package com.adrianrusu.pandawave.appshell.presentation

import com.adrianrusu.pandawave.appshell.navigation.HomeDestination
import com.adrianrusu.pandawave.appshell.navigation.NowPlayingDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellChromeTest {
    @Test
    fun `ambient now playing hides rail mini player and content padding`() {
        assertEquals(
            AppShellChrome(
                showNavigationRail = false,
                showMiniPlayer = false,
                applyContentPadding = false
            ),
            resolveAppShellChrome(
                currentDestination = NowPlayingDestination,
                ambientVisible = true
            )
        )
    }

    @Test
    fun `interactive destinations keep their normal shell chrome`() {
        assertEquals(
            AppShellChrome(
                showNavigationRail = true,
                showMiniPlayer = true,
                applyContentPadding = true
            ),
            resolveAppShellChrome(
                currentDestination = HomeDestination,
                ambientVisible = false
            )
        )
        assertEquals(
            AppShellChrome(
                showNavigationRail = true,
                showMiniPlayer = false,
                applyContentPadding = false
            ),
            resolveAppShellChrome(
                currentDestination = NowPlayingDestination,
                ambientVisible = false
            )
        )
    }
}
