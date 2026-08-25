package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem

internal object PandaEngineNativeCatalogItemMapper {
    fun toDomain(values: Array<String>?): EngineCatalogItem? {
        if (values == null || values.size != VALUE_COUNT) return null
        val itemType = values[ITEM_TYPE_INDEX].toIntOrNull() ?: return null
        return when {
            values[ID_INDEX].isBlank() || values[TITLE_INDEX].isBlank() -> null

            else -> runCatching {
                EngineCatalogItem(
                    mediaId = values[ID_INDEX],
                    title = values[TITLE_INDEX],
                    artist = values[ARTIST_INDEX].ifBlank { null },
                    album = values[ALBUM_INDEX].ifBlank { null },
                    artworkUri = values[ARTWORK_INDEX].ifBlank { null },
                    sourceUri = values[SOURCE_INDEX].ifBlank { null },
                    mimeType = values[MIME_INDEX].ifBlank { null },
                    itemType = itemType,
                )
            }.getOrNull()
        }
    }

    private const val VALUE_COUNT = 8
    private const val ID_INDEX = 0
    private const val TITLE_INDEX = 1
    private const val ARTIST_INDEX = 2
    private const val ALBUM_INDEX = 3
    private const val ARTWORK_INDEX = 4
    private const val SOURCE_INDEX = 5
    private const val MIME_INDEX = 6
    private const val ITEM_TYPE_INDEX = 7
}
