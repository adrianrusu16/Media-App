package com.adrianrusu.mediaapp.core.rust.bridge.aidl

object EngineCommandPayloads {
    const val DEFAULT_BROWSE_PARENT_ID = "root"

    private const val DEFAULT_PLAYBACK_SPEED = 1F
    private const val MIN_PLAYBACK_SPEED = 0F
    private const val MIN_POSITION_MILLIS = 0L

    fun seekPositionMillis(positionMillis: Long): String = positionMillis
        .coerceAtLeast(MIN_POSITION_MILLIS)
        .toString()

    fun playbackSpeed(speed: Float): String = speed
        .coerceAtLeast(MIN_PLAYBACK_SPEED)
        .toString()

    fun searchQuery(query: String): String = query

    fun browseParentId(parentId: String): String = parentId.ifBlank { DEFAULT_BROWSE_PARENT_ID }

    fun parseSeekPositionMillis(payload: String?): Long = payload
        ?.toLongOrNull()
        ?.coerceAtLeast(MIN_POSITION_MILLIS)
        ?: MIN_POSITION_MILLIS

    fun parsePlaybackSpeed(payload: String?): Float = payload
        ?.toFloatOrNull()
        ?.coerceAtLeast(MIN_PLAYBACK_SPEED)
        ?: DEFAULT_PLAYBACK_SPEED

    fun parseSearchQuery(payload: String?): String = payload.orEmpty()

    fun parseBrowseParentId(payload: String?): String = payload
        ?.takeIf { parentId -> parentId.isNotBlank() }
        ?: DEFAULT_BROWSE_PARENT_ID
}
