package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntentNames
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackTelemetryAttributes
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusChange
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Media3PlaybackEngineBridgeTest {
    @Test
    fun `audio focus changes dispatch typed engine events and structured telemetry`() {
        val repository = RecordingPlaybackRepository()
        val telemetrySink = RecordingTelemetrySink()
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger(telemetrySink),
        )

        bridge.dispatchAudioFocusChange(BambooAudioFocusChange.LossTransient)

        val event = repository.intents.single() as BambooPlaybackIntent.PlatformEvent
        assertEquals(EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED, event.type)
        assertEquals(
            """{"version":1,"focus_change":"loss_transient"}""",
            event.payload,
        )
        assertEquals(
            "loss_transient",
            telemetrySink.events.single {
                it.name == Media3PlaybackTelemetryEvents.AUDIO_FOCUS_CHANGED
            }.attributes[Media3PlaybackTelemetryAttributes.FOCUS_CHANGE],
        )
    }

    @Test
    fun `playing schedules recurring safe position checkpoints and pausing sends a final checkpoint`() {
        val repository = RecordingPlaybackRepository()
        val telemetrySink = RecordingTelemetrySink()
        val scheduler = RecordingPlaybackCheckpointScheduler()
        var metrics = PlaybackCompletionMetrics(positionMillis = 18_300L, durationMillis = 120_000L)
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger(telemetrySink),
            playbackMetricsProvider = PlaybackCompletionMetricsProvider { metrics },
            playbackInstanceIdProvider = { 42L },
            checkpointScheduler = scheduler,
            checkpointIntervalMillis = 10_000L,
        )

        bridge.onIsPlayingChanged(true)

        assertEquals(listOf(10_000L), scheduler.pendingDelays())
        scheduler.runNext()

        val periodicEvent = repository.intents
            .filterIsInstance<BambooPlaybackIntent.PlatformEvent>()
            .single()
        assertEquals(EnginePlatformEvent.TYPE_PLAYBACK_POSITION_CHECKPOINT, periodicEvent.type)
        assertEquals(
            """{"version":1,"playback_instance_id":42,"position_ms":18300}""",
            periodicEvent.payload,
        )
        assertEquals(listOf(10_000L), scheduler.pendingDelays())

        metrics = metrics.copy(positionMillis = 19_100L)
        bridge.onIsPlayingChanged(false)

        val finalEvent = repository.intents
            .filterIsInstance<BambooPlaybackIntent.PlatformEvent>()
            .last()
        assertEquals(
            """{"version":1,"playback_instance_id":42,"position_ms":19100}""",
            finalEvent.payload,
        )
        assertTrue(scheduler.pendingDelays().isEmpty())
        assertEquals(
            listOf("periodic", "paused"),
            telemetrySink.events
                .filter { it.name == Media3PlaybackTelemetryEvents.POSITION_CHECKPOINT_DISPATCHED }
                .map { it.attributes.getValue(Media3PlaybackTelemetryAttributes.TRIGGER) },
        )
    }

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
    fun `volume changes dispatch through playback repository`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchVolume(1.5F)
        bridge.onVolumeChanged(0.25F)

        assertEquals(
            listOf<BambooPlaybackIntent>(
                BambooPlaybackIntent.SetVolume(1F),
                BambooPlaybackIntent.SetVolume(0.25F)
            ),
            repository.intents
        )
    }

    @Test
    fun `projected volume changes do not dispatch playback intents`() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.projectPlatformPlaybackState {
            bridge.onVolumeChanged(0.25F)
        }

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
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
        assertEquals(
            setOf(TelemetryModule.Media3),
            telemetrySink.events.mapTo(mutableSetOf()) { it.module }
        )
    }

    @Test
    fun `catalog telemetry records shape without user supplied identifiers`() {
        val telemetrySink = RecordingTelemetrySink()
        val bridge = Media3PlaybackEngineBridge(
            RecordingPlaybackRepository(),
            testTelemetryLogger(telemetrySink)
        )

        bridge.dispatchCatalogBrowse("private-parent")
        bridge.dispatchCatalogSearch("secret query")
        bridge.dispatchCatalogPlay("private-media-id")

        assertEquals(
            "true",
            telemetrySink.events[0].attributes[Media3PlaybackTelemetryAttributes.CATALOG_PARENT_ID_PRESENT]
        )
        assertEquals(
            "12",
            telemetrySink.events[1].attributes[Media3PlaybackTelemetryAttributes.CATALOG_QUERY_LENGTH]
        )
        assertEquals(
            "true",
            telemetrySink.events[2].attributes[Media3PlaybackTelemetryAttributes.MEDIA_ID_PRESENT]
        )
        assertEquals(
            emptySet<String>(),
            telemetrySink.events
                .flatMap { it.attributes.values }
                .intersect(setOf("private-parent", "secret query", "private-media-id"))
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

private class RecordingPlaybackCheckpointScheduler : PlaybackCheckpointScheduler {
    private data class Task(
        val delayMillis: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMillis: Long, action: () -> Unit): AutoCloseable {
        val task = Task(delayMillis, action)
        tasks += task
        return AutoCloseable { task.cancelled = true }
    }

    fun pendingDelays(): List<Long> = tasks.filterNot(Task::cancelled).map(Task::delayMillis)

    fun runNext() {
        val task = tasks.first { !it.cancelled }
        task.cancelled = true
        task.action()
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
