package com.adrianrusu.mediaapp.core.rust.bridge.aidl

object EngineCommandPayloads {
    private const val DEFAULT_PLAYBACK_SPEED = 1F
    private const val MIN_PLAYBACK_SPEED = 0F
    private const val MIN_POSITION_MILLIS = 0L

    fun seekPositionMillis(positionMillis: Long): String = positionMillis
        .coerceAtLeast(MIN_POSITION_MILLIS)
        .toString()

    fun playbackSpeed(speed: Float): String = speed
        .coerceAtLeast(MIN_PLAYBACK_SPEED)
        .toString()

    fun parseSeekPositionMillis(payload: String?): Long = payload
        ?.toLongOrNull()
        ?.coerceAtLeast(MIN_POSITION_MILLIS)
        ?: MIN_POSITION_MILLIS

    fun parsePlaybackSpeed(payload: String?): Float = payload
        ?.toFloatOrNull()
        ?.coerceAtLeast(MIN_PLAYBACK_SPEED)
        ?: DEFAULT_PLAYBACK_SPEED
}
