package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntentNames
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackTelemetryAttributes
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Media3PlaybackEngineBridgeTest {
    @Test
    fun `bootstrap starts playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.bootstrap()

        assertEquals(1, repository.startCount)
    }

    @Test
    fun `bootstrap subscribes executor to repository effects`() {
        val repository = RecordingPlaybackRepository()
        val executor = RecordingEffectExecutor()
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger(),
            effectExecutor = executor
        )
        val effects = listOf(EngineEffect(type = EngineEffect.TYPE_PLAY))

        bridge.bootstrap()
        repository.emitEffects(effects)

        assertEquals(listOf(effects), executor.effects)
    }

    @Test
    fun `close stops playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.close()

        assertEquals(1, repository.closeCount)
    }

    @Test
    fun `play when ready change dispatches playback intents`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(
            listOf(BambooPlaybackIntent.Play, BambooPlaybackIntent.Pause),
            repository.intents
        )
    }

    @Test
    fun `projected platform playback state does not dispatch playback intent`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.projectPlatformPlaybackState {
            bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }

    @Test
    fun `player skip commands dispatch through playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)

        assertEquals(
            listOf(
                BambooPlaybackIntent.SkipPrevious,
                BambooPlaybackIntent.SkipNext
            ),
            repository.intents
        )
    }

    @Test
    fun `unrelated player command is ignored by playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }

    @Test
    fun `seek dispatches through playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchSeek(12_345L)

        assertEquals(
            listOf<BambooPlaybackIntent>(BambooPlaybackIntent.SeekTo(positionMillis = 12_345L)),
            repository.intents
        )
    }

    @Test
    fun `playback speed dispatches through playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchPlaybackSpeed(1.25F)

        assertEquals(
            listOf<BambooPlaybackIntent>(BambooPlaybackIntent.SetSpeed(speed = 1.25F)),
            repository.intents
        )
    }

    @Test
    fun `catalog browse and search dispatch through playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchCatalogBrowse(LibraryItems.ENGINE_ROOT_PARENT_ID)
        bridge.dispatchCatalogSearch("Rust")
        bridge.dispatchCatalogPlay("track-1")

        assertEquals(
            listOf<BambooPlaybackIntent>(
                BambooPlaybackIntent.BrowseCatalog(parentId = LibraryItems.ENGINE_ROOT_PARENT_ID),
                BambooPlaybackIntent.SearchCatalog(query = "Rust"),
                BambooPlaybackIntent.PlayMedia(mediaId = "track-1")
            ),
            repository.intents
        )
    }

    @Test
    fun `telemetry records dispatched ignored and projected media3 commands`() {
        val telemetrySink = RecordingTelemetrySink()
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger(telemetrySink))

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.projectPlatformPlaybackState {
            bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(
            listOf(
                Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_RECEIVED,
                Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_IGNORED,
                Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
                Media3PlaybackTelemetryEvents.PLAYER_COMMAND_IGNORED
            ),
            telemetrySink.events.map { it.name }
        )
        assertEquals("true", telemetrySink.events[0].attributes[Media3PlaybackTelemetryAttributes.PLAY_WHEN_READY])
        assertEquals(
            Media3PlaybackTelemetryValues.PLATFORM_PROJECTION,
            telemetrySink.events[1].attributes[Media3PlaybackTelemetryAttributes.SOURCE]
        )
        assertEquals(
            BambooPlaybackIntentNames.SKIP_NEXT,
            telemetrySink.events[2].attributes[BambooPlaybackTelemetryAttributes.INTENT]
        )
        assertEquals(
            Player.COMMAND_SEEK_FORWARD.toString(),
            telemetrySink.events[3].attributes[Media3PlaybackTelemetryAttributes.PLAYER_COMMAND]
        )
    }
}

private class RecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())
    private val effectListeners = mutableSetOf<(List<EngineEffect>) -> Unit>()

    var startCount = 0
    var closeCount = 0
    val intents = mutableListOf<BambooPlaybackIntent>()

    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() {
        startCount += 1
    }

    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listener(state.value)
        return AutoCloseable { }
    }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable {
        effectListeners += listener

        return AutoCloseable {
            effectListeners -= listener
        }
    }

    fun emitEffects(effects: List<EngineEffect>) {
        effectListeners.toList().forEach { listener ->
            listener(effects)
        }
    }

    override fun close() {
        closeCount += 1
    }
}

private class RecordingEffectExecutor : BambooPlaybackEffectExecutor {
    val effects = mutableListOf<List<EngineEffect>>()

    override fun execute(effects: List<EngineEffect>) {
        this.effects += effects
    }
}

private fun testTelemetryLogger(sink: TelemetrySink = TelemetrySink { }): TelemetryLogger = TelemetryLogger(
    sink = sink,
    clock = { 42L }
)

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
