package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand

internal object PlaybackEngineCommandMapper {
    fun fromPlayWhenReady(playWhenReady: Boolean): EngineCommand =
        EngineCommand(
            type = if (playWhenReady) {
                EngineCommand.TYPE_PLAY
            } else {
                EngineCommand.TYPE_PAUSE
            },
            payload = null,
        )
}
