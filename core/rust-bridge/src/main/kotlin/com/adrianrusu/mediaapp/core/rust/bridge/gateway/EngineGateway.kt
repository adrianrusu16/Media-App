package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult

/**
 * App-facing boundary for reading and commanding the PandaEngine.
 */
interface EngineGateway {
    fun snapshot(): EngineSnapshot

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable
}
