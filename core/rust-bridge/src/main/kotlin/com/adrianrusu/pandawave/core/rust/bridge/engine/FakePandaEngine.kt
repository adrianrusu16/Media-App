package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

internal class FakePandaEngine(private val clock: () -> Long = System::currentTimeMillis) : RustEngine {
    @Volatile
    private var currentSnapshot: EngineSnapshot =
        EngineSnapshot.idle(clock())

    @Volatile
    private var currentEffects: List<EngineEffect> = emptyList()

    @Volatile
    private var audioSourceResolver: AudioSourceResolver? = null

    override fun setAudioSourceResolver(resolver: AudioSourceResolver) {
        audioSourceResolver = resolver
    }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun effectCount(): Int = currentEffects.size

    override fun effect(index: Int): EngineEffect? = currentEffects.getOrNull(index)

    override fun browseResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        snapshotPage(offset, limit, currentSnapshot.browseResultsCount, ::browseItem)

    override fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        snapshotPage(offset, limit, currentSnapshot.searchResultsCount, ::searchItem)

    override fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage {
        val snapshot = currentSnapshot
        return EngineHistoryPage(
            generation = snapshot.historyGeneration,
            items = if (snapshot.historyGeneration == generation) {
                snapshotPage(offset, limit, snapshot.historyEntriesCount, ::historyItem)
            } else {
                emptyList()
            }
        )
    }

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        val reducedSnapshot = FakePandaEngineReducer.reduce(
            current = currentSnapshot,
            command = command,
            nowMillis = clock()
        )
        val nextSnapshot = resolvePlaybackSource(command, reducedSnapshot)
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
                type = EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE,
                mediaId = mediaId.takeUnless { value -> value.isBlank() }
            ),
            EngineEffect(
                type = EngineEffect.TYPE_UPDATE_METADATA,
                mediaId = mediaId.takeUnless { value -> value.isBlank() }
            ),
            EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
            EngineEffect(type = EngineEffect.TYPE_PLAY)
        )
    }

    private fun resolvePlaybackSource(command: EngineCommand, snapshot: EngineSnapshot): EngineSnapshot {
        if (command.type != EngineCommand.TYPE_PLAY_MEDIA_BY_ID) return snapshot
        val mediaId = EngineCommandPayloads.parseMediaId(command.payload)
        if (mediaId.isBlank()) return snapshot

        val source = audioSourceResolver?.resolve(mediaId) ?: return snapshot
        return snapshot.copy(
            sourceUri = source.uri,
            mimeType = source.mimeType ?: snapshot.mimeType,
            durationMillis = source.expectedDurationMillis ?: snapshot.durationMillis
        )
    }

    private fun browseItem(index: Int): EngineCatalogItem = EngineCatalogItem(
        mediaId = "browse-$index",
        title = "Browse result $index",
        artist = "PandaWave",
        artworkUri = "content://pandawave/catalog/browse-$index",
        sourceUri = PandaWaveAudioSourceContract.sourceUriForTrack("browse-$index"),
        mimeType = DEFAULT_AUDIO_MIME_TYPE,
        itemType = EngineCatalogItem.TYPE_ALBUM,
        artworkId = "browse-art-$index",
        artworkVersion = "browse-hash-$index"
    )

    private fun searchItem(index: Int): EngineCatalogItem = EngineCatalogItem(
        mediaId = "search-$index",
        title = "Search result $index",
        artist = "PandaWave",
        album = "Canopy Sessions",
        artworkUri = "content://pandawave/catalog/search-$index",
        sourceUri = PandaWaveAudioSourceContract.sourceUriForTrack("search-$index"),
        mimeType = DEFAULT_AUDIO_MIME_TYPE,
        itemType = EngineCatalogItem.TYPE_TRACK,
        artworkId = "search-art-$index",
        artworkVersion = "search-hash-$index"
    )

    private fun historyItem(index: Int): EngineHistoryItem = EngineHistoryItem(
        historyId = "history-$index",
        mediaId = "history-track-$index",
        title = "Played track $index",
        artist = "PandaWave",
        album = "Recently played",
        artworkUri = "content://pandawave/history/history-track-$index",
        playedAtEpochMillis = clock() - index * 60_000L,
        listenedDurationMillis = 180_000L,
        completionRatio = 1F,
        playable = true,
        artworkId = "history-art-$index",
        artworkVersion = "history-hash-$index"
    )

    private fun <T> snapshotPage(offset: Int, limit: Int, count: Int, itemAt: (Int) -> T): List<T> {
        val start = offset.coerceAtLeast(0)
        val endExclusive = (start + limit.coerceIn(0, MAX_FAKE_ENGINE_PAGE_QUERY_SIZE))
            .coerceAtMost(count.coerceAtLeast(0))
        if (start >= endExclusive) return emptyList()
        return (start until endExclusive).map(itemAt)
    }
}

internal const val DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"
private const val MAX_FAKE_ENGINE_PAGE_QUERY_SIZE = 50
