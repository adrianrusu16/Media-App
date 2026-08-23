package com.adrianrusu.pandawave.core.rust.bridge.aidl

import com.adrianrusu.pandawave.core.rust.bridge.engine.native.PandaEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EngineCommandPayloadsTest {
    @Test
    fun `decoder failure payload carries typed diagnostics and intent`() {
        val payload = Json.parseToJsonElement(
            EngineCommandPayloads.decoderFailed(
                playbackInstanceId = 41L,
                positionMillis = 183_200L,
                decoder = "c2.android.mp3.decoder",
                errorCode = 4003,
                phase = "decoding",
                playWhenReady = true
            )
        ).jsonObject

        assertEquals("decoder_failed", payload.getValue("kind").jsonPrimitive.content)
        assertEquals("41", payload.getValue("playback_instance_id").jsonPrimitive.content)
        assertEquals("183200", payload.getValue("position_ms").jsonPrimitive.content)
        assertEquals("c2.android.mp3.decoder", payload.getValue("decoder").jsonPrimitive.content)
        assertEquals("4003", payload.getValue("error_code").jsonPrimitive.content)
        assertEquals("decoding", payload.getValue("phase").jsonPrimitive.content)
        assertEquals("true", payload.getValue("play_when_ready").jsonPrimitive.content)
    }

    @Test
    fun `discovery payloads and native discriminants are append only and token opaque`() {
        assertEquals(
            "{\"version\":1,\"exclude_track_ids\":[\"track-1\"],\"page\":{\"page_size\":17}}",
            EngineCommandPayloads.discoveryFeed(listOf("track-1"), pageSize = 17)
        )
        assertEquals("{\"version\":1}", EngineCommandPayloads.loadNextDiscoveryPage())
        assertEquals(
            listOf(55, 56),
            listOf(
                EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
                EngineCommand.TYPE_LOAD_NEXT_DISCOVERY_PAGE
            ).map { PandaEngine.nativeCommandType(EngineCommand(it, null)) }
        )
    }
    @Test
    fun searchCatalogBuildsVersionedJsonAndEscapesQuery() {
        assertEquals(
            "{\"version\":1,\"query\":\"a\\\"b\\n\",\"page\":{\"page_size\":25}}",
            EngineCommandPayloads.searchCatalog("a\"b\n", pageSize = 25)
        )
    }

    @Test
    fun browseCatalogBuildsVersionedJsonWithFilters() {
        assertEquals(
            "{\"version\":1,\"parent_id\":\"root\",\"genres\":[\"jazz\",\"fusion\"],\"page\":{\"page_size\":10}}",
            EngineCommandPayloads.browseCatalog("root", listOf("jazz", "fusion"), pageSize = 10)
        )
    }

    @Test
    fun loadNextCatalogPageContainsOnlyVersionAndOperationId() {
        assertEquals(
            "{\"version\":1,\"operation_id\":\"catalog-1\"}",
            EngineCommandPayloads.loadNextCatalogPage("catalog-1")
        )
    }

    @Test
    fun loadNextCatalogCommandMapsToNativeCommand19() {
        val command = EngineCommand(
            type = EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE,
            payload = EngineCommandPayloads.loadNextCatalogPage("catalog-1")
        )

        assertEquals(19, PandaEngine.nativeCommandType(command))
    }

    @Test
    fun profilePayloadsPreserveNullableDisplayNameAndTypedThemeUpdate() {
        assertEquals(
            """{"version":1,"display_name":null}""",
            EngineCommandPayloads.upsertProfile(displayName = null)
        )
        assertEquals(
            """{"version":1,"update_display_name":true,"display_name":""}""",
            EngineCommandPayloads.updateProfileDisplayName(displayName = "")
        )
        assertEquals(
            """{"version":1,"values":{"theme":"forest_tech_dark"}}""",
            EngineCommandPayloads.updateProfileTheme("forest_tech_dark")
        )
    }

    @Test
    fun profileCommandsMapToStableNativeIds() {
        assertEquals(
            20,
            PandaEngine.nativeCommandType(
                EngineCommand(EngineCommand.TYPE_UPSERT_PROFILE, EngineCommandPayloads.upsertProfile(null))
            )
        )
        assertEquals(
            25,
            PandaEngine.nativeCommandType(
                EngineCommand(
                    EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
                    EngineCommandPayloads.updateProfileTheme("system_default")
                )
            )
        )
    }

    @Test
    fun playlistCommandsMapToAppendOnlyNativeIds() {
        val types = listOf(
            EngineCommand.TYPE_CREATE_PLAYLIST,
            EngineCommand.TYPE_UPDATE_PLAYLIST,
            EngineCommand.TYPE_DELETE_PLAYLIST,
            EngineCommand.TYPE_LIST_PLAYLISTS,
            EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE,
            EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
            EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
            EngineCommand.TYPE_LIST_PLAYLIST_TRACKS,
            EngineCommand.TYPE_LOAD_NEXT_PLAYLIST_TRACKS_PAGE,
            EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS,
        )

        assertEquals((40..49).toList(), types.map { PandaEngine.nativeCommandType(EngineCommand(it, null)) })
    }
}
