package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.engine.RustEngine

/**
 * Gateway implementation used while the app and fake engine live in-process.
 */
class InProcessEngineGateway(private val engine: RustEngine) :
    EngineGateway,
    EngineAuthGateway {
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()

    override val isAuthAvailable: Boolean = true

    override fun observeAuthAvailability(listener: (Boolean) -> Unit): AutoCloseable {
        listener(true)
        return AutoCloseable { }
    }

    override fun snapshot(): EngineSnapshot = engine.snapshot()

    override fun browseResult(index: Int): EngineCatalogItem? = engine.browseResult(index)
    override fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        engine.browseResultsPage(offset, limit)
    override fun discoveryResult(index: Int): EngineCatalogItem? = engine.discoveryResult(index)
    override fun forYouResult(index: Int): EngineCatalogItem? = engine.forYouResult(index)
    override fun recommendationResult(index: Int): EngineCatalogItem? = engine.recommendationResult(index)
    override fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        engine.discoveryResultsPage(offset, limit)
    override fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        engine.forYouResultsPage(offset, limit)
    override fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        engine.recommendationResultsPage(offset, limit)
    override fun profilePreferenceValue(key: String): String? = engine.profilePreferenceValue(key)

    override fun searchResult(index: Int): EngineCatalogItem? = engine.searchResult(index)
    override fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        engine.searchResultsPage(offset, limit)
    override fun historyEntry(index: Int) = engine.historyEntry(index)
    override fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage =
        engine.historyPage(offset, limit, generation)
    override fun savedTrack(index: Int) = engine.savedTrack(index)
    override fun savedTracksPage(offset: Int, limit: Int) = engine.savedTracksPage(offset, limit)
    override fun likedTrack(index: Int) = engine.likedTrack(index)
    override fun likedTracksPage(offset: Int, limit: Int) = engine.likedTracksPage(offset, limit)
    override fun pendingLibraryTrackId(index: Int) = engine.pendingLibraryTrackId(index)
    override fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        engine.pendingLibraryTrackIdsPage(offset, limit)
    override fun playlist(index: Int) = engine.playlist(index)
    override fun playlistsPage(offset: Int, limit: Int) = engine.playlistsPage(offset, limit)
    override fun playlistTrack(index: Int) = engine.playlistTrack(index)
    override fun playlistTracksPage(offset: Int, limit: Int) = engine.playlistTracksPage(offset, limit)
    override fun selectedPlaylistId(): String? = engine.selectedPlaylistId()
    override fun playlistReconciliation() = engine.playlistReconciliation()

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        withSecret(password) { engine.registerPassword(email, password) }

    override fun resendVerification(email: String): EngineAuthOperationResult = engine.resendVerification(email)

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(verificationToken) {
            engine.verifyEmail(verificationToken, deviceLabel)
        }.also { notifySnapshotChanged(engine.snapshot()) }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(password) {
            engine.loginPassword(email, password, deviceLabel)
        }.also { notifySnapshotChanged(engine.snapshot()) }

    override fun logout(): EngineAuthOperationResult =
        engine.logout().also { notifySnapshotChanged(engine.snapshot()) }

    private inline fun withSecret(
        secret: ByteArray,
        operation: () -> EngineAuthOperationResult
    ): EngineAuthOperationResult = try {
        operation()
    } finally {
        secret.fill(0)
    }

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val result = engine.dispatch(command)
        notifySnapshotChanged(result.snapshot)
        notifyEngineEvent(result.event)
        return result
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        val result = engine.dispatchPlatformEvent(event)
        notifySnapshotChanged(result.snapshot)
        notifyEngineEvent(result.event)
        return result
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(snapshot())

        return AutoCloseable {
            listeners -= listener
        }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable {
        eventListeners += listener

        return AutoCloseable {
            eventListeners -= listener
        }
    }

    private fun notifySnapshotChanged(snapshot: EngineSnapshot) {
        listeners.toList().forEach { listener ->
            listener(snapshot)
        }
    }

    private fun notifyEngineEvent(event: EngineEvent) {
        eventListeners.toList().forEach { listener ->
            listener(event)
        }
    }
}
