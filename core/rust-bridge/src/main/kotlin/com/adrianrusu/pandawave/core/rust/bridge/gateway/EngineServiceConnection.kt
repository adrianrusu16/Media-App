package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

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
    fun discoveryResult(index: Int): EngineCatalogItem? = null
    fun forYouResult(index: Int): EngineCatalogItem? = null
    fun recommendationResult(index: Int): EngineCatalogItem? = null
    fun profilePreferenceValue(key: String): String? = null

    fun searchResult(index: Int): EngineCatalogItem?

    fun historyEntry(index: Int): EngineHistoryItem? = null

    fun savedTrack(index: Int): EngineLibraryItem? = null

    fun likedTrack(index: Int): EngineLibraryItem? = null

    fun pendingLibraryTrackId(index: Int): String? = null

    fun playlist(index: Int): EnginePlaylistItem? = null

    fun playlistTrack(index: Int): EnginePlaylistTrackItem? = null

    fun selectedPlaylistId(): String? = null

    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand)

    fun dispatchPlatformEvent(event: EnginePlatformEvent)
}

/**
 * Listener used by the bound service connection to publish engine updates.
 */
interface EngineServiceListener {
    fun onSnapshotChanged(snapshot: EngineSnapshot)

    fun onEngineEvent(event: EngineEvent) = Unit
}
