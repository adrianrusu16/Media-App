package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

internal class FakePandaEngine(private val clock: () -> Long = System::currentTimeMillis) : RustEngine {
    @Volatile
    private var currentSnapshot: EngineSnapshot =
        EngineSnapshot.idle(clock())

    @Volatile
    private var currentEffects: List<EngineEffect> = emptyList()

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun effectCount(): Int = currentEffects.size

    override fun effect(index: Int): EngineEffect? = currentEffects.getOrNull(index)

    override fun browseResult(index: Int): EngineCatalogItem? = when {
        index in 0 until currentSnapshot.browseResultsCount -> EngineCatalogItem(
            mediaId = "browse-$index",
            title = "Browse result $index",
            artist = "PandaWave",
            artworkUri = "content://pandawave/catalog/browse-$index",
            sourceUri = "content://pandawave/audio/browse-$index",
            mimeType = DEFAULT_AUDIO_MIME_TYPE,
            itemType = EngineCatalogItem.TYPE_ALBUM
        )

        else -> null
    }

    override fun searchResult(index: Int): EngineCatalogItem? = when {
        index in 0 until currentSnapshot.searchResultsCount -> EngineCatalogItem(
            mediaId = "search-$index",
            title = "Search result $index",
            artist = "PandaWave",
            album = "Canopy Sessions",
            artworkUri = "content://pandawave/catalog/search-$index",
            sourceUri = "content://pandawave/audio/search-$index",
            mimeType = DEFAULT_AUDIO_MIME_TYPE,
            itemType = EngineCatalogItem.TYPE_TRACK
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
        currentEffects = effectsFor(command)

        return EngineDispatchResult(
            snapshot = nextSnapshot,
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            ),
            effects = currentEffects
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        val nextSnapshot = FakePandaEngineReducer.reducePlatformEvent(
            current = currentSnapshot,
            event = event,
            nowMillis = clock()
        )
        currentSnapshot = nextSnapshot
        currentEffects = emptyList()

        return EngineDispatchResult(
            snapshot = nextSnapshot,
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                message = event.type
            ),
            effects = currentEffects
        )
    }

    private fun effectsFor(command: EngineCommand): List<EngineEffect> = when (command.type) {
        EngineCommand.TYPE_PLAY -> listOf(
            EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
            EngineEffect(type = EngineEffect.TYPE_PLAY)
        )

        EngineCommand.TYPE_PAUSE -> listOf(EngineEffect(type = EngineEffect.TYPE_PAUSE))

        EngineCommand.TYPE_SEEK -> listOf(
            EngineEffect(
                type = EngineEffect.TYPE_SEEK,
                positionMillis = EngineCommandPayloads.parseSeekPositionMillis(command.payload)
            )
        )

        EngineCommand.TYPE_SET_SPEED -> listOf(
            EngineEffect(
                type = EngineEffect.TYPE_SET_SPEED,
                speed = EngineCommandPayloads.parsePlaybackSpeed(command.payload)
            )
        )

        EngineCommand.TYPE_PLAY_MEDIA_BY_ID -> playMediaEffects(command.payload)

        else -> emptyList()
    }

    private fun playMediaEffects(payload: String?): List<EngineEffect> {
        val mediaId = EngineCommandPayloads.parseMediaId(payload)

        return listOf(
            EngineEffect(
                type = EngineEffect.TYPE_UPDATE_METADATA,
                mediaId = mediaId.takeUnless { value -> value.isBlank() }
            ),
            EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
            EngineEffect(type = EngineEffect.TYPE_PLAY)
        )
    }
}

internal const val DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"
