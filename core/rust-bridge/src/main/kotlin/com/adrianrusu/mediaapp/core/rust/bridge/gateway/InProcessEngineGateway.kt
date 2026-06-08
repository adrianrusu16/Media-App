package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine

/**
 * Gateway implementation used while the app and fake engine live in-process.
 */
class InProcessEngineGateway(private val engine: RustEngine) : EngineGateway {
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()

    override fun snapshot(): EngineSnapshot = engine.snapshot()

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
