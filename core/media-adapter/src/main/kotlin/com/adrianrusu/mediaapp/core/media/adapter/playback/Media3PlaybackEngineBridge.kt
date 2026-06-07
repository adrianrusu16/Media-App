package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway

/**
 * Projects Media3 playback requests into the Rust-owned engine boundary.
 */
class Media3PlaybackEngineBridge(private val engine: EngineGateway) : Player.Listener {
    fun bootstrap() {
        engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_BOOTSTRAP,
                payload = null
            )
        )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        engine.dispatch(
            PlaybackEngineCommandMapper.fromPlayWhenReady(playWhenReady)
        )
    }

    fun dispatchPlayerCommand(playerCommand: Int): Boolean {
        val command = PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand) ?: return false

        engine.dispatch(command)
        return true
    }
}
