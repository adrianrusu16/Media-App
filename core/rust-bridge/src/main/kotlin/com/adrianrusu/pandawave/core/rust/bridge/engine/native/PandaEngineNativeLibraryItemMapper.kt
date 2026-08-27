package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem

internal object PandaEngineNativeLibraryItemMapper {
    fun toDomain(values: Array<String>?): EngineLibraryItem? {
        if (values == null || values.size != VALUE_COUNT) return null
        return itemAt(values, 0)
    }

    fun toPage(values: Array<String>?): List<EngineLibraryItem> =
        PandaEngineNativePackedPage.toItems(values, VALUE_COUNT, ::itemAt)

    private fun itemAt(values: Array<String>, offset: Int): EngineLibraryItem? {
        if (values.size < offset + VALUE_COUNT) return null
        return runCatching {
            EngineLibraryItem(
                relationshipId = values[offset],
                mediaId = values[offset + 1],
                title = values[offset + 2],
                artistId = values[offset + 3],
                artist = values[offset + 4],
                album = values[offset + 5].ifEmpty { null },
                durationMillis = values[offset + 6].toLong(),
                explicit = values[offset + 7] == "1",
                artworkUri = values[offset + 8].ifEmpty { null },
                relationshipAtEpochMillis = values[offset + 9].toLong(),
                artworkId = values[offset + 10].ifEmpty { null },
                artworkVersion = values[offset + 11].ifEmpty { null }
            )
        }.getOrNull()
    }

    private const val VALUE_COUNT = 12
}
