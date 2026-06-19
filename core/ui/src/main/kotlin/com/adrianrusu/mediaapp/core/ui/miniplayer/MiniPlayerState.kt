package com.adrianrusu.mediaapp.core.ui.miniplayer

import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlin.math.max
import kotlin.math.min

data class MiniPlayerState(
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val isRestricted: Boolean,
    val progressAnchor: MiniPlayerProgressAnchor = MiniPlayerProgressAnchor()
) {
    fun progressAt(nowMillis: Long): MiniPlayerProgress = MiniPlayerProgressProjector.fromAnchor(
        anchor = progressAnchor,
        nowMillis = nowMillis
    )

    companion object {
        val Empty = MiniPlayerState(
            title = BambooPlaybackText.FALLBACK_IDLE_TITLE,
            subtitle = BambooPlaybackText.FALLBACK_IDLE_SUBTITLE,
            isPlaying = false,
            isRestricted = false
        )
    }
}

data class MiniPlayerProgressAnchor(
    val positionMillis: Long = 0L,
    val durationMillis: Long? = null,
    val updatedAtEpochMillis: Long = 0L,
    val playbackSpeed: Float = 1F,
    val isPlaying: Boolean = false
)

data class MiniPlayerProgress(val fraction: Float, val positionMillis: Long, val durationMillis: Long?)

internal object MiniPlayerProgressProjector {
    fun fromAnchor(anchor: MiniPlayerProgressAnchor, nowMillis: Long): MiniPlayerProgress {
        val durationMillis = anchor.durationMillis?.takeIf { duration -> duration > 0L }

        if (durationMillis == null) {
            return MiniPlayerProgress(
                fraction = 0F,
                positionMillis = max(0L, anchor.positionMillis),
                durationMillis = null
            )
        }

        val projectedPositionMillis = projectedPositionMillis(
            anchor = anchor,
            nowMillis = nowMillis,
            durationMillis = durationMillis
        )

        return MiniPlayerProgress(
            fraction = (projectedPositionMillis.toDouble() / durationMillis.toDouble()).toFloat().coerceIn(0F, 1F),
            positionMillis = projectedPositionMillis,
            durationMillis = durationMillis
        )
    }

    private fun projectedPositionMillis(anchor: MiniPlayerProgressAnchor, nowMillis: Long, durationMillis: Long): Long {
        val elapsedMillis = if (anchor.isPlaying) {
            max(0L, nowMillis - anchor.updatedAtEpochMillis)
        } else {
            0L
        }
        val advancedPositionMillis =
            max(0L, anchor.positionMillis) + (elapsedMillis * max(0F, anchor.playbackSpeed)).toLong()

        return min(advancedPositionMillis, durationMillis)
    }
}
