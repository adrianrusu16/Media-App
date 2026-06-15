package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal class FakePandaEngine(private val clock: () -> Long = System::currentTimeMillis) : RustEngine {
    @Volatile
    private var currentSnapshot: EngineSnapshot =
        EngineSnapshot.idle(clock())

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = when {
        index in 0 until currentSnapshot.browseResultsCount -> EngineCatalogItem(
            mediaId = "browse-$index",
            title = "Browse result $index"
        )

        else -> null
    }

    override fun searchResult(index: Int): EngineCatalogItem? = when {
        index in 0 until currentSnapshot.searchResultsCount -> EngineCatalogItem(
            mediaId = "search-$index",
            title = "Search result $index"
        )

        else -> null
    }

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

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        val nextSnapshot = FakePandaEngineReducer.reducePlatformEvent(
            current = currentSnapshot,
            event = event,
            nowMillis = clock()
        )
        currentSnapshot = nextSnapshot

        return EngineDispatchResult(
            snapshot = nextSnapshot,
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                message = event.type
            )
        )
    }
}
