package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.math.abs

internal class Media3PlayerStateSink(private val player: Player) : BambooMediaSessionStateSink {
    private var lastSeekProjection: SeekProjection? = null

    override fun project(projection: BambooMediaSessionStateProjection) {
        if (player.volume != projection.volume) {
            player.volume = projection.volume
        }

        val hasPlayableSource = projection.mediaItem.localConfiguration != null
        if (!hasPlayableSource) {
            lastSeekProjection = null
            return
        }

        val currentMediaMatchesProjection = player.currentMediaItem.hasSameMediaState(projection.mediaItem)
        if (!currentMediaMatchesProjection) {
            player.setMediaItem(projection.mediaItem, projection.positionMillis)
        } else if (shouldSeekTo(projection)) {
            player.seekTo(projection.positionMillis)
        }
        lastSeekProjection = SeekProjection(
            mediaItem = projection.mediaItem,
            positionMillis = projection.positionMillis
        )

        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }

        if (player.playWhenReady != projection.playWhenReady) {
            player.playWhenReady = projection.playWhenReady
        }
    }

    private fun shouldSeekTo(projection: BambooMediaSessionStateProjection): Boolean {
        val previousProjection = lastSeekProjection ?: return false
        return previousProjection.mediaItem.hasSameMediaState(projection.mediaItem) &&
            previousProjection.positionMillis != projection.positionMillis &&
            abs(player.currentPosition - projection.positionMillis) > MEDIA3_POSITION_DRIFT_THRESHOLD_MILLIS
    }
}

private data class SeekProjection(
    val mediaItem: MediaItem,
    val positionMillis: Long
)

private const val MEDIA3_POSITION_DRIFT_THRESHOLD_MILLIS = 1_000L
