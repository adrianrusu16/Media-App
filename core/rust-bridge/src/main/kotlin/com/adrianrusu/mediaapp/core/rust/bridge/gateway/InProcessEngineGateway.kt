package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine

/**
 * Gateway implementation used while the app and fake engine live in-process.
 */
class InProcessEngineGateway(private val engine: RustEngine) : EngineGateway {
    private val listeners = mutableSetOf<(EngineSnapshot) -> Unit>()

    override fun snapshot(): EngineSnapshot = engine.snapshot()

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val result = engine.dispatch(command)
        notifySnapshotChanged(result.snapshot)
        return result
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(snapshot())

        return AutoCloseable {
            listeners -= listener
        }
    }

    private fun notifySnapshotChanged(snapshot: EngineSnapshot) {
        listeners.toList().forEach { listener ->
            listener(snapshot)
        }
    }
}
