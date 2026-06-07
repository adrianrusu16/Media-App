package com.adrianrusu.mediaapp.feature.nowplaying.domain

data class NowPlayingRestrictionState(val label: String, val isRestricted: Boolean) {
    companion object {
        val Unavailable = NowPlayingRestrictionState(
            label = "Safety status unavailable",
            isRestricted = false
        )
    }
}
