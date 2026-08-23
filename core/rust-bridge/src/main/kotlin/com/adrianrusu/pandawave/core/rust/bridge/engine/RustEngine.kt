package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

interface RustEngine {
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

    fun setAudioSourceResolver(resolver: AudioSourceResolver)

    fun snapshot(): EngineSnapshot

    /**
     * Starts engine-owned backend health monitoring. The host supplies only result delivery;
     * probe cadence and retry policy remain an engine concern.
     */
    fun startBackendHealthMonitoring(
        onDispatchResult: (EngineDispatchResult) -> Unit,
        onSnapshotChanged: (EngineSnapshot) -> Unit
    ) = Unit

    /** Stops engine-owned backend health monitoring without destroying the engine. */
    fun stopBackendHealthMonitoring() = Unit

    /** Reports a platform connectivity observation without prescribing a probe schedule. */
    fun hintNetworkAvailability(isAvailable: Boolean) = Unit

    fun browseResult(index: Int): EngineCatalogItem?

    fun discoveryResult(index: Int): EngineCatalogItem? = null
    fun forYouResult(index: Int): EngineCatalogItem? = null
    fun recommendationResult(index: Int): EngineCatalogItem? = null

    fun profilePreferenceValue(key: String): String? = null

    fun searchResult(index: Int): EngineCatalogItem?

    fun historyEntry(index: Int): EngineHistoryItem? = null

    fun savedTrack(index: Int): EngineLibraryItem? = null
    fun playlist(index: Int): com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem? = null
    fun playlistTrack(index: Int): com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem? = null
    fun selectedPlaylistId(): String? = null
    fun playlistReconciliation(): com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation? = null

    fun likedTrack(index: Int): EngineLibraryItem? = null

    fun pendingLibraryTrackId(index: Int): String? = null

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}
