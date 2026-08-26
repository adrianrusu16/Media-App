package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.ui.playback.MAX_PROGRESS_INTERPOLATION_GAP_MILLIS
import kotlin.math.max
import kotlin.math.min

data class BambooPlaybackProgress(val positionMillis: Long, val durationMillis: Long?, val fraction: Float) {
    val hasKnownDuration: Boolean
        get() = durationMillis != null && durationMillis > 0L
}

data class BambooPlaybackProgressAnchor(
    val positionMillis: Long = 0L,
    val durationMillis: Long? = null,
    val updatedAtEpochMillis: Long = 0L,
    val playbackSpeed: Float = 1F,
    val isPlaying: Boolean = false
) {
    companion object {
        fun fromPlaybackState(state: BambooPlaybackState): BambooPlaybackProgressAnchor = BambooPlaybackProgressAnchor(
            positionMillis = state.positionMillis,
            durationMillis = state.durationMillis,
            updatedAtEpochMillis = state.updatedAtEpochMillis,
            playbackSpeed = state.playbackSpeed,
            isPlaying = state.isPlaying
        )
    }
}

object BambooPlaybackProgressProjector {
    fun fromPlaybackState(state: BambooPlaybackState, nowMillis: Long): BambooPlaybackProgress = fromAnchor(
        anchor = BambooPlaybackProgressAnchor.fromPlaybackState(state),
        nowMillis = nowMillis
    )

    fun fromAnchor(anchor: BambooPlaybackProgressAnchor, nowMillis: Long): BambooPlaybackProgress {
        val durationMillis = anchor.durationMillis?.takeIf { duration -> duration >= 0L }
        val projectedPositionMillis = projectedPositionMillis(
            anchor = anchor,
            nowMillis = nowMillis,
            durationMillis = durationMillis
        )

        return BambooPlaybackProgress(
            positionMillis = projectedPositionMillis,
            durationMillis = durationMillis,
            fraction = progressFraction(
                positionMillis = projectedPositionMillis,
                durationMillis = durationMillis
            )
        )
    }

    private fun projectedPositionMillis(
        anchor: BambooPlaybackProgressAnchor,
        nowMillis: Long,
        durationMillis: Long?
    ): Long {
        val anchoredPositionMillis = max(0L, anchor.positionMillis)
        val elapsedMillis = if (anchor.isPlaying) {
            min(
                MAX_PROGRESS_INTERPOLATION_GAP_MILLIS,
                max(0L, nowMillis - anchor.updatedAtEpochMillis)
            )
        } else {
            0L
        }
        val advancedPositionMillis = anchoredPositionMillis + (elapsedMillis * max(0F, anchor.playbackSpeed)).toLong()

        return if (durationMillis != null && durationMillis > 0L) {
            min(advancedPositionMillis, durationMillis)
        } else {
            advancedPositionMillis
        }
    }

    private fun progressFraction(positionMillis: Long, durationMillis: Long?): Float {
        if (durationMillis == null || durationMillis <= 0L) {
            return 0F
        }

        return (positionMillis.toDouble() / durationMillis.toDouble()).toFloat().coerceIn(0F, 1F)
    }
}
