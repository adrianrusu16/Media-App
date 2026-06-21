package com.adrianrusu.mediaapp.appshell.domain

enum class AppDestination(val isPrimary: Boolean) {
    Home(isPrimary = true),
    Library(isPrimary = true),
    Search(isPrimary = true),
    Profile(isPrimary = true),
    NowPlaying(isPrimary = false),
    ProfileSettings(isPrimary = false);

    companion object {
        val railDestinations: List<AppDestination> = entries.filter(AppDestination::isPrimary)
    }
}
