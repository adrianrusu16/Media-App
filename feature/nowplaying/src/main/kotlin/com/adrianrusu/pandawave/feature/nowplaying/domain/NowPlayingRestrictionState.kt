package com.adrianrusu.pandawave.feature.nowplaying.domain

data class NowPlayingRestrictionState(val isRestricted: Boolean) {
    companion object {
        val Unavailable = NowPlayingRestrictionState(
            isRestricted = false
        )
    }
}
