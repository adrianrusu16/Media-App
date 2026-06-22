package com.adrianrusu.pandawave.appshell.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PandaWaveNavigatorTest {
    @Test
    fun `selecting library replaces top level history with home and library`() {
        val stack = mutableListOf<NavKey>(HomeDestination, ProfileDestination)
        val navigator = PandaWaveNavigator(stack)

        navigator.selectPrimary(LibraryDestination)

        assertEquals(listOf<NavKey>(HomeDestination, LibraryDestination), stack)
    }

    @Test
    fun `selecting search replaces the previous primary destination`() {
        val stack = mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        val navigator = PandaWaveNavigator(stack)

        navigator.selectPrimary(SearchDestination)

        assertEquals(listOf<NavKey>(HomeDestination, SearchDestination), stack)
    }

    @Test
    fun `opening preferences creates home profile preferences hierarchy`() {
        val stack = mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        val navigator = PandaWaveNavigator(stack)

        navigator.openPreferences()

        assertEquals(
            listOf<NavKey>(HomeDestination, ProfileDestination, PreferencesDestination),
            stack
        )
    }

    @Test
    fun `opening now playing from preferences preserves profile as back target`() {
        val stack = mutableListOf<NavKey>(
            HomeDestination,
            ProfileDestination,
            PreferencesDestination
        )
        val navigator = PandaWaveNavigator(stack)

        navigator.openNowPlaying()

        assertEquals(
            listOf<NavKey>(HomeDestination, ProfileDestination, NowPlayingDestination),
            stack
        )
    }

    @Test
    fun `home is the only root navigation stack`() {
        val homeNavigator = PandaWaveNavigator(mutableListOf<NavKey>(HomeDestination))
        val libraryNavigator = PandaWaveNavigator(
            mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        )

        assertEquals(true, homeNavigator.isAtRoot)
        assertEquals(false, libraryNavigator.isAtRoot)
    }

    @Test
    fun `selecting the active primary destination does not duplicate it`() {
        val stack = mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        val navigator = PandaWaveNavigator(stack)

        navigator.selectPrimary(LibraryDestination)

        assertEquals(listOf<NavKey>(HomeDestination, LibraryDestination), stack)
    }

    @Test
    fun `current destination is derived from the top stack entry`() {
        val navigator = PandaWaveNavigator(
            mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        )

        assertEquals(LibraryDestination, navigator.currentDestination)
    }

    @Test
    fun `pop removes one destination but never removes home`() {
        val stack = mutableListOf<NavKey>(HomeDestination, LibraryDestination)
        val navigator = PandaWaveNavigator(stack)

        assertEquals(true, navigator.pop())
        assertEquals(listOf<NavKey>(HomeDestination), stack)
        assertEquals(false, navigator.pop())
        assertEquals(listOf<NavKey>(HomeDestination), stack)
    }
}
