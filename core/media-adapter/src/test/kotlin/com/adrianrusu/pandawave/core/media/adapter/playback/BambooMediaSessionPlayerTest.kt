package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.PandawaveTestUri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class BambooMediaSessionPlayerTest {
    @Test
    fun `stop is intercepted as pause and prepare does not reach exoplayer`() {
        val repository = PlayerRecordingPlaybackRepository()
        val player = RecordingForwardingDelegate()
        val sessionPlayer = BambooMediaSessionPlayer(
            delegate = player.proxy,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger()),
            controlsEnabled = { true }
        )

        sessionPlayer.stop()
        sessionPlayer.prepare()
        sessionPlayer.setMediaItem(MediaItem.Builder().setMediaId("track-1").setUri(PandawaveTestUri).build())

        assertEquals(listOf<BambooPlaybackIntent>(BambooPlaybackIntent.Pause), repository.intents)
        assertFalse(player.calls.contains("stop"))
        assertFalse(player.calls.contains("prepare"))
        assertTrue(player.calls.none { call -> call.startsWith("setMediaItem") })
    }

    @Test
    fun `seek to another media item is ignored while same item seek is dispatched`() {
        val repository = PlayerRecordingPlaybackRepository()
        val player = RecordingForwardingDelegate(currentMediaItemIndex = 0)
        val sessionPlayer = BambooMediaSessionPlayer(
            delegate = player.proxy,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger()),
            controlsEnabled = { true }
        )

        sessionPlayer.seekTo(1, 1_000L)
        sessionPlayer.seekTo(0, 2_000L)

        assertEquals(listOf<BambooPlaybackIntent>(BambooPlaybackIntent.SeekTo(2_000L)), repository.intents)
    }
}

private class RecordingForwardingDelegate(var currentMediaItemIndex: Int = C.INDEX_UNSET) {
    val calls = mutableListOf<String>()
    val proxy: Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java)
    ) { _, method, args ->
        calls += method.name
        when (method.name) {
            "getAvailableCommands" -> Player.Commands.Builder().addAllCommands().build()
            "getCurrentMediaItemIndex" -> currentMediaItemIndex
            "getCurrentMediaItem" -> null
            "getVolume" -> 1F
            "getPlaybackState" -> Player.STATE_IDLE
            "getPlayWhenReady" -> false
            else -> defaultReturn(method.returnType, args)
        }
    } as Player
}

private fun defaultReturn(returnType: Class<*>, args: Array<out Any>?): Any? = when (returnType) {
    java.lang.Boolean.TYPE -> false
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0F
    java.lang.Void.TYPE -> null
    else -> null
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
