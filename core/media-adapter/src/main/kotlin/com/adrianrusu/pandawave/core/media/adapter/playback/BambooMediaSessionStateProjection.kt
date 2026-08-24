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
    val positionMillis: Long,
    val volume: Float = 1F,
    val playbackExpiresAtEpochMillis: Long? = null,
    val contentType: String? = null
)

internal fun BambooMediaSessionStateProjection.hasSameMediaSessionState(
    other: BambooMediaSessionStateProjection
): Boolean = playWhenReady == other.playWhenReady &&
    positionMillis == other.positionMillis &&
    volume == other.volume &&
    playbackExpiresAtEpochMillis == other.playbackExpiresAtEpochMillis &&
    contentType == other.contentType &&
    mediaItem.hasSameMediaState(other.mediaItem)

internal fun MediaItem?.hasSameMediaState(mediaItem: MediaItem): Boolean = this?.let { current ->
    current.mediaId == mediaItem.mediaId &&
        current.localConfiguration?.uri == mediaItem.localConfiguration?.uri &&
        current.localConfiguration?.mimeType == mediaItem.localConfiguration?.mimeType &&
        current.mediaMetadata.title == mediaItem.mediaMetadata.title &&
        current.mediaMetadata.artist == mediaItem.mediaMetadata.artist &&
        current.mediaMetadata.albumTitle == mediaItem.mediaMetadata.albumTitle &&
        current.mediaMetadata.durationMs == mediaItem.mediaMetadata.durationMs &&
        current.mediaMetadata.artworkUri == mediaItem.mediaMetadata.artworkUri
} == true

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
        positionMillis = positionMillis.coerceAtLeast(0L),
        volume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME),
        playbackExpiresAtEpochMillis = playbackExpiresAtEpochMillis,
        contentType = mimeType
    )
}

private const val FALLBACK_MEDIA_ID = "pandawave.playback.current"
private const val MIN_VOLUME = 0F
private const val MAX_VOLUME = 1F
