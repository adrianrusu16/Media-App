package com.adrianrusu.mediaapp.core.ui.miniplayer

import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

data class MiniPlayerState(val title: String, val subtitle: String, val isPlaying: Boolean, val isRestricted: Boolean) {
    companion object {
        val Empty = MiniPlayerState(
            title = BambooPlaybackText.FALLBACK_IDLE_TITLE,
            subtitle = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
            isPlaying = false,
            isRestricted = false
        )
    }
}
