package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal class NativeEngineMetadataCache(private val queryMetadata: () -> NativeEngineMetadata) {
    private var cachedKey: NativeEngineMetadataKey? = null
    private var cachedMetadata: NativeEngineMetadata = NativeEngineMetadata.empty()

    fun enrich(snapshot: EngineSnapshot): EngineSnapshot {
        val key = NativeEngineMetadataKey.from(snapshot)
        if (key == null) {
            cachedKey = null
            cachedMetadata = NativeEngineMetadata.empty()
            return snapshot.withMetadata(NativeEngineMetadata.empty())
        }

        if (cachedKey != key) {
            cachedKey = key
            cachedMetadata = queryMetadata()
        }

        return snapshot.withMetadata(cachedMetadata)
    }
}

internal data class NativeEngineMetadata(
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val userId: String?
) {
    companion object {
        fun empty(): NativeEngineMetadata = NativeEngineMetadata(
            mediaId = null,
            title = null,
            artist = null,
            userId = null
        )
    }
}

private data class NativeEngineMetadataKey(val updatedAtEpochMillis: Long) {
    companion object {
        fun from(snapshot: EngineSnapshot): NativeEngineMetadataKey? = if (snapshot.hasActiveSession) {
            NativeEngineMetadataKey(snapshot.updatedAtEpochMillis)
        } else {
            null
        }
    }
}

private fun EngineSnapshot.withMetadata(metadata: NativeEngineMetadata): EngineSnapshot = copy(
    mediaId = metadata.mediaId,
    title = metadata.title,
    artist = metadata.artist,
    userId = metadata.userId
)
