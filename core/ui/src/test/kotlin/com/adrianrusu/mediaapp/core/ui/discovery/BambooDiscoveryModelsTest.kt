package com.adrianrusu.mediaapp.core.ui.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooDiscoveryModelsTest {
    @Test
    fun `selected filter marks only matching chip selected`() {
        val filters = BambooFilterOption.items(
            selectedId = "albums",
            labels = listOf("playlists" to "Playlists", "albums" to "Albums")
        )

        assertFalse(filters[0].selected)
        assertTrue(filters[1].selected)
    }

    @Test
    fun `media action can be unavailable without pretending to work`() {
        val item = BambooMediaItem(
            id = "zen",
            title = "Zen Forest",
            subtitle = "Nature sounds",
            description = "Ambient layers for calm drives",
            action = BambooMediaAction.Unavailable
        )

        assertEquals("Zen Forest", item.title)
        assertEquals(BambooMediaAction.Unavailable, item.action)
    }
}
