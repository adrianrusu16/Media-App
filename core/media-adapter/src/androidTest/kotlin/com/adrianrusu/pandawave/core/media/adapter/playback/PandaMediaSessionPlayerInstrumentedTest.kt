package com.adrianrusu.pandawave.core.media.adapter.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PandaMediaSessionPlayerInstrumentedTest {
    @Test
    fun transientSuppressionIsPublishedWithoutClearingPlayIntent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val repository = InstrumentedPlaybackRepository()
            val bridge = Media3PlaybackEngineBridge(
                playbackRepository = repository,
                telemetryLogger = TelemetryLogger(TelemetrySink { }, clock = { 1L })
            )
            val model = PandaMediaSessionPlayerModel(
                availableCommands = emptySet(),
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                playbackSuppressionReason =
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                volume = 0.8F,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                playbackSpeed = 1F,
                playlist = listOf(
                    PandaTimelineItem(
                        uid = "track-1",
                        mediaItem = MediaItem.Builder().setMediaId("track-1").build(),
                        durationMs = 60_000L
                    )
                ),
                currentIndex = 0
            )
            val player = PandaMediaSessionPlayer(
                looper = Looper.getMainLooper(),
                playbackEngineBridge = bridge,
                model = { model },
                seekToQueueIndex = { _, _ -> }
            )

            try {
                assertEquals(true, player.playWhenReady)
                assertEquals(
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                    player.playbackSuppressionReason
                )
            } finally {
                player.release()
            }
        }
    }
}

private class InstrumentedPlaybackRepository : BambooPlaybackRepository {
    override val state: StateFlow<BambooPlaybackState> = MutableStateFlow(BambooPlaybackState())

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) = Unit

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable = AutoCloseable { }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit
}
