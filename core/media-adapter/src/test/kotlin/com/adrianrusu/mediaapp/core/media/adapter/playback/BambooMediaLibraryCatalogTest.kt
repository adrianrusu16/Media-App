package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.model.catalog.BambooCatalogNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BambooMediaLibraryCatalogTest {
    @Test
    fun rootReturnsBrowsablePandaWaveItem() {
        val catalog = BambooMediaLibraryCatalog(source = EmptyCatalogSource)

        val root = catalog.root()

        assertEquals(LibraryItems.ROOT_MEDIA_ID, root.mediaId)
        assertEquals("PandaWave", root.mediaMetadata.title.toString())
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertFalse(root.mediaMetadata.isPlayable == true)
    }

    @Test
    fun rootChildrenAreProjectedFromCatalogSource() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(
                    BambooCatalogNode(
                        mediaId = "catalog.albums",
                        title = "Albums",
                        subtitle = "Saved albums",
                        isBrowsable = true,
                        isPlayable = false
                    ),
                    BambooCatalogNode(
                        mediaId = "catalog.mix",
                        title = "Daily drive",
                        subtitle = "Ready to play",
                        isBrowsable = false,
                        isPlayable = true
                    )
                )
            )
        )

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 0,
            pageSize = 10
        )

        assertEquals(listOf("catalog.albums", "catalog.mix"), children.map { it.mediaId })
        assertEquals("Albums", children[0].mediaMetadata.title.toString())
        assertEquals("Saved albums", children[0].mediaMetadata.subtitle.toString())
        assertTrue(children[0].mediaMetadata.isBrowsable == true)
        assertFalse(children[0].mediaMetadata.isPlayable == true)
        assertFalse(children[1].mediaMetadata.isBrowsable == true)
        assertTrue(children[1].mediaMetadata.isPlayable == true)
    }

    @Test
    fun childrenArePaged() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(
                    node("one"),
                    node("two"),
                    node("three")
                )
            )
        )

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 1,
            pageSize = 2
        )

        assertEquals(listOf("three"), children.map { it.mediaId })
    }

    @Test
    fun invalidPageOrUnknownParentReturnsNoChildren() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(node("one"))
            )
        )

        assertEquals(emptyList<String>(), catalog.children(LibraryItems.ROOT_MEDIA_ID, -1, 10).map { it.mediaId })
        assertEquals(emptyList<String>(), catalog.children(LibraryItems.ROOT_MEDIA_ID, 0, 0).map { it.mediaId })
        assertEquals(emptyList<String>(), catalog.children("unknown", 0, 10).map { it.mediaId })
    }

    @Test
    fun placeholderSourceExposesStableRootCategories() {
        val catalog = BambooMediaLibraryCatalog(source = PlaceholderBambooCatalogSource)

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 0,
            pageSize = 10
        )

        assertEquals(
            listOf(
                "pandawave.library.saved",
                "pandawave.library.downloads",
                "pandawave.library.recent"
            ),
            children.map { it.mediaId }
        )
        assertTrue(children.all { item -> item.mediaMetadata.isBrowsable == true })
        assertTrue(children.none { item -> item.mediaMetadata.isPlayable == true })
    }
}

private object EmptyCatalogSource : BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = emptyList()
}

private class FixedCatalogSource(private val parentId: String, private val children: List<BambooCatalogNode>) :
    BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = when (parentId) {
        this.parentId -> children
        else -> emptyList()
    }
}

private fun node(id: String): BambooCatalogNode = BambooCatalogNode(
    mediaId = id,
    title = id,
    isBrowsable = true,
    isPlayable = false
)
