package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult

/**
 * Connection boundary between an app-facing gateway and a bound engine service.
 */
interface EngineServiceConnection : AutoCloseable {
    val service: EngineService?

    fun connect(listener: EngineServiceListener)

    /**
     * Invalidates a service proxy after a failed Binder transaction.
     *
     * Implementations must only clear the connection when [service] is still their current proxy.
     */
    fun invalidate(service: EngineService) = Unit
}

/**
 * Narrow command/snapshot surface exposed by a connected engine service.
 */
interface EngineService {
    fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun resendVerification(email: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun verifyEmail(
        verificationToken: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun loginPassword(
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun logout(): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun snapshot(): EngineSnapshot

    fun browseResult(index: Int): EngineCatalogItem?
    fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        boundedPage(offset, limit, ::browseResult)
    fun discoveryResult(index: Int): EngineCatalogItem? = null
    fun forYouResult(index: Int): EngineCatalogItem? = null
    fun recommendationResult(index: Int): EngineCatalogItem? = null
    fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        boundedPage(offset, limit, ::discoveryResult)
    fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        boundedPage(offset, limit, ::forYouResult)
    fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        boundedPage(offset, limit, ::recommendationResult)
    fun profilePreferenceValue(key: String): String? = null

    fun searchResult(index: Int): EngineCatalogItem?
    fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        boundedPage(offset, limit, ::searchResult)

    fun historyEntry(index: Int): EngineHistoryItem? = null
    fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
        EngineHistoryPage(generation, boundedPage(offset, limit, ::historyEntry))

    fun savedTrack(index: Int): EngineLibraryItem? = null
    fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        boundedPage(offset, limit, ::savedTrack)

    fun likedTrack(index: Int): EngineLibraryItem? = null
    fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        boundedPage(offset, limit, ::likedTrack)

    fun pendingLibraryTrackId(index: Int): String? = null
    fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        boundedPage(offset, limit, ::pendingLibraryTrackId)

    fun playlist(index: Int): EnginePlaylistItem? = null
    fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> =
        boundedPage(offset, limit, ::playlist)

    fun playlistTrack(index: Int): EnginePlaylistTrackItem? = null
    fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> =
        boundedPage(offset, limit, ::playlistTrack)

    fun selectedPlaylistId(): String? = null

    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}

private const val MAX_ENGINE_SERVICE_PAGE_QUERY_SIZE = 50

private fun <T> boundedPage(offset: Int, limit: Int, itemAt: (Int) -> T?): List<T> {
    val start = offset.coerceAtLeast(0)
    val count = limit.coerceIn(0, MAX_ENGINE_SERVICE_PAGE_QUERY_SIZE)
    return List(count) { index -> itemAt(start + index) }.filterNotNull()
}

/**
 * Listener used by the bound service connection to publish engine updates.
 */
interface EngineServiceListener {
    fun onSnapshotChanged(snapshot: EngineSnapshot)

    fun onEngineEvent(event: EngineEvent) = Unit
}
