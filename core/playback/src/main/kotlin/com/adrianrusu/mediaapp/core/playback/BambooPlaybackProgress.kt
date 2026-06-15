package com.adrianrusu.mediaapp.core.playback

import kotlin.math.max
import kotlin.math.min

data class BambooPlaybackProgress(val positionMillis: Long, val durationMillis: Long?, val fraction: Float) {
    val hasKnownDuration: Boolean
        get() = durationMillis != null && durationMillis > 0L
}

object BambooPlaybackProgressProjector {
    fun fromPlaybackState(state: BambooPlaybackState, nowMillis: Long): BambooPlaybackProgress {
        val durationMillis = state.durationMillis?.takeIf { duration -> duration >= 0L }
        val projectedPositionMillis = projectedPositionMillis(
            state = state,
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

    private fun projectedPositionMillis(state: BambooPlaybackState, nowMillis: Long, durationMillis: Long?): Long {
        val anchoredPositionMillis = max(0L, state.positionMillis)
        val elapsedMillis = if (state.isPlaying) {
            max(0L, nowMillis - state.updatedAtEpochMillis)
        } else {
            0L
        }
        val advancedPositionMillis = anchoredPositionMillis + (elapsedMillis * max(0F, state.playbackSpeed)).toLong()

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
