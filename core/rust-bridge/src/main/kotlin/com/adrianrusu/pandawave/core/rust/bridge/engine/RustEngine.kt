package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

interface RustEngine {
    fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun resendVerification(email: String): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

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

    fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()

    fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()
    fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()

    fun profilePreferenceValue(key: String): String? = null

    fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> = emptyList()

    fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
        EngineHistoryPage(generation, emptyList())

    fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> = emptyList()
    fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> = emptyList()
    fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> = emptyList()
    fun selectedPlaylistId(): String? = null
    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> = emptyList()

    fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> = emptyList()

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}
