package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player

internal class Media3PlayerStateSink(private val player: Player) : BambooMediaSessionStateSink {
    override fun project(projection: BambooMediaSessionStateProjection) {
        if (player.volume != projection.volume) {
            player.volume = projection.volume
        }

        val hasPlayableSource = projection.mediaItem.localConfiguration != null
        if (!hasPlayableSource) {
            return
        }

        // Source loads and seeks are owned by engine effects. Mirroring snapshot
        // position here double-loads opaque streams and seeks the player every
        // time interpolated progress drifts.
        if (player.playWhenReady != projection.playWhenReady) {
            player.playWhenReady = projection.playWhenReady
        }
    }
}
