package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        assertEquals(listOf(EngineEffect(type = EngineEffect.TYPE_SEEK)), seekResult.effects)
        assertEquals(listOf(EngineEffect(type = EngineEffect.TYPE_SET_SPEED)), speedResult.effects)
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
        assertEquals("browse-0", engine.browseResult(0)?.mediaId)
        assertEquals("Browse result 0", engine.browseResult(0)?.title)
        assertEquals(EngineCatalogItem.TYPE_ALBUM, engine.browseResult(0)?.itemType)
        assertEquals("search-0", engine.searchResult(0)?.mediaId)
        assertEquals("Search result 0", engine.searchResult(0)?.title)
        assertEquals("Canopy Sessions", engine.searchResult(0)?.album)
        assertEquals(EngineCatalogItem.TYPE_TRACK, engine.searchResult(0)?.itemType)
        assertEquals(null, engine.browseResult(1))
        assertEquals(null, engine.searchResult(1))
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
        assertEquals(
            listOf(
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
}
