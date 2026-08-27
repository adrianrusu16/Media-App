package com.adrianrusu.pandawave.core.ui.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BambooArtworkModelTest {
    @Test
    fun `cacheKey joins id and version`() {
        val artwork = BambooArtworkModel(
            id = "art-1",
            version = "v3",
            uri = "content://pandawave/art/1"
        )

        assertEquals("art-1:v3", artwork.cacheKey())
    }

    @Test
    fun `toBambooArtworkModel requires all three non-blank fields`() {
        assertEquals(
            BambooArtworkModel(id = "id", version = "1", uri = "content://art"),
            toBambooArtworkModel(id = "id", version = "1", uri = "content://art")
        )
        assertNull(toBambooArtworkModel(id = null, version = "1", uri = "content://art"))
        assertNull(toBambooArtworkModel(id = "id", version = null, uri = "content://art"))
        assertNull(toBambooArtworkModel(id = "id", version = "1", uri = null))
        assertNull(toBambooArtworkModel(id = " ", version = "1", uri = "content://art"))
        assertNull(toBambooArtworkModel(id = "id", version = "", uri = "content://art"))
        assertNull(toBambooArtworkModel(id = "id", version = "1", uri = "  "))
        assertNull(Triple(null as String?, null, null).toBambooArtworkModel())
    }
}
