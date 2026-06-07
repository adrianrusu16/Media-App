package com.adrianrusu.mediaapp.core.ui.miniplayer

import com.adrianrusu.mediaapp.core.ui.playback.PlaybackDisplayText

data class MiniPlayerState(val title: String, val subtitle: String, val isPlaying: Boolean, val isRestricted: Boolean) {
    companion object {
        val Empty = MiniPlayerState(
            title = PlaybackDisplayText.FALLBACK_IDLE_TITLE,
            subtitle = PlaybackDisplayText.FALLBACK_IDLE_SUBTITLE,
            isPlaying = false,
            isRestricted = false
        )
    }
}
