package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player

internal class Media3PlayerStateSink(private val player: Player, private val onProjected: () -> Unit = {}) :
    BambooMediaSessionStateSink {
    override fun project(projection: BambooMediaSessionStateProjection) {
        if (player.volume != projection.volume) {
            player.volume = projection.volume
        }
        onProjected()
    }
}
