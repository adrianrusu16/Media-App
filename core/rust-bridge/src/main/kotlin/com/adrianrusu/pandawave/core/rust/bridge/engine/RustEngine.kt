package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
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
    /**
     * Default path snapshots once, then walks [historyEntry]. Native [PandaEngine]
     * overrides this with one bulk JNI call that already includes generation.
     */
    fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage {
        val snapshot = snapshot()
        return EngineHistoryPage(
            generation = snapshot.historyGeneration,
            items = if (snapshot.historyGeneration == generation) {
                boundedPage(offset, limit, ::historyEntry)
            } else {
                emptyList()
            },
        )
    }

    fun savedTrack(index: Int): EngineLibraryItem? = null
    fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        boundedPage(offset, limit, ::savedTrack)
    fun playlist(index: Int): EnginePlaylistItem? = null
    fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> =
        boundedPage(offset, limit, ::playlist)
    fun playlistTrack(index: Int): EnginePlaylistTrackItem? = null
    fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> =
        boundedPage(offset, limit, ::playlistTrack)
    fun selectedPlaylistId(): String? = null
    fun playlistReconciliation(): EnginePlaylistReconciliation? = null

    fun likedTrack(index: Int): EngineLibraryItem? = null
    fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        boundedPage(offset, limit, ::likedTrack)

    fun pendingLibraryTrackId(index: Int): String? = null
    fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        boundedPage(offset, limit, ::pendingLibraryTrackId)

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}

private const val MAX_ENGINE_PAGE_QUERY_SIZE = 50

private fun <T> boundedPage(offset: Int, limit: Int, itemAt: (Int) -> T?): List<T> {
    val start = offset.coerceAtLeast(0)
    val count = limit.coerceIn(0, MAX_ENGINE_PAGE_QUERY_SIZE)
    return List(count) { index -> itemAt(start + index) }.filterNotNull()
}
