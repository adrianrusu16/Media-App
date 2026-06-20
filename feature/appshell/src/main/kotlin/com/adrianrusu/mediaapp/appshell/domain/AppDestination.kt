package com.adrianrusu.mediaapp.appshell.domain

enum class AppDestination(val label: String, val isPrimary: Boolean) {
    Home("Home", isPrimary = true),
    Library("Library", isPrimary = true),
    Search("Search", isPrimary = true),
    Profile("Profile", isPrimary = true),
    NowPlaying("Now Playing", isPrimary = false),
    ProfileSettings("Settings", isPrimary = false);

    companion object {
        val railDestinations: List<AppDestination> = entries.filter(AppDestination::isPrimary)
    }
}
