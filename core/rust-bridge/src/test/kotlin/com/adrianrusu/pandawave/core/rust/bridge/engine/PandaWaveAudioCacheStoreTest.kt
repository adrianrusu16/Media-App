package com.adrianrusu.pandawave.core.rust.bridge.engine

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaWaveAudioCacheStoreTest {
    @Test
    fun `put writes completed audio files into cache`() {
        val cacheDirectory = Files.createTempDirectory("pandawave-audio-cache").toFile()
        try {
            val store = PandaWaveAudioCacheStore(audioCacheDirectory = cacheDirectory)

            val file = store.put(
                trackId = " track-42 ",
                source = "panda audio bytes".byteInputStream()
            )

            assertTrue(file.isFile)
            assertEquals("panda audio bytes", file.readText())
            assertTrue(store.isCached("track-42"))
            assertFalse(cacheDirectory.listFiles().orEmpty().any { value -> value.extension == "tmp" })
        } finally {
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun `put replaces previous cached file atomically`() {
        val cacheDirectory = Files.createTempDirectory("pandawave-audio-cache").toFile()
        try {
            val store = PandaWaveAudioCacheStore(audioCacheDirectory = cacheDirectory)

            val first = store.put("track-42", "first".byteInputStream())
            val second = store.put("track-42", "second".byteInputStream())

            assertEquals(first, second)
            assertEquals("second", second.readText())
            assertEquals(1, cacheDirectory.listFiles().orEmpty().count { value -> value.extension == "audio" })
        } finally {
            cacheDirectory.deleteRecursively()
        }
    }
}
