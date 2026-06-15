package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeEngineMetadataCacheTest {
    @Test
    fun `metadata is queried once while native snapshot key is unchanged`() {
        var queryCount = 0
        val cache = NativeEngineMetadataCache {
            queryCount += 1
            metadata(title = "Track $queryCount")
        }

        val firstSnapshot = cache.enrich(activeSnapshot(updatedAtEpochMillis = 42L))
        val secondSnapshot = cache.enrich(activeSnapshot(updatedAtEpochMillis = 42L))

        assertEquals(1, queryCount)
        assertEquals("Track 1", firstSnapshot.title)
        assertEquals("Track 1", secondSnapshot.title)
    }

    @Test
    fun `metadata is queried again when native snapshot key changes`() {
        var queryCount = 0
        val cache = NativeEngineMetadataCache {
            queryCount += 1
            metadata(title = "Track $queryCount")
        }

        val firstSnapshot = cache.enrich(activeSnapshot(updatedAtEpochMillis = 42L))
        val secondSnapshot = cache.enrich(activeSnapshot(updatedAtEpochMillis = 43L))

        assertEquals(2, queryCount)
        assertEquals("Track 1", firstSnapshot.title)
        assertEquals("Track 2", secondSnapshot.title)
    }

    @Test
    fun `metadata is cleared while native snapshot has no active session`() {
        var queryCount = 0
        val cache = NativeEngineMetadataCache {
            queryCount += 1
            metadata(title = "Track $queryCount")
        }

        cache.enrich(activeSnapshot(updatedAtEpochMillis = 42L))
        val idleSnapshot = cache.enrich(EngineSnapshot.idle(nowMillis = 42L))
        val resumedSnapshot = cache.enrich(activeSnapshot(updatedAtEpochMillis = 42L))

        assertEquals(2, queryCount)
        assertNull(idleSnapshot.mediaId)
        assertNull(idleSnapshot.title)
        assertNull(idleSnapshot.artist)
        assertNull(idleSnapshot.userId)
        assertEquals("Track 2", resumedSnapshot.title)
    }

    private fun activeSnapshot(updatedAtEpochMillis: Long): EngineSnapshot =
        EngineSnapshot.idle(nowMillis = updatedAtEpochMillis)
            .copy(hasActiveSession = true)

    private fun metadata(title: String): NativeEngineMetadata = NativeEngineMetadata(
        mediaId = "media-id",
        title = title,
        artist = "PandaWave",
        userId = "user-id"
    )
}
