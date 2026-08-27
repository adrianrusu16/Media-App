package com.adrianrusu.pandawave.core.media.adapter.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Media3 Player façade whose public state is PandaEngine, not ExoPlayer.
 *
 * ExoPlayer remains a playback sink. First-play metadata is published as soon
 * as PandaEngine has a current item, even while the sink is still preparing.
 */
@UnstableApi
internal class PandaMediaSessionPlayer(
    looper: Looper,
    private val playbackEngineBridge: Media3PlaybackEngineBridge,
    private val model: () -> PandaMediaSessionPlayerModel,
    private val seekToQueueIndex: (Int, Long) -> Unit
) : SimpleBasePlayer(looper) {
    fun invalidatePlaybackState() {
        invalidateState()
    }

    override fun getState(): State {
        val snapshot = model()
        val commands = Player.Commands.Builder().apply {
            snapshot.availableCommands.forEach { command -> add(command) }
        }.build()
        val playlist = snapshot.playlist.map { item ->
            MediaItemData.Builder(item.uid)
                .setMediaItem(item.mediaItem)
                .setDurationUs(durationUs(item.durationMs))
                .setIsSeekable(true)
                .build()
        }
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(snapshot.playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(snapshot.playbackState)
            .setVolume(snapshot.volume)
            .setPlaybackParameters(PlaybackParameters(snapshot.playbackSpeed))
        if (playlist.isNotEmpty()) {
            builder.setPlaylist(ImmutableList.copyOf(playlist))
                .setCurrentMediaItemIndex(snapshot.currentIndex)
                .setContentPositionMs(snapshot.positionMs)
        }
        snapshot.errorType?.let { type ->
            PandaMediaSessionErrorMapper.playbackException(type)?.let(builder::setPlayerError)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playbackEngineBridge.dispatchPlayWhenReady(playWhenReady)
        return completed()
    }

    override fun handlePrepare(): ListenableFuture<*> = completed()

    override fun handleRelease(): ListenableFuture<*> = completed()

    override fun handleStop(): ListenableFuture<*> {
        playbackEngineBridge.dispatchPlayWhenReady(playWhenReady = false)
        return completed()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        playbackEngineBridge.dispatchVolume(volume)
        return completed()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        playbackEngineBridge.dispatchPlaybackSpeed(playbackParameters.speed)
        return completed()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> = completed()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_PREVIOUS)

            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_NEXT)

            COMMAND_SEEK_TO_MEDIA_ITEM -> seekToQueueIndex(mediaItemIndex, positionMs)

            else -> playbackEngineBridge.dispatchSeek(positionMs)
        }
        return completed()
    }
}

private fun durationUs(durationMs: Long): Long = when {
    durationMs == C.TIME_UNSET || durationMs < 0L -> C.TIME_UNSET
    else -> durationMs * 1_000L
}

private fun completed(): ListenableFuture<*> = Futures.immediateVoidFuture()

@UnstableApi
internal object PandaMediaSessionErrorMapper {
    fun playbackException(errorType: String): PlaybackException? {
        val code = errorCode(errorType) ?: return null
        return PlaybackException(errorType, null, code)
    }

    fun errorCode(errorType: String): Int? = when (errorType) {
        "authentication" -> PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED
        "network" -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        "not_found" -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        "player", "unknown" -> PlaybackException.ERROR_CODE_UNSPECIFIED
        else -> null
    }
}
