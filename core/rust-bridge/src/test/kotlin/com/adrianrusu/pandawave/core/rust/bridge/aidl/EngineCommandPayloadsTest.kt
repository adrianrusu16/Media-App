package com.adrianrusu.pandawave.core.rust.bridge.aidl

import com.adrianrusu.pandawave.core.rust.bridge.engine.native.PandaEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineCommandPayloadsTest {
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
}
