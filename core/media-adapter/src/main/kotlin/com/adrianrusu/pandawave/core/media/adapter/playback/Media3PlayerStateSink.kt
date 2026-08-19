package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.math.abs

internal class Media3PlayerStateSink(private val player: Player) : BambooMediaSessionStateSink {
    override fun project(projection: BambooMediaSessionStateProjection) {
        if (projection.mediaItem.localConfiguration == null) {
            return
        }

        if (!player.currentMediaItem.hasSameMediaState(projection.mediaItem)) {
            player.setMediaItem(projection.mediaItem, projection.positionMillis)
        } else if (abs(player.currentPosition - projection.positionMillis) > MEDIA3_POSITION_DRIFT_THRESHOLD_MILLIS) {
            player.seekTo(projection.positionMillis)
        }

        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }

        if (player.playWhenReady != projection.playWhenReady) {
            player.playWhenReady = projection.playWhenReady
        }
    }
}

private fun MediaItem?.hasSameMediaState(mediaItem: MediaItem): Boolean = this?.let { current ->
    current.mediaId == mediaItem.mediaId &&
        current.localConfiguration?.uri == mediaItem.localConfiguration?.uri &&
        current.localConfiguration?.mimeType == mediaItem.localConfiguration?.mimeType &&
        current.mediaMetadata.title == mediaItem.mediaMetadata.title &&
        current.mediaMetadata.artist == mediaItem.mediaMetadata.artist &&
        current.mediaMetadata.albumTitle == mediaItem.mediaMetadata.albumTitle &&
        current.mediaMetadata.durationMs == mediaItem.mediaMetadata.durationMs &&
        current.mediaMetadata.artworkUri == mediaItem.mediaMetadata.artworkUri
} == true

private const val MEDIA3_POSITION_DRIFT_THRESHOLD_MILLIS = 1_000L
