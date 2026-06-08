package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository

/**
 * Projects Media3 playback requests into the shared Bamboo playback source of truth.
 */
class Media3PlaybackEngineBridge(private val playbackRepository: BambooPlaybackRepository) :
    Player.Listener,
    AutoCloseable {
    private var platformProjectionDepth = 0

    fun bootstrap() {
        playbackRepository.start()
    }

    fun projectPlatformPlaybackState(block: () -> Unit) {
        platformProjectionDepth += 1
        try {
            block()
        } finally {
            platformProjectionDepth -= 1
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (platformProjectionDepth > 0) {
            return
        }

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
