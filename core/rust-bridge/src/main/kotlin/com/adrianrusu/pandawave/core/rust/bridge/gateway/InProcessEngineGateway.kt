package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
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
    override fun discoveryResult(index: Int): EngineCatalogItem? = engine.discoveryResult(index)
    override fun profilePreferenceValue(key: String): String? = engine.profilePreferenceValue(key)

    override fun searchResult(index: Int): EngineCatalogItem? = engine.searchResult(index)
    override fun savedTrack(index: Int) = engine.savedTrack(index)
    override fun likedTrack(index: Int) = engine.likedTrack(index)
    override fun pendingLibraryTrackId(index: Int) = engine.pendingLibraryTrackId(index)
    override fun playlist(index: Int) = engine.playlist(index)
    override fun playlistTrack(index: Int) = engine.playlistTrack(index)
    override fun selectedPlaylistId(): String? = engine.selectedPlaylistId()
    override fun playlistReconciliation() = engine.playlistReconciliation()

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        withSecret(password) { engine.registerPassword(email, password) }

    override fun resendVerification(email: String): EngineAuthOperationResult = engine.resendVerification(email)

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(verificationToken) {
            engine.verifyEmail(verificationToken, deviceLabel)
        }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(password) {
            engine.loginPassword(email, password, deviceLabel)
        }

    override fun logout(): EngineAuthOperationResult = engine.logout()

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
