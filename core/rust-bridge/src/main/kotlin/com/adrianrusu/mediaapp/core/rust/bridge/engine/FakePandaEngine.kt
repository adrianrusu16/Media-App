package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal class FakePandaEngine(private val clock: () -> Long = System::currentTimeMillis) : RustEngine {
    @Volatile
    private var currentSnapshot: EngineSnapshot =
        EngineSnapshot.idle(clock())

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val nextSnapshot = FakePandaEngineReducer.reduce(
            current = currentSnapshot,
            command = command,
            nowMillis = clock()
        )
        currentSnapshot = nextSnapshot

        return EngineDispatchResult(
            snapshot = nextSnapshot,
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            )
        )
    }
}
