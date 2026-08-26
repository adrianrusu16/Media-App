package com.adrianrusu.pandawave.appshell.navigation

import androidx.navigation3.runtime.NavKey
import com.adrianrusu.pandawave.core.common.log.PandaLog

class PandaWaveNavigator(private val backStack: MutableList<NavKey>) {
    val currentDestination: PandaWaveDestination
        get() = backStack.last() as PandaWaveDestination

    val isAtRoot: Boolean
        get() = backStack.size == 1 && backStack.single() == HomeDestination

    fun selectPrimary(destination: PandaWaveDestination) {
        require(destination in primaryDestinations)
        PandaLog.v(PandaLog.Tag.APP_SHELL) {
            "click action=navigate destination=${destination::class.java.simpleName}"
        }
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

    fun openLogin() {
        openProfileChild(LoginDestination)
    }

    fun openRegister() {
        openProfileChild(RegisterDestination)
    }

    private fun openProfileChild(destination: PandaWaveDestination) {
        backStack.clear()
        backStack += listOf(HomeDestination, ProfileDestination, destination)
    }

    fun openNowPlaying() {
        PandaLog.v(PandaLog.Tag.NPS) { "click action=open_now_playing" }
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
