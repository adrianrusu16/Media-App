package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PandaEngineNativePlaylistMapperTest {
    @Test
    fun presentEmptyDescriptionRemainsDistinctFromAbsent() {
        val present = PandaEngine.playlistItem(arrayOf("p1", "Mix", "", "7", "100", "200", "1"))
        val absent = PandaEngine.playlistItem(arrayOf("p1", "Mix", "", "7", "100", "200", "0"))

        assertEquals("", present?.description)
        assertNull(absent?.description)

        val playlists = PandaEngine.playlistItems(
            arrayOf(
                "p1", "Mix", "", "7", "100", "200", "1",
                "p2", "Chill", "desc", "8", "110", "210", "0"
            )
        )
        assertEquals(listOf("p1", "p2"), playlists.map { it.id })
        assertEquals("", playlists[0].description)
        assertNull(playlists[1].description)
    }

    @Test
    fun unsignedValuesOutsideKotlinRangesAreRejectedWithoutThrowing() {
        assertNull(PandaEngine.playlistItem(arrayOf("p1", "Mix", "", "18446744073709551615", "100", "200", "0")))
        assertNull(
            PandaEngine.playlistTrackItem(
                arrayOf(
                    "m1", "p1", "t1", "Title", "a1", "Artist", "", "100", "0", "",
                    "4294967295", "200", "", ""
                )
            )
        )
        assertNull(
            PandaEngine.playlistReconciliationItem(
                arrayOf("p1", "p1", "18446744073709551615", "8", "m1", "m1")
            )
        )
    }
}
