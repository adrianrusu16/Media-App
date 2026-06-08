package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus

internal data class BambooMediaSessionStateProjection(val mediaItem: MediaItem, val playWhenReady: Boolean)

internal fun BambooPlaybackState.toMediaSessionStateProjection(): BambooMediaSessionStateProjection =
    BambooMediaSessionStateProjection(
        mediaItem = MediaItem.Builder()
            .setMediaId(mediaId ?: FALLBACK_MEDIA_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build(),
        playWhenReady = playbackStatus == BambooPlaybackStatus.Playing
    )

private const val FALLBACK_MEDIA_ID = "pandawave.playback.current"
