package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

/**
 * Connection boundary between an app-facing gateway and a bound engine service.
 */
interface EngineServiceConnection : AutoCloseable {
    val service: EngineService?

    fun connect(listener: EngineServiceListener)
}

/**
 * Narrow command/snapshot surface exposed by a connected engine service.
 */
interface EngineService {
    fun snapshot(): EngineSnapshot

    fun dispatch(command: EngineCommand)
}

/**
 * Listener used by the bound service connection to publish engine snapshots.
 */
fun interface EngineServiceListener {
    fun onSnapshotChanged(snapshot: EngineSnapshot)
}
