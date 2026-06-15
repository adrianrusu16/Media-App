package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

interface RustEngine {
    fun snapshot(): EngineSnapshot

    fun browseResult(index: Int): EngineCatalogItem?

    fun searchResult(index: Int): EngineCatalogItem?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}
