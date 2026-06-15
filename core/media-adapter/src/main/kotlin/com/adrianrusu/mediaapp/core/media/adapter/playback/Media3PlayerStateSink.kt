package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal class Media3PlayerStateSink(private val player: Player) : BambooMediaSessionStateSink {
    override fun project(projection: BambooMediaSessionStateProjection) {
        if (!player.currentMediaItem.hasSameMediaState(projection.mediaItem)) {
            player.setMediaItem(projection.mediaItem)
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
        current.mediaMetadata.title == mediaItem.mediaMetadata.title &&
        current.mediaMetadata.artist == mediaItem.mediaMetadata.artist &&
        current.mediaMetadata.albumTitle == mediaItem.mediaMetadata.albumTitle &&
        current.mediaMetadata.durationMs == mediaItem.mediaMetadata.durationMs &&
        current.mediaMetadata.artworkUri == mediaItem.mediaMetadata.artworkUri
} == true
