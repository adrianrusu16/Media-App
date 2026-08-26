package com.adrianrusu.pandawave.core.rust.bridge.gateway

import android.os.RemoteException
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import java.util.concurrent.Executor

/**
 * Engine gateway backed by the AIDL media engine service.
 */
class AidlEngineGateway(
    private val connection: EngineServiceConnection,
    telemetryLogger: TelemetryLogger? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    callbackExecutor: Executor = Executor { command -> command.run() },
) : EngineGateway,
    EngineAuthGateway,
    AutoCloseable {
    private val telemetryLogger = telemetryLogger?.forModule(TelemetryModule.RustBridge)
    private val stateLock = Any()
    private val callbackExecutor = SerialExecutor(callbackExecutor)
    private var latestSnapshot: EngineSnapshot? = null
    private var isClosed = false
    private var isDrainingPendingCommands = false
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()
    private val authAvailabilityListeners = mutableSetOf<(Boolean) -> Unit>()
    private var lastAuthAvailability: Boolean? = null
    private val pendingCommands = ArrayDeque<EngineCommand>()
    private val pendingPlatformEvents = ArrayDeque<EnginePlatformEvent>()

    private val listener = object : EngineServiceListener {
        override fun onSnapshotChanged(snapshot: EngineSnapshot) {
            val accepted = synchronized(stateLock) {
                if (isClosed) {
                    false
                } else {
                    latestSnapshot = snapshot
                    true
                }
            }
            if (!accepted) return
            notifySnapshotChanged(snapshot)
            notifyAuthAvailabilityChanged()
            drainPendingCommands()
        }

        override fun onEngineEvent(event: EngineEvent) {
            if (closed()) return
            notifyAuthAvailabilityChanged()
            logEngineEvent(event)
            notifyEngineEvent(event)
        }
    }

    init {
        PandaTrace.section("PW.Engine.Gateway.connect") {
            connection.connect(listener)
        }
    }

    override fun snapshot(): EngineSnapshot = PandaTrace.section("PW.Engine.Gateway.snapshot") {
        val serviceSnapshot = withRemoteService(null) { service -> service.snapshot() }

        when (serviceSnapshot) {
            null -> synchronized(stateLock) {
                latestSnapshot ?: EngineSnapshot.idle(nowMillis = clock())
            }

            else -> {
                synchronized(stateLock) {
                    if (!isClosed) latestSnapshot = serviceSnapshot
                }
                serviceSnapshot
            }
        }
    }

    override fun browseResult(index: Int): EngineCatalogItem? = withRemoteService(null) { it.browseResult(index) }
    override fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        withRemoteService(emptyList()) { it.browseResultsPage(offset, limit) }
    override fun discoveryResult(index: Int): EngineCatalogItem? = withRemoteService(null) { it.discoveryResult(index) }
    override fun forYouResult(index: Int): EngineCatalogItem? = withRemoteService(null) { it.forYouResult(index) }
    override fun recommendationResult(index: Int): EngineCatalogItem? = withRemoteService(null) { it.recommendationResult(index) }
    override fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        withRemoteService(emptyList()) { it.discoveryResultsPage(offset, limit) }
    override fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        withRemoteService(emptyList()) { it.forYouResultsPage(offset, limit) }
    override fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        withRemoteService(emptyList()) { it.recommendationResultsPage(offset, limit) }
    override fun profilePreferenceValue(key: String): String? = withRemoteService(null) { it.profilePreferenceValue(key) }

    override fun searchResult(index: Int): EngineCatalogItem? = withRemoteService(null) { it.searchResult(index) }
    override fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        withRemoteService(emptyList()) { it.searchResultsPage(offset, limit) }
    override fun historyEntry(index: Int) = withRemoteService(null) { it.historyEntry(index) }
    override fun historyPage(offset: Int, limit: Int, generation: Long) =
        withRemoteService(super<EngineGateway>.historyPage(offset, limit, generation)) { it.historyPage(offset, limit, generation) }
    override fun savedTrack(index: Int) = withRemoteService(null) { it.savedTrack(index) }
    override fun savedTracksPage(offset: Int, limit: Int) =
        withRemoteService(emptyList()) { it.savedTracksPage(offset, limit) }
    override fun likedTrack(index: Int) = withRemoteService(null) { it.likedTrack(index) }
    override fun likedTracksPage(offset: Int, limit: Int) =
        withRemoteService(emptyList()) { it.likedTracksPage(offset, limit) }
    override fun pendingLibraryTrackId(index: Int) = withRemoteService(null) { it.pendingLibraryTrackId(index) }
    override fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        withRemoteService(emptyList()) { it.pendingLibraryTrackIdsPage(offset, limit) }
    override fun playlist(index: Int): EnginePlaylistItem? = withRemoteService(null) { it.playlist(index) }
    override fun playlistsPage(offset: Int, limit: Int) =
        withRemoteService(emptyList()) { it.playlistsPage(offset, limit) }
    override fun playlistTrack(index: Int): EnginePlaylistTrackItem? = withRemoteService(null) { it.playlistTrack(index) }
    override fun playlistTracksPage(offset: Int, limit: Int) =
        withRemoteService(emptyList()) { it.playlistTracksPage(offset, limit) }
    override fun selectedPlaylistId(): String? = withRemoteService(null) { it.selectedPlaylistId() }
    override fun playlistReconciliation(): EnginePlaylistReconciliation? = withRemoteService(null) { it.playlistReconciliation() }

    override val isAuthAvailable: Boolean
        get() = connection.service != null && synchronized(stateLock) { !isClosed }

    override fun observeAuthAvailability(listener: (Boolean) -> Unit): AutoCloseable {
        val registered = synchronized(stateLock) {
            if (isClosed) false else authAvailabilityListeners.add(listener)
        }
        callbackExecutor.execute { listener(registered && isAuthAvailable) }
        return AutoCloseable {
            synchronized(stateLock) { authAvailabilityListeners -= listener }
        }
    }

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        withSecret(password) { withRemoteService(null) { it.registerPassword(email, password) } }

    override fun resendVerification(email: String): EngineAuthOperationResult = if (closed()) {
        EngineAuthOperationResult.unavailable()
    } else {
        withRemoteService(null) { it.resendVerification(email) } ?: EngineAuthOperationResult.unavailable()
    }

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(verificationToken) {
            val startedAt = clock()
            val result = withRemoteService(null) { it.verifyEmail(verificationToken, deviceLabel) }
            val snapshotAuth = synchronized(stateLock) { latestSnapshot?.authState?.state } ?: "unknown"
            telemetryLogger?.info(
                name = EVENT_ENGINE_AUTH_VERIFY,
                attributes = mapOf(
                    ATTRIBUTE_STATUS to (result?.status ?: STATUS_UNAVAILABLE),
                    ATTRIBUTE_ELAPSED_MS to (clock() - startedAt).toString(),
                    ATTRIBUTE_SNAPSHOT_AUTH to snapshotAuth
                )
            )
            result
        }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        withSecret(password) {
            val startedAt = clock()
            val result = withRemoteService(null) { it.loginPassword(email, password, deviceLabel) }
            val snapshotAuth = synchronized(stateLock) { latestSnapshot?.authState?.state } ?: "unknown"
            telemetryLogger?.info(
                name = EVENT_ENGINE_AUTH_LOGIN,
                attributes = mapOf(
                    ATTRIBUTE_STATUS to (result?.status ?: STATUS_UNAVAILABLE),
                    ATTRIBUTE_ELAPSED_MS to (clock() - startedAt).toString(),
                    ATTRIBUTE_SNAPSHOT_AUTH to snapshotAuth
                )
            )
            result
        }

    override fun logout(): EngineAuthOperationResult = if (closed()) {
        EngineAuthOperationResult.unavailable()
    } else {
        withRemoteService(null) { it.logout() } ?: EngineAuthOperationResult.unavailable()
    }

    private inline fun withSecret(
        secret: ByteArray,
        operation: () -> EngineAuthOperationResult?
    ): EngineAuthOperationResult = try {
        if (closed()) {
            EngineAuthOperationResult.unavailable()
        } else {
            operation() ?: EngineAuthOperationResult.unavailable()
        }
    } finally {
        secret.fill(0)
    }

    override fun dispatch(command: EngineCommand): EngineDispatchResult =
        PandaTrace.section("PW.Engine.Gateway.dispatch") {
        val service = connection.service

        when {
            closed() -> unavailableResult(command)

            service == null && !command.isReplayableAfterReconnect() -> unavailableResult(command)

            service == null -> queuedResult(command)

            else -> {
                try {
                    val result = service.dispatch(command)
                    synchronized(stateLock) {
                        if (!isClosed) latestSnapshot = result.snapshot
                    }
                    logCommand(command = command, status = STATUS_APPLIED)
                    result
                } catch (_: RemoteException) {
                    invalidateFailedService(service)
                    unavailableResult(command)
                }
            }
        }
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        PandaTrace.section("PW.Engine.Gateway.platformEvent") {
        val service = connection.service

        when {
            closed() -> unavailableResult(event)

            service == null && !event.isReplayableAfterReconnect() -> unavailableResult(event)

            service == null -> queuedResult(event)

            else -> {
                try {
                    val result = service.dispatchPlatformEvent(event)
                    synchronized(stateLock) {
                        if (!isClosed) latestSnapshot = result.snapshot
                    }
                    logPlatformEvent(event = event, status = STATUS_APPLIED)
                    result
                } catch (_: RemoteException) {
                    invalidateFailedService(service)
                    unavailableResult(event)
                }
            }
        }
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        val initialSnapshot = snapshot()
        val registered = synchronized(stateLock) {
            if (isClosed) false else listeners.add(listener)
        }
        if (registered) callbackExecutor.execute { listener(initialSnapshot) }

        return AutoCloseable {
            synchronized(stateLock) { listeners -= listener }
        }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable {
        synchronized(stateLock) {
            if (!isClosed) eventListeners += listener
        }

        return AutoCloseable {
            synchronized(stateLock) { eventListeners -= listener }
        }
    }

    override fun close() {
        val availabilityCallbacks = synchronized(stateLock) {
            if (isClosed) return
            isClosed = true
            lastAuthAvailability = false
            pendingCommands.clear()
            pendingPlatformEvents.clear()
            listeners.clear()
            eventListeners.clear()
            authAvailabilityListeners.toList().also { authAvailabilityListeners.clear() }
        }
        if (availabilityCallbacks.isNotEmpty()) {
            callbackExecutor.execute {
                availabilityCallbacks.forEach { listener -> listener(false) }
            }
        }
        connection.close()
    }

    private fun drainPendingCommands() {
        val service = connection.service ?: return
        val claimed = synchronized(stateLock) {
            if (isClosed || isDrainingPendingCommands) {
                false
            } else {
                isDrainingPendingCommands = true
                true
            }
        }
        if (!claimed) return
        var ownsDrain = true
        try {
            while (true) {
                when (val work = takePendingWork()) {
                    null -> {
                        ownsDrain = false
                        return
                    }

                    is PendingWork.Command -> {
                        val result = service.dispatch(work.value)
                        synchronized(stateLock) {
                            if (!isClosed) latestSnapshot = result.snapshot
                        }
                        logCommand(command = work.value, status = STATUS_REPLAYED)
                        notifySnapshotChanged(result.snapshot)
                    }

                    is PendingWork.PlatformEvent -> {
                        val result = service.dispatchPlatformEvent(work.value)
                        synchronized(stateLock) {
                            if (!isClosed) latestSnapshot = result.snapshot
                        }
                        logPlatformEvent(event = work.value, status = STATUS_REPLAYED)
                        notifySnapshotChanged(result.snapshot)
                    }
                }
            }
        } catch (_: RemoteException) {
            invalidateFailedService(service)
        } finally {
            if (ownsDrain) {
                val shouldRestart = synchronized(stateLock) {
                    isDrainingPendingCommands = false
                    !isClosed && (pendingCommands.isNotEmpty() || pendingPlatformEvents.isNotEmpty())
                }
                if (shouldRestart) drainPendingCommands()
            }
        }
    }

    private fun takePendingWork(): PendingWork? = synchronized(stateLock) {
        when {
            pendingCommands.isNotEmpty() -> PendingWork.Command(pendingCommands.removeFirst())
            pendingPlatformEvents.isNotEmpty() -> PendingWork.PlatformEvent(pendingPlatformEvents.removeFirst())
            else -> {
                isDrainingPendingCommands = false
                null
            }
        }
    }

    private fun notifySnapshotChanged(snapshot: EngineSnapshot) {
        val callbacks = synchronized(stateLock) { listeners.toList() }
        if (callbacks.isNotEmpty()) callbackExecutor.execute {
            PandaTrace.section("PW.Engine.Gateway.snapshotCallback") {
                val startedAt = clock()
                if (!closed()) callbacks.forEach { listener -> listener(snapshot) }
                val elapsedMs = clock() - startedAt
                if (
                    elapsedMs >= SNAPSHOT_FANOUT_LOG_THRESHOLD_MS ||
                    snapshot.authState.state == EngineAuthState.AUTHENTICATED
                ) {
                    PandaLog.i(PandaLog.Tag.AUTH) {
                        "snapshot.fanout listeners=${callbacks.size} elapsedMs=$elapsedMs " +
                            "auth=${snapshot.authState.state}"
                    }
                }
            }
        }
    }

    private fun notifyEngineEvent(event: EngineEvent) {
        val callbacks = synchronized(stateLock) { eventListeners.toList() }
        if (callbacks.isNotEmpty()) callbackExecutor.execute {
            PandaTrace.section("PW.Engine.Gateway.eventCallback") {
                if (!closed()) callbacks.forEach { listener -> listener(event) }
            }
        }
    }

    private fun notifyAuthAvailabilityChanged() {
        val available = isAuthAvailable
        val callbacks = synchronized(stateLock) {
            if (lastAuthAvailability == available) return
            lastAuthAvailability = available
            authAvailabilityListeners.toList()
        }
        if (callbacks.isNotEmpty()) callbackExecutor.execute {
            PandaTrace.section("PW.Engine.Gateway.authAvailabilityCallback") {
                callbacks.forEach { listener -> listener(available) }
            }
        }
    }

    private fun EngineService.effects(): List<EngineEffect> = List(
        size = effectCount(),
        init = ::effect
    ).filterNotNull()

    private inline fun <T> withRemoteService(
        unavailableValue: T,
        operation: (EngineService) -> T
    ): T {
        val service = connection.service ?: return unavailableValue
        return try {
            operation(service)
        } catch (_: RemoteException) {
            invalidateFailedService(service)
            unavailableValue
        }
    }

    private fun invalidateFailedService(service: EngineService) {
        connection.invalidate(service)
        notifyAuthAvailabilityChanged()
    }

    private fun queuedResult(command: EngineCommand): EngineDispatchResult {
        val queued = synchronized(stateLock) {
            if (isClosed) {
                false
            } else {
                if (pendingCommands.size == MAX_PENDING_COMMANDS) {
                    pendingCommands.removeFirst()
                }
                pendingCommands += command
                true
            }
        }
        if (!queued) return unavailableResult(command)
        logCommand(
            command = command,
            status = STATUS_QUEUED
        )
        drainPendingCommands()

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_QUEUED,
                message = command.type
            )
        )
    }

    private fun queuedResult(event: EnginePlatformEvent): EngineDispatchResult {
        val queued = synchronized(stateLock) {
            if (isClosed) {
                false
            } else {
                if (pendingPlatformEvents.size == MAX_PENDING_EVENTS) {
                    pendingPlatformEvents.removeFirst()
                }
                pendingPlatformEvents += event
                true
            }
        }
        if (!queued) return unavailableResult(event)
        logPlatformEvent(
            event = event,
            status = STATUS_QUEUED
        )
        drainPendingCommands()

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_QUEUED,
                message = event.type
            )
        )
    }

    private fun unavailableResult(command: EngineCommand): EngineDispatchResult {
        logCommand(
            command = command,
            status = STATUS_UNAVAILABLE
        )

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
                message = command.type
            )
        )
    }

    private fun unavailableResult(event: EnginePlatformEvent): EngineDispatchResult {
        logPlatformEvent(
            event = event,
            status = STATUS_UNAVAILABLE
        )

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
                message = event.type
            )
        )
    }

    private fun EngineCommand.isReplayableAfterReconnect(): Boolean = when (type) {
        EngineCommand.TYPE_UPSERT_PROFILE,
        EngineCommand.TYPE_UPDATE_PROFILE,
        EngineCommand.TYPE_DELETE_PROFILE,
        EngineCommand.TYPE_DELETE_ACCOUNT,
        EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
        EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS,
        EngineCommand.TYPE_DELETE_HISTORY_ENTRY,
        EngineCommand.TYPE_CLEAR_HISTORY,
        EngineCommand.TYPE_SAVE_TRACK,
        EngineCommand.TYPE_REMOVE_SAVED_TRACK,
        EngineCommand.TYPE_LIKE_TRACK,
        EngineCommand.TYPE_UNLIKE_TRACK,
        EngineCommand.TYPE_CREATE_PLAYLIST,
        EngineCommand.TYPE_UPDATE_PLAYLIST,
        EngineCommand.TYPE_DELETE_PLAYLIST,
        EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
        EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
        EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS -> false
        else -> true
    }

    private fun EnginePlatformEvent.isReplayableAfterReconnect(): Boolean =
        type != EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED

    private fun logCommand(command: EngineCommand, status: String) {
        val pendingCount = synchronized(stateLock) { pendingCommands.size }
        telemetryLogger?.debug(
            name = EVENT_ENGINE_GATEWAY_COMMAND,
            attributes = mapOf(
                ATTRIBUTE_COMMAND_TYPE to command.type,
                ATTRIBUTE_STATUS to status,
                ATTRIBUTE_PENDING_COUNT to pendingCount.toString()
            )
        )
    }

    private fun logPlatformEvent(event: EnginePlatformEvent, status: String) {
        val pendingCount = synchronized(stateLock) { pendingPlatformEvents.size }
        telemetryLogger?.debug(
            name = EVENT_ENGINE_GATEWAY_PLATFORM_EVENT,
            attributes = mapOf(
                ATTRIBUTE_PLATFORM_EVENT_TYPE to event.type,
                ATTRIBUTE_STATUS to status,
                ATTRIBUTE_PENDING_COUNT to pendingCount.toString()
            )
        )
    }

    private fun logEngineEvent(event: EngineEvent) {
        telemetryLogger?.debug(
            name = EVENT_ENGINE_GATEWAY_EVENT,
            attributes = mapOf(
                ATTRIBUTE_EVENT_TYPE to event.type,
                ATTRIBUTE_MESSAGE_PRESENT to (event.message != null).toString()
            )
        )
    }

    private fun closed(): Boolean = synchronized(stateLock) { isClosed }

    private sealed interface PendingWork {
        data class Command(val value: EngineCommand) : PendingWork

        data class PlatformEvent(val value: EnginePlatformEvent) : PendingWork
    }

    private companion object {
        const val MAX_PENDING_COMMANDS = 32
        const val MAX_PENDING_EVENTS = 32
        const val EVENT_ENGINE_GATEWAY_COMMAND = "engine_gateway.command"
        const val EVENT_ENGINE_GATEWAY_EVENT = "engine_gateway.event"
        const val EVENT_ENGINE_GATEWAY_PLATFORM_EVENT = "engine_gateway.platform_event"
        const val EVENT_ENGINE_AUTH_LOGIN = "engine.auth.login_password"
        const val EVENT_ENGINE_AUTH_VERIFY = "engine.auth.verify_email"
        const val SNAPSHOT_FANOUT_LOG_THRESHOLD_MS = 50L
        const val ATTRIBUTE_COMMAND_TYPE = "command_type"
        const val ATTRIBUTE_EVENT_TYPE = "event_type"
        const val ATTRIBUTE_PLATFORM_EVENT_TYPE = "platform_event_type"
        const val ATTRIBUTE_MESSAGE_PRESENT = "message_present"
        const val ATTRIBUTE_PENDING_COUNT = "pending_count"
        const val ATTRIBUTE_STATUS = "status"
        const val ATTRIBUTE_ELAPSED_MS = "elapsed_ms"
        const val ATTRIBUTE_SNAPSHOT_AUTH = "snapshot_auth"
        const val STATUS_APPLIED = "applied"
        const val STATUS_QUEUED = "queued"
        const val STATUS_REPLAYED = "replayed"
        const val STATUS_UNAVAILABLE = "unavailable"
    }
}
