package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.SharedPreferences

internal data class MediaSessionPlaybackResumption(
    val mediaIds: List<String>,
    val startIndex: Int,
    val positionMillis: Long,
)

internal class MediaSessionPlaybackResumptionStore(
    private val preferences: SharedPreferences,
) {
    fun save(mediaIds: List<String>, startIndex: Int, positionMillis: Long = 0L) {
        val normalized = mediaIds.map(String::trim).filter(String::isNotBlank)
        if (normalized.isEmpty()) return
        val index = startIndex.coerceIn(0, normalized.lastIndex)
        preferences.edit()
            .putString(KEY_MEDIA_IDS, normalized.joinToString(separator = "\n"))
            .putInt(KEY_START_INDEX, index)
            .putLong(KEY_POSITION_MILLIS, positionMillis.coerceAtLeast(0L))
            .apply()
    }

    fun savePosition(positionMillis: Long) {
        if (!preferences.contains(KEY_MEDIA_IDS)) return
        preferences.edit()
            .putLong(KEY_POSITION_MILLIS, positionMillis.coerceAtLeast(0L))
            .apply()
    }

    fun load(): MediaSessionPlaybackResumption? {
        val mediaIds = preferences.getString(KEY_MEDIA_IDS, null)
            ?.split('\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (mediaIds.isEmpty()) return null
        return MediaSessionPlaybackResumption(
            mediaIds = mediaIds,
            startIndex = preferences.getInt(KEY_START_INDEX, 0).coerceIn(0, mediaIds.lastIndex),
            positionMillis = preferences.getLong(KEY_POSITION_MILLIS, 0L).coerceAtLeast(0L),
        )
    }

    private companion object {
        const val KEY_MEDIA_IDS = "media_ids"
        const val KEY_START_INDEX = "start_index"
        const val KEY_POSITION_MILLIS = "position_millis"
    }
}
