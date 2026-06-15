package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

data class EngineDispatchResult(
    val snapshot: EngineSnapshot,
    val event: EngineEvent,
    val effects: List<EngineEffect> = emptyList()
)
