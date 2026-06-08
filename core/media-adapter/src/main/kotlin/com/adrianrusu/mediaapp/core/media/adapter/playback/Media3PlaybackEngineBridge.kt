package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository

/**
 * Projects Media3 playback requests into the shared Bamboo playback source of truth.
 */
class Media3PlaybackEngineBridge(private val playbackRepository: BambooPlaybackRepository) :
    Player.Listener,
    AutoCloseable {
    fun bootstrap() {
        playbackRepository.start()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        playbackRepository.dispatch(
            PlaybackEngineCommandMapper.fromPlayWhenReady(playWhenReady)
        )
    }

    fun dispatchPlayerCommand(playerCommand: Int): Boolean {
        val intent = PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand) ?: return false

        playbackRepository.dispatch(intent)
        return true
    }

    override fun close() {
        playbackRepository.close()
    }
}
