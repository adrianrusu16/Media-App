package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage

internal object PandaEngineNativeHistoryItemMapper {
    fun toDomain(values: Array<String>?): EngineHistoryItem? {
        if (values == null || values.size != VALUE_COUNT) return null
        return itemAt(values, 0)
    }

    fun toPage(values: Array<String>?, requestedGeneration: Long): EngineHistoryPage {
        if (values.isNullOrEmpty()) {
            return EngineHistoryPage(requestedGeneration, emptyList())
        }
        val generation = values[0].toLongOrNull() ?: return EngineHistoryPage(requestedGeneration, emptyList())
        val payloadSize = values.size - HEADER_COUNT
        if (payloadSize == 0) {
            return EngineHistoryPage(generation, emptyList())
        }
        if (payloadSize % VALUE_COUNT != 0) {
            return EngineHistoryPage(generation, emptyList())
        }
        val items = (0 until payloadSize / VALUE_COUNT).mapNotNull { index ->
            itemAt(values, HEADER_COUNT + index * VALUE_COUNT)
        }
        return EngineHistoryPage(generation, items)
    }

    private fun itemAt(values: Array<String>, offset: Int): EngineHistoryItem? {
        if (values.size < offset + VALUE_COUNT) return null
        return runCatching {
            EngineHistoryItem(
                historyId = values[offset + HISTORY_ID_INDEX],
                mediaId = values[offset + MEDIA_ID_INDEX].ifEmpty { null },
                title = values[offset + TITLE_INDEX],
                artist = values[offset + ARTIST_INDEX].ifEmpty { null },
                album = values[offset + ALBUM_INDEX].ifEmpty { null },
                artworkUri = values[offset + ARTWORK_INDEX].ifEmpty { null },
                playedAtEpochMillis = values[offset + PLAYED_AT_INDEX].takeIf(String::isNotEmpty)?.toLong(),
                listenedDurationMillis = values[offset + DURATION_INDEX].toLong(),
                completionRatio = values[offset + COMPLETION_INDEX].toFloat(),
                playable = when (values[offset + PLAYABLE_INDEX]) {
                    "1" -> true
                    "0" -> false
                    else -> error("invalid playable flag")
                },
            )
        }.getOrNull()
    }

    private const val HEADER_COUNT = 1
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
