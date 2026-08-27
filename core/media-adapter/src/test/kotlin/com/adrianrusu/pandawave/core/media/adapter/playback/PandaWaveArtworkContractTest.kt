package com.adrianrusu.pandawave.core.media.adapter.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PandaWaveArtworkContractTest {
    @Test
    fun `http artwork is rewritten to the exported content provider`() {
        val rewritten = PandaWaveArtworkContract.mediaHostUriString(
            packageName = "com.adrianrusu.pandawave",
            artworkUri = "https://cdn.pandawave.test/artwork/art-1/hash-1"
        )

        assertEquals("content", rewritten?.substringBefore("://"))
        assertTrue(rewritten!!.startsWith("content://com.adrianrusu.pandawave.artwork/remote?"))
        assertEquals(
            "https://cdn.pandawave.test/artwork/art-1/hash-1",
            PandaWaveArtworkContract.remoteSourceString(rewritten)
        )
    }

    @Test
    fun `content artwork uris are left in place for the media host`() {
        assertEquals(
            "content://pandawave/art/track-1",
            PandaWaveArtworkContract.mediaHostUriString(
                packageName = "com.adrianrusu.pandawave",
                artworkUri = "content://pandawave/art/track-1"
            )
        )
    }

    @Test
    fun `blank and credentialed remote uris are rejected`() {
        assertNull(PandaWaveArtworkContract.mediaHostUriString("com.adrianrusu.pandawave", " "))
        assertNull(
            PandaWaveArtworkContract.mediaHostUriString(
                packageName = "com.adrianrusu.pandawave",
                artworkUri = "https://user:secret@cdn.pandawave.test/artwork/art-1"
            )
        )
        assertFalse(
            PandaWaveArtworkContract.isAllowedRemoteSource(
                "file:///data/local/artwork.png".toJavaUriOrNull()!!
            )
        )
        assertTrue(
            PandaWaveArtworkContract.isAllowedRemoteSource(
                "http://10.0.2.2:8080/artwork/a/b".toJavaUriOrNull()!!
            )
        )
    }

    @Test
    fun `media host projector rewrites http artwork before android uri parse`() {
        val parsed = mutableListOf<String>()
        val projector = MediaHostArtworkUriProjector("com.adrianrusu.pandawave") { value ->
            parsed += value
            null
        }

        projector.project("https://cdn.pandawave.test/artwork/art-1/hash-1")

        assertEquals(1, parsed.size)
        assertEquals(
            "https://cdn.pandawave.test/artwork/art-1/hash-1",
            PandaWaveArtworkContract.remoteSourceString(parsed.single())
        )
    }
}
