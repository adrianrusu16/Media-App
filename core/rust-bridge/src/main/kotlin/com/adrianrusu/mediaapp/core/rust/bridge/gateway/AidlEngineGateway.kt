package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult

/**
 * Engine gateway backed by the AIDL media engine service.
 */
class AidlEngineGateway(
    private val connection: EngineServiceConnection,
    private val clock: () -> Long = System::currentTimeMillis
) : EngineGateway,
    AutoCloseable {
    private var latestSnapshot: EngineSnapshot? = null
    private var isClosed = false
    private var isDrainingPendingCommands = false
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val pendingCommands = ArrayDeque<EngineCommand>()

    private val listener = EngineServiceListener { snapshot ->
        latestSnapshot = snapshot
        notifySnapshotChanged(snapshot)
        drainPendingCommands()
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

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val service = connection.service

        return when {
            isClosed -> unavailableResult(command)

            service == null -> queuedResult(command)

            else -> {
                service.dispatch(command)
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

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(snapshot())

        return AutoCloseable {
            listeners -= listener
        }
    }

    override fun close() {
        isClosed = true
        pendingCommands.clear()
        listeners.clear()
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

    private fun queuedResult(command: EngineCommand): EngineDispatchResult {
        if (pendingCommands.size == MAX_PENDING_COMMANDS) {
            pendingCommands.removeFirst()
        }
        pendingCommands += command

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_QUEUED,
                message = command.type
            )
        )
    }

    private fun unavailableResult(command: EngineCommand): EngineDispatchResult = EngineDispatchResult(
        snapshot = snapshot(),
        event = EngineEvent(
            type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
            message = command.type
        )
    )

    private companion object {
        const val MAX_PENDING_COMMANDS = 32
    }
}
