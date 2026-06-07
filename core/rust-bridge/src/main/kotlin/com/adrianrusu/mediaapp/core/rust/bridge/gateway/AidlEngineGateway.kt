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

    private val listener = EngineServiceListener { snapshot ->
        latestSnapshot = snapshot
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
        val service = connection.service ?: return unavailableResult(command)

        service.dispatch(command)

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            )
        )
    }

    override fun close() {
        connection.close()
    }

    private fun unavailableResult(command: EngineCommand): EngineDispatchResult = EngineDispatchResult(
        snapshot = snapshot(),
        event = EngineEvent(
            type = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
            message = command.type
        )
    )
}
