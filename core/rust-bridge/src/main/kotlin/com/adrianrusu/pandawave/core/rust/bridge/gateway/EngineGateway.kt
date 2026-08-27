package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult

/**
 * App-facing boundary for reading and commanding the PandaEngine.
 */
interface EngineGateway {
    fun snapshot(): EngineSnapshot

    fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun profilePreferenceValue(key: String): String? = null

    fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()

    fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
        EngineHistoryPage(generation, emptyList())

    fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> = emptyList()

    fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> = emptyList()

    fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> = emptyList()
    fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> = emptyList()
    fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> = emptyList()
    fun selectedPlaylistId(): String? = null
    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult

    fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable

    fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable
}
