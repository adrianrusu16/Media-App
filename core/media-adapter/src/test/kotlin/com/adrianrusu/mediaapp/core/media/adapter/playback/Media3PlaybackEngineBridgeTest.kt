package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3PlaybackEngineBridgeTest {
    @Test
    fun bootstrapStartsPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository)

        bridge.bootstrap()

        assertEquals(1, repository.startCount)
    }

    @Test
    fun closeStopsPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository)

        bridge.close()

        assertEquals(1, repository.closeCount)
    }

    @Test
    fun playWhenReadyChangeDispatchesPlaybackIntents() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository)

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(
            listOf(BambooPlaybackIntent.Play, BambooPlaybackIntent.Pause),
            repository.intents
        )
    }

    @Test
    fun playerSkipCommandsDispatchThroughPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository)

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
    fun unrelatedPlayerCommandIsIgnoredByPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository)

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }
}

private class RecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())

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

    override fun close() {
        closeCount += 1
    }
}
