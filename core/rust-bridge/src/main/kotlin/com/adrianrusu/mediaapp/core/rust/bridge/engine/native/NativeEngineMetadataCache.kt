package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal class NativeEngineMetadataCache(private val queryMetadata: () -> NativeEngineMetadata) {
    private var cachedKey: NativeEngineMetadataKey? = null
    private var cachedMetadata: NativeEngineMetadata = NativeEngineMetadata.empty()

    fun enrich(projection: NativeEngineSnapshotProjection): EngineSnapshot {
        val key = NativeEngineMetadataKey.from(projection)
        if (key == null) {
            cachedKey = null
            cachedMetadata = NativeEngineMetadata.empty()
            return projection.snapshot.withMetadata(NativeEngineMetadata.empty())
        }

        if (cachedKey != key) {
            cachedKey = key
            cachedMetadata = queryMetadata()
        }

        return projection.snapshot.withMetadata(cachedMetadata)
    }
}

internal data class NativeEngineSnapshotProjection(val snapshot: EngineSnapshot, val metadataRevision: Long)

internal data class NativeEngineMetadata(
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMillis: Long?,
    val artworkUri: String?,
    val userId: String?
) {
    companion object {
        fun empty(): NativeEngineMetadata = NativeEngineMetadata(
            mediaId = null,
            title = null,
            artist = null,
            album = null,
            durationMillis = null,
            artworkUri = null,
            userId = null
        )
    }
}

private data class NativeEngineMetadataKey(val metadataRevision: Long) {
    companion object {
        fun from(projection: NativeEngineSnapshotProjection): NativeEngineMetadataKey? = if (
            projection.snapshot.hasActiveSession
        ) {
            NativeEngineMetadataKey(projection.metadataRevision)
        } else {
            null
        }
    }
}

private fun EngineSnapshot.withMetadata(metadata: NativeEngineMetadata): EngineSnapshot = copy(
    mediaId = metadata.mediaId,
    title = metadata.title,
    artist = metadata.artist,
    album = metadata.album,
    durationMillis = metadata.durationMillis,
    artworkUri = metadata.artworkUri,
    userId = metadata.userId
)
