package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PandaEngineFactoryTest {
    @Test
    fun `create fake returns test engine`() {
        val engine = PandaEngineFactory.createFake()

        assertNotNull(engine.snapshot())
    }

    @Test
    fun `fake engine applies seek and speed payloads`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })

        val seekResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_SEEK,
                payload = EngineCommandPayloads.seekPositionMillis(12_345L)
            )
        )
        val speedResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_SET_SPEED,
                payload = EngineCommandPayloads.playbackSpeed(1.25F)
            )
        )

        assertEquals(12_345L, seekResult.snapshot.positionMillis)
        assertEquals(1.25F, speedResult.snapshot.playbackSpeed)
        assertEquals(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_SEEK,
                    positionMillis = 12_345L
                )
            ),
            seekResult.effects
        )
        assertEquals(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_SET_SPEED,
                    speed = 1.25F
                )
            ),
            speedResult.effects
        )
    }

    @Test
    fun `fake engine exposes command effects`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })

        val result = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            ),
            result.effects
        )
        assertEquals(2, engine.effectCount())
        assertEquals(EngineEffect(type = EngineEffect.TYPE_PLAY), engine.effect(index = 1))
    }

    @Test
    fun `fake engine applies browse and search payloads`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })

        val browseResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_BROWSE,
                payload = EngineCommandPayloads.browseParentId("root")
            )
        )
        val searchResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_SEARCH,
                payload = EngineCommandPayloads.searchQuery("Rust")
            )
        )

        assertEquals(1, browseResult.snapshot.browseResultsCount)
        assertEquals(1, searchResult.snapshot.searchResultsCount)
        val browse = engine.browseResultsPage(0, 10).single()
        assertEquals("browse-0", browse.mediaId)
        assertEquals("Browse result 0", browse.title)
        assertEquals("content://com.adrianrusu.pandawave.audio/audio/browse-0", browse.sourceUri)
        assertEquals("audio/mpeg", browse.mimeType)
        assertEquals(EngineCatalogItem.TYPE_ALBUM, browse.itemType)
        val search = engine.searchResultsPage(0, 10).single()
        assertEquals("search-0", search.mediaId)
        assertEquals("Search result 0", search.title)
        assertEquals("Canopy Sessions", search.album)
        assertEquals("content://com.adrianrusu.pandawave.audio/audio/search-0", search.sourceUri)
        assertEquals("audio/mpeg", search.mimeType)
        assertEquals(EngineCatalogItem.TYPE_TRACK, search.itemType)
        assertEquals(emptyList(), engine.browseResultsPage(1, 1))
        assertEquals(emptyList(), engine.searchResultsPage(1, 1))
    }

    @Test
    fun `fake engine applies play media payload`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })

        val result = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY_MEDIA_BY_ID,
                payload = EngineCommandPayloads.mediaId("track-42")
            )
        )

        assertEquals(EngineSnapshot.PLAYBACK_BUFFERING, result.snapshot.playbackState)
        assertEquals("track-42", result.snapshot.mediaId)
        assertEquals("track-42", result.snapshot.title)
        assertEquals("content://com.adrianrusu.pandawave.audio/audio/track-42", result.snapshot.sourceUri)
        assertEquals("audio/mpeg", result.snapshot.mimeType)
        assertEquals(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE,
                    mediaId = "track-42"
                ),
                EngineEffect(
                    type = EngineEffect.TYPE_UPDATE_METADATA,
                    mediaId = "track-42"
                ),
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            ),
            result.effects
        )
    }

    @Test
    fun `engine playback source rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            EnginePlaybackSource(
                sourceId = "source-1",
                uri = "",
                expectedDurationMillis = 1L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EnginePlaybackSource(
                sourceId = "source-1",
                uri = "content://com.adrianrusu.pandawave.audio/audio/track-42",
                expectedDurationMillis = -1L
            )
        }
    }

    @Test
    fun `fake engine can resolve play media source through configured resolver`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })
        engine.setAudioSourceResolver { trackId ->
            EnginePlaybackSource(
                sourceId = "source-$trackId",
                uri = "content://resolver/audio/$trackId",
                mimeType = "audio/flac",
                expectedDurationMillis = 123_000L
            )
        }

        val result = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY_MEDIA_BY_ID,
                payload = EngineCommandPayloads.mediaId("track-42")
            )
        )

        assertEquals("content://resolver/audio/track-42", result.snapshot.sourceUri)
        assertEquals("audio/flac", result.snapshot.mimeType)
        assertEquals(123_000L, result.snapshot.durationMillis)
    }

    @Test
    fun `panda wave content resolver maps track ids to stable content sources`() {
        val source = PandaWaveContentAudioSourceResolver().resolve(" track 42/side A ")

        assertEquals("pandawave:track 42/side A", source.sourceId)
        assertEquals("content://com.adrianrusu.pandawave.audio/audio/track%2042%2Fside%20A", source.uri)
        assertEquals("audio/mpeg", source.mimeType)
        assertEquals(null, source.expectedDurationMillis)
        assertEquals("track 42/side A", PandaWaveAudioSourceContract.trackIdFromSourceUri(source.uri))
    }

    @Test
    fun `panda wave content resolver rejects blank track ids`() {
        assertFailsWith<IllegalArgumentException> {
            PandaWaveContentAudioSourceResolver().resolve(" ")
        }
    }

    @Test
    fun `panda wave source contract rejects unknown source uris`() {
        assertEquals(null, PandaWaveAudioSourceContract.trackIdFromSourceUri("https://example.com/audio/track-42"))
        assertEquals(null, PandaWaveAudioSourceContract.trackIdFromSourceUri("content://pandawave/audio/track-42"))
        assertEquals(
            null,
            PandaWaveAudioSourceContract.trackIdFromSourceUri("content://com.adrianrusu.pandawave.audio")
        )
    }

    @Test
    fun `panda wave cache keys are stable and filesystem safe`() {
        val first = PandaWaveAudioCacheKey.fileNameForTrack(" track-42 ")
        val second = PandaWaveAudioCacheKey.fileNameForTrack("track-42")

        assertEquals(first, second)
        assertTrue(first.endsWith(".audio"))
        assertTrue(first.removeSuffix(".audio").all { value -> value in '0'..'9' || value in 'a'..'f' })
    }

    @Test
    fun `panda wave cache keys reject blank track ids`() {
        assertFailsWith<IllegalArgumentException> {
            PandaWaveAudioCacheKey.fileNameForTrack(" ")
        }
    }
}
