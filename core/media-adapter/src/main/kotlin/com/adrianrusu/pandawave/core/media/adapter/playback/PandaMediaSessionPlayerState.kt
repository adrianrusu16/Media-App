package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus

internal data class PandaTimelineItem(val uid: String, val mediaItem: MediaItem, val durationMs: Long)

internal data class PandaExoRuntimeState(
    val playbackState: Int = Player.STATE_IDLE,
    val currentMediaId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = C.TIME_UNSET,
    val bufferedPositionMs: Long = 0L
)

internal data class PandaMediaSessionPlayerModel(
    val availableCommands: Set<Int>,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val volume: Float,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val playbackSpeed: Float,
    val playlist: List<PandaTimelineItem>,
    val currentIndex: Int,
    val errorType: String? = null
)

internal object PandaMediaSessionPlayerState {
    fun from(
        playback: BambooPlaybackState,
        queue: Media3QueueProjection,
        exo: PandaExoRuntimeState,
        artworkUris: ArtworkUriProjector
    ): PandaMediaSessionPlayerModel {
        queue.alignToMediaId(playback.mediaId)
        val timeline = timeline(playback, queue, artworkUris)
        val currentIndex = timeline.indexOfCurrent(playback.mediaId, queue.currentIndex)
        val playWhenReady = playback.playWhenReady
        return PandaMediaSessionPlayerModel(
            availableCommands = BambooMediaSessionCommandPolicy.availableCommandTypes(
                controls = playback.controls,
                hasSeekableTimeline = queue.hasSeekableTimeline()
            ),
            playWhenReady = playWhenReady,
            playbackState = playbackState(playback, exo, timeline.isNotEmpty()),
            volume = playback.volume.coerceIn(MIN_VOLUME, MAX_VOLUME),
            positionMs = positionMs(playback, exo),
            bufferedPositionMs = exo.bufferedPositionMs.coerceAtLeast(0L),
            playbackSpeed = playback.playbackSpeed.coerceAtLeast(0F),
            playlist = timeline,
            currentIndex = currentIndex,
            errorType = playback.errorType.takeIf { playback.hasError && it != "none" }
        )
    }

    private fun timeline(
        playback: BambooPlaybackState,
        queue: Media3QueueProjection,
        artworkUris: ArtworkUriProjector
    ): List<PandaTimelineItem> {
        val queued = queue.snapshot().map { item -> item.toTimelineItem(artworkUris) }
        if (queued.isNotEmpty()) {
            return queued
        }
        val mediaId = playback.mediaId?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
        return listOf(
            PandaTimelineItem(
                uid = mediaId,
                mediaItem = currentMediaItem(playback, mediaId, artworkUris),
                durationMs = playback.durationMillis ?: C.TIME_UNSET
            )
        )
    }

    private fun playbackState(playback: BambooPlaybackState, exo: PandaExoRuntimeState, hasTimeline: Boolean): Int {
        if (playback.playbackStatus == BambooPlaybackStatus.Ended) return Player.STATE_ENDED
        if (!hasTimeline && playback.mediaId.isNullOrBlank()) return Player.STATE_IDLE
        val currentId = playback.mediaId
        val exoReady = exo.playbackState == Player.STATE_READY &&
            (exo.currentMediaId == null || exo.currentMediaId == currentId)
        return if (exoReady) Player.STATE_READY else Player.STATE_BUFFERING
    }

    private fun positionMs(playback: BambooPlaybackState, exo: PandaExoRuntimeState): Long {
        val enginePosition = playback.positionMillis.coerceAtLeast(0L)
        val currentId = playback.mediaId
        return if (exo.currentMediaId != null && exo.currentMediaId == currentId && exo.positionMs >= 0L) {
            exo.positionMs
        } else {
            enginePosition
        }
    }
}

private fun Media3QueueItem.toTimelineItem(artworkUris: ArtworkUriProjector): PandaTimelineItem = PandaTimelineItem(
    uid = queueItemId,
    mediaItem = MediaItem.Builder()
        .setMediaId(queueItemId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDurationMs(durationMs)
                .setArtworkUri(artworkUris.project(artworkUri))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build(),
    durationMs = durationMs ?: C.TIME_UNSET
)

private fun currentMediaItem(
    playback: BambooPlaybackState,
    mediaId: String,
    artworkUris: ArtworkUriProjector
): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(playback.title.takeIf(String::isNotBlank) ?: mediaId)
            .setArtist(playback.artist.takeIf(String::isNotBlank))
            .setAlbumTitle(playback.album)
            .setDurationMs(playback.durationMillis)
            .setArtworkUri(artworkUris.project(playback.artworkUri, playback.artworkId, playback.artworkVersion))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()

private fun List<PandaTimelineItem>.indexOfCurrent(mediaId: String?, fallback: Int): Int {
    if (isEmpty()) return 0
    val id = mediaId?.trim()?.takeIf(String::isNotBlank) ?: return fallback.coerceIn(0, lastIndex)
    val match = indexOfFirst { item -> item.uid == id || item.mediaItem.mediaId == id }
    return if (match >= 0) match else fallback.coerceIn(0, lastIndex)
}

private const val MIN_VOLUME = 0F
private const val MAX_VOLUME = 1F
