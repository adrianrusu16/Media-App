package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

interface RustEngine {
    fun snapshot(): EngineSnapshot

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}
