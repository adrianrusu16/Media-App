package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem

internal object PandaEngineNativeLibraryItemMapper {
    fun toDomain(values: Array<String>?): EngineLibraryItem? {
        if (values == null || values.size != 10) return null
        return runCatching {
            EngineLibraryItem(
                relationshipId = values[0], mediaId = values[1], title = values[2],
                artistId = values[3], artist = values[4], album = values[5].ifEmpty { null },
                durationMillis = values[6].toLong(), explicit = values[7] == "1",
                artworkId = values[8].ifEmpty { null }, relationshipAtEpochMillis = values[9].toLong()
            )
        }.getOrNull()
    }
}
