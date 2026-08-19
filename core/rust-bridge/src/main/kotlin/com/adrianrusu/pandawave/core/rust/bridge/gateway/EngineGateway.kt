package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult

/**
 * App-facing boundary for reading and commanding the PandaEngine.
 */
interface EngineGateway {
    fun snapshot(): EngineSnapshot

    fun browseResult(index: Int): EngineCatalogItem?
    fun discoveryResult(index: Int): EngineCatalogItem? = null
    fun forYouResult(index: Int): EngineCatalogItem? = null
    fun recommendationResult(index: Int): EngineCatalogItem? = null
    fun profilePreferenceValue(key: String): String? = null

    fun searchResult(index: Int): EngineCatalogItem?

    fun savedTrack(index: Int): EngineLibraryItem? = null

    fun likedTrack(index: Int): EngineLibraryItem? = null

    fun pendingLibraryTrackId(index: Int): String? = null
    fun playlist(index: Int): EnginePlaylistItem? = null
    fun playlistTrack(index: Int): EnginePlaylistTrackItem? = null
    fun selectedPlaylistId(): String? = null
    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult

    fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable

    fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable
}
