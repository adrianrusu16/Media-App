package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus

internal data class BambooMediaSessionStateProjection(
    val mediaItem: MediaItem,
    val playWhenReady: Boolean,
    val positionMillis: Long
)

internal fun interface BambooUriParser {
    fun parse(value: String): Uri?
}

private val DefaultUriParser = BambooUriParser { value -> value.toUri() }

internal fun BambooPlaybackState.toMediaSessionStateProjection(
    uriParser: BambooUriParser = DefaultUriParser
): BambooMediaSessionStateProjection {
    val mediaItemBuilder = MediaItem.Builder()
        .setMediaId(mediaId ?: FALLBACK_MEDIA_ID)

    sourceUri?.let { value -> mediaItemBuilder.setUri(uriParser.parse(value)) }
    mimeType?.let(mediaItemBuilder::setMimeType)

    return BambooMediaSessionStateProjection(
        mediaItem = mediaItemBuilder
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setDurationMs(durationMillis)
                    .setArtworkUri(artworkUri?.let(uriParser::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build(),
        playWhenReady = playbackStatus == BambooPlaybackStatus.Playing,
        positionMillis = positionMillis.coerceAtLeast(0L)
    )
}

private const val FALLBACK_MEDIA_ID = "pandawave.playback.current"
