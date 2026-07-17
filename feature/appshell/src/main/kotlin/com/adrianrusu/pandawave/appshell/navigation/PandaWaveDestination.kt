package com.adrianrusu.pandawave.appshell.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface PandaWaveDestination : NavKey

@Serializable
data object HomeDestination : PandaWaveDestination

@Serializable
data object LibraryDestination : PandaWaveDestination

@Serializable
data object SearchDestination : PandaWaveDestination

@Serializable
data object ProfileDestination : PandaWaveDestination

@Serializable
data object PreferencesDestination : PandaWaveDestination

@Serializable
data object LoginDestination : PandaWaveDestination

@Serializable
data object RegisterDestination : PandaWaveDestination

@Serializable
data object NowPlayingDestination : PandaWaveDestination

val primaryDestinations: List<PandaWaveDestination> = listOf(
    HomeDestination,
    LibraryDestination,
    SearchDestination,
    ProfileDestination
)

val PandaWaveDestination.navigationId: String
    get() = when (this) {
        HomeDestination -> "home"
        LibraryDestination -> "library"
        SearchDestination -> "search"
        ProfileDestination -> "profile"
        PreferencesDestination -> "preferences"
        LoginDestination -> "login"
        RegisterDestination -> "register"
        NowPlayingDestination -> "now-playing"
    }

val PandaWaveDestination.selectedRailDestination: PandaWaveDestination?
    get() = when (this) {
        PreferencesDestination, LoginDestination, RegisterDestination -> ProfileDestination
        NowPlayingDestination -> null
        else -> this
    }

val PandaWaveDestination.shouldShowMiniPlayer: Boolean
    get() = this != NowPlayingDestination
