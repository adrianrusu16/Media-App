package com.adrianrusu.pandawave.appshell.navigation

import androidx.navigation3.runtime.NavKey

class PandaWaveNavigator(private val backStack: MutableList<NavKey>) {
    val currentDestination: PandaWaveDestination
        get() = backStack.last() as PandaWaveDestination

    val isAtRoot: Boolean
        get() = backStack.size == 1 && backStack.single() == HomeDestination

    fun selectPrimary(destination: PandaWaveDestination) {
        require(destination in primaryDestinations)
        backStack.clear()
        backStack += HomeDestination
        if (destination != HomeDestination) {
            backStack += destination
        }
    }

    fun openPreferences() {
        backStack.clear()
        backStack += listOf(HomeDestination, ProfileDestination, PreferencesDestination)
    }

    fun openNowPlaying() {
        val primaryDestination = backStack
            .asReversed()
            .filterIsInstance<PandaWaveDestination>()
            .firstOrNull(primaryDestinations::contains)
            ?: HomeDestination

        backStack.clear()
        backStack += HomeDestination
        if (primaryDestination != HomeDestination) {
            backStack += primaryDestination
        }
        backStack += NowPlayingDestination
    }

    fun pop(): Boolean {
        if (isAtRoot) return false
        backStack.removeLast()
        return true
    }
}
