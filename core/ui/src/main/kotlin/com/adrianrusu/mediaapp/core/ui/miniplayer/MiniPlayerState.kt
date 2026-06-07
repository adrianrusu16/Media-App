package com.adrianrusu.mediaapp.core.ui.miniplayer

data class MiniPlayerState(val title: String, val subtitle: String, val isPlaying: Boolean, val isRestricted: Boolean) {
    companion object {
        val Empty = MiniPlayerState(
            title = "Nothing playing",
            subtitle = "Ready when you are",
            isPlaying = false,
            isRestricted = false
        )
    }
}
