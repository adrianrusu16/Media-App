package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem

internal object PandaEngineNativeHistoryItemMapper {
    fun toDomain(values: Array<String>?): EngineHistoryItem? {
        if (values == null || values.size != VALUE_COUNT) return null
        return runCatching {
            EngineHistoryItem(
                historyId = values[HISTORY_ID_INDEX],
                mediaId = values[MEDIA_ID_INDEX].ifEmpty { null },
                title = values[TITLE_INDEX],
                artist = values[ARTIST_INDEX].ifEmpty { null },
                album = values[ALBUM_INDEX].ifEmpty { null },
                artworkUri = values[ARTWORK_INDEX].ifEmpty { null },
                playedAtEpochMillis = values[PLAYED_AT_INDEX].takeIf(String::isNotEmpty)?.toLong(),
                listenedDurationMillis = values[DURATION_INDEX].toLong(),
                completionRatio = values[COMPLETION_INDEX].toFloat(),
                playable = when (values[PLAYABLE_INDEX]) {
                    "1" -> true
                    "0" -> false
                    else -> error("invalid playable flag")
                },
            )
        }.getOrNull()
    }

    private const val HISTORY_ID_INDEX = 0
    private const val MEDIA_ID_INDEX = 1
    private const val TITLE_INDEX = 2
    private const val ARTIST_INDEX = 3
    private const val ALBUM_INDEX = 4
    private const val ARTWORK_INDEX = 5
    private const val PLAYED_AT_INDEX = 6
    private const val DURATION_INDEX = 7
    private const val COMPLETION_INDEX = 8
    private const val PLAYABLE_INDEX = 9
    private const val VALUE_COUNT = 10
}
