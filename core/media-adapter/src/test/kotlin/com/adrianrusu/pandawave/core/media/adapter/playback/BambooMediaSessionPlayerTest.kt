package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BambooMediaSessionPlayerTest {
    @Test
    fun `first play publishes engine metadata before exoplayer has a current item`() {
        val playback = BambooPlaybackState(
            mediaId = "track-1",
            title = "Song A",
            artist = "Artist",
            playbackStatus = BambooPlaybackStatus.Recovering
        )
        val model = PandaMediaSessionPlayerState.from(
            playback = playback,
            queue = Media3QueueProjection(),
            exo = PandaExoRuntimeState(),
            artworkUris = PassthroughArtworkUriProjector
        )

        assertEquals("track-1", model.playlist.single().mediaItem.mediaId)
        assertEquals("Song A", model.playlist.single().mediaItem.mediaMetadata.title.toString())
        assertEquals("Artist", model.playlist.single().mediaItem.mediaMetadata.artist.toString())
        assertEquals(androidx.media3.common.Player.STATE_BUFFERING, model.playbackState)
        assertEquals(true, model.playWhenReady)
    }

    @Test
    fun `seek to another queue item dispatches play from context`() {
        val repository = PlayerRecordingPlaybackRepository()
        val queue = Media3QueueProjection()
        queue.replace(
            listOf(
                Media3QueueItem("pw:v1:track:a", "a", "A"),
                Media3QueueItem("pw:v1:track:b", "b", "B")
            ),
            currentIndex = 0
        )
        val items = queue.snapshot()
        BambooMediaLibraryPlaybackSelection.playbackIntent(
            mediaIds = items.map { item -> item.queueItemId },
            startIndex = 1
        )?.let { intent ->
            Media3PlaybackEngineBridge(repository, testTelemetryLogger()).dispatchPlayFromContext(intent)
        }

        assertEquals(
            listOf<BambooPlaybackIntent>(
                BambooPlaybackIntent.PlayFromContext(
                    context = com.adrianrusu.pandawave.core.playback.PandaPlaybackContext.Browse("b"),
                    selectedMediaId = "b",
                    mediaIds = listOf("a", "b")
                )
            ),
            repository.intents
        )
    }
}

private class PlayerRecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())
    val intents = mutableListOf<BambooPlaybackIntent>()
    override val state: StateFlow<BambooPlaybackState> = mutableState
    override fun start() = Unit
    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }
    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listener(state.value)
        return AutoCloseable { }
    }
    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }
    override fun close() = Unit
}

private fun testTelemetryLogger(): TelemetryLogger = TelemetryLogger(sink = TelemetrySink { }, clock = { 42L })
