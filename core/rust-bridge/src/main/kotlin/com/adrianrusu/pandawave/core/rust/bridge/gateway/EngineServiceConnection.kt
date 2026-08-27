package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
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

    fun resendVerification(email: String): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun logout(): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

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

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}

/**
 * Listener used by the bound service connection to publish engine updates.
 */
interface EngineServiceListener {
    fun onSnapshotChanged(snapshot: EngineSnapshot)

    fun onEngineEvent(event: EngineEvent) = Unit
}
