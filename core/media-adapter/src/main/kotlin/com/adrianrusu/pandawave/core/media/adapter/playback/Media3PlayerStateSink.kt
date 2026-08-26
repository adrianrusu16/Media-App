package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.C
import androidx.media3.common.Player

internal class Media3PlayerStateSink(private val player: Player) : BambooMediaSessionStateSink {
    override fun project(projection: BambooMediaSessionStateProjection) {
        if (player.volume != projection.volume) {
            player.volume = projection.volume
        }

        applyMetadata(projection)

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

    private fun applyMetadata(projection: BambooMediaSessionStateProjection) {
        val current = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val metadata = projection.mediaItem.mediaMetadata
        val sameItem = current.mediaId == projection.mediaItem.mediaId &&
            current.mediaMetadata.title == metadata.title &&
            current.mediaMetadata.artist == metadata.artist &&
            current.mediaMetadata.albumTitle == metadata.albumTitle &&
            current.mediaMetadata.durationMs == metadata.durationMs &&
            current.mediaMetadata.artworkUri == metadata.artworkUri
        if (sameItem) return
        player.replaceMediaItem(
            index,
            current.buildUpon()
                .setMediaId(projection.mediaItem.mediaId)
                .setMediaMetadata(metadata)
                .build()
        )
    }
}
