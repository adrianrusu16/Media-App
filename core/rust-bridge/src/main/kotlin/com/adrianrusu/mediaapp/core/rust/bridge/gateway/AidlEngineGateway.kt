package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger

/**
 * Engine gateway backed by the AIDL media engine service.
 */
class AidlEngineGateway(
    private val connection: EngineServiceConnection,
    private val telemetryLogger: TelemetryLogger? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : EngineGateway,
    AutoCloseable {
    private var latestSnapshot: EngineSnapshot? = null
    private var isClosed = false
    private var isDrainingPendingCommands = false
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()
    private val pendingCommands = ArrayDeque<EngineCommand>()
    private val pendingPlatformEvents = ArrayDeque<EnginePlatformEvent>()

    private val listener = object : EngineServiceListener {
        override fun onSnapshotChanged(snapshot: EngineSnapshot) {
            latestSnapshot = snapshot
            notifySnapshotChanged(snapshot)
            drainPendingCommands()
        }

        override fun onEngineEvent(event: EngineEvent) {
            logEngineEvent(event)
            notifyEngineEvent(event)
        }
    }

    init {
        connection.connect(listener)
    }

    override fun snapshot(): EngineSnapshot {
        val serviceSnapshot = connection.service?.snapshot()

        return when (serviceSnapshot) {
            null -> latestSnapshot ?: EngineSnapshot.idle(nowMillis = clock())

            else -> {
                latestSnapshot = serviceSnapshot
                serviceSnapshot
            }
        }
    }

    override fun browseResult(index: Int): EngineCatalogItem? = connection.service?.browseResult(index)

    override fun searchResult(index: Int): EngineCatalogItem? = connection.service?.searchResult(index)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val service = connection.service

        return when {
            isClosed -> unavailableResult(command)

            service == null -> queuedResult(command)

            else -> {
                service.dispatch(command)
                logCommand(
                    command = command,
                    status = STATUS_APPLIED
                )
                EngineDispatchResult(
                    snapshot = snapshot(),
                    event = EngineEvent(
                        type = EngineEvent.TYPE_COMMAND_APPLIED,
                        message = command.type
                    )
                )
            }
        }
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        val service = connection.service

        return when {
            isClosed -> unavailableResult(event)

            service == null -> queuedResult(event)

            else -> {
                service.dispatchPlatformEvent(event)
                logPlatformEvent(
                    event = event,
                    status = STATUS_APPLIED
                )
                EngineDispatchResult(
                    snapshot = snapshot(),
                    event = EngineEvent(
                        type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                        message = event.type
                    )
                )
            }
        }
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

    override fun close() {
        isClosed = true
        pendingCommands.clear()
        pendingPlatformEvents.clear()
        listeners.clear()
        eventListeners.clear()
        connection.close()
    }

    private fun drainPendingCommands() {
        if (isDrainingPendingCommands) {
            return
        }

        val service = connection.service ?: return
        isDrainingPendingCommands = true
        try {
            while (pendingCommands.isNotEmpty()) {
                val command = pendingCommands.removeFirst()
                service.dispatch(command)
                val serviceSnapshot = service.snapshot()
                latestSnapshot = serviceSnapshot
                logCommand(
                    command = command,
                    status = STATUS_REPLAYED
                )
                notifySnapshotChanged(serviceSnapshot)
            }

            while (pendingPlatformEvents.isNotEmpty()) {
                val event = pendingPlatformEvents.removeFirst()
                service.dispatchPlatformEvent(event)
                val serviceSnapshot = service.snapshot()
                latestSnapshot = serviceSnapshot
                logPlatformEvent(
                    event = event,
                    status = STATUS_REPLAYED
                )
                notifySnapshotChanged(serviceSnapshot)
            }
        } finally {
            isDrainingPendingCommands = false
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

    private fun queuedResult(command: EngineCommand): EngineDispatchResult {
        if (pendingCommands.size == MAX_PENDING_COMMANDS) {
            pendingCommands.removeFirst()
        }
        pendingCommands += command
        logCommand(
            command = command,
            status = STATUS_QUEUED
        )

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_QUEUED,
                message = command.type
            )
        )
    }

    private fun queuedResult(event: EnginePlatformEvent): EngineDispatchResult {
        if (pendingPlatformEvents.size == MAX_PENDING_EVENTS) {
            pendingPlatformEvents.removeFirst()
        }
        pendingPlatformEvents += event
        logPlatformEvent(
            event = event,
            status = STATUS_QUEUED
        )

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

    private fun logCommand(command: EngineCommand, status: String) {
        telemetryLogger?.debug(
            name = EVENT_ENGINE_GATEWAY_COMMAND,
            attributes = mapOf(
                ATTRIBUTE_COMMAND_TYPE to command.type,
                ATTRIBUTE_STATUS to status,
                ATTRIBUTE_PENDING_COUNT to pendingCommands.size.toString()
            )
        )
    }

    private fun logPlatformEvent(event: EnginePlatformEvent, status: String) {
        telemetryLogger?.debug(
            name = EVENT_ENGINE_GATEWAY_PLATFORM_EVENT,
            attributes = mapOf(
                ATTRIBUTE_PLATFORM_EVENT_TYPE to event.type,
                ATTRIBUTE_STATUS to status,
                ATTRIBUTE_PENDING_COUNT to pendingPlatformEvents.size.toString()
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

    private companion object {
        const val MAX_PENDING_COMMANDS = 32
        const val MAX_PENDING_EVENTS = 32
        const val EVENT_ENGINE_GATEWAY_COMMAND = "engine_gateway.command"
        const val EVENT_ENGINE_GATEWAY_EVENT = "engine_gateway.event"
        const val EVENT_ENGINE_GATEWAY_PLATFORM_EVENT = "engine_gateway.platform_event"
        const val ATTRIBUTE_COMMAND_TYPE = "command_type"
        const val ATTRIBUTE_EVENT_TYPE = "event_type"
        const val ATTRIBUTE_PLATFORM_EVENT_TYPE = "platform_event_type"
        const val ATTRIBUTE_MESSAGE_PRESENT = "message_present"
        const val ATTRIBUTE_PENDING_COUNT = "pending_count"
        const val ATTRIBUTE_STATUS = "status"
        const val STATUS_APPLIED = "applied"
        const val STATUS_QUEUED = "queued"
        const val STATUS_REPLAYED = "replayed"
        const val STATUS_UNAVAILABLE = "unavailable"
    }
}
