package com.adrianrusu.pandawave.feature.nowplaying.data

import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackProgressAnchor
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRestrictionState
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryNowPlayingRepository(private val playbackRepository: BambooPlaybackRepository) :
    NowPlayingRepository {
    private val mutableState = MutableStateFlow(NowPlayingState())
    private var playbackSubscription: AutoCloseable? = null

    override val state: StateFlow<NowPlayingState> = mutableState.asStateFlow()

    override fun start() {
        playbackSubscription?.close()
        playbackSubscription = playbackRepository.observe { playback ->
            mutableState.update { current ->
                current.withPlaybackState(playback)
            }
        }
        playbackRepository.start()
    }

    override fun dispatch(intent: NowPlayingIntent) {
        when (intent) {
            NowPlayingIntent.Refresh -> playbackRepository.dispatch(BambooPlaybackIntent.Refresh)
            NowPlayingIntent.TogglePlayback -> playbackRepository.dispatch(BambooPlaybackIntent.TogglePlayback)
            NowPlayingIntent.SkipPrevious -> playbackRepository.dispatch(BambooPlaybackIntent.SkipPrevious)
            NowPlayingIntent.SkipNext -> playbackRepository.dispatch(BambooPlaybackIntent.SkipNext)
            is NowPlayingIntent.SetVolume -> playbackRepository.dispatch(
                BambooPlaybackIntent.SetVolume(intent.volume)
            )
        }
    }

    override fun close() {
        playbackSubscription?.close()
        playbackSubscription = null
        playbackRepository.close()
    }
}

internal fun NowPlayingState.withPlaybackState(playback: BambooPlaybackState): NowPlayingState = copy(
    mediaId = playback.mediaId,
    artworkUri = playback.artworkUri,
    title = playback.title,
    artist = playback.artist,
    playbackState = playback.playbackStatus.toNowPlayingPlaybackState(),
    engineConnection = playback.engineConnection,
    restriction = playback.restriction.toNowPlayingRestrictionState(),
    isParked = playback.vehicleSafety.isParked,
    isUxUnrestricted = playback.vehicleSafety.isUxUnrestricted,
    hasPlaybackError = playback.hasError,
    controls = playback.controls,
    updatedAtEpochMillis = playback.updatedAtEpochMillis,
    volume = playback.volume,
    progressAnchor = BambooPlaybackProgressAnchor.fromPlaybackState(playback)
)

private fun BambooPlaybackStatus.toNowPlayingPlaybackState(): NowPlayingPlaybackState = when (this) {
    BambooPlaybackStatus.Playing -> NowPlayingPlaybackState.Playing
    BambooPlaybackStatus.Paused -> NowPlayingPlaybackState.Paused
    BambooPlaybackStatus.Recovering -> NowPlayingPlaybackState.Playing
    BambooPlaybackStatus.Ended -> NowPlayingPlaybackState.Idle
    BambooPlaybackStatus.Idle -> NowPlayingPlaybackState.Idle
}

private fun BambooPlaybackRestrictionState.toNowPlayingRestrictionState(): NowPlayingRestrictionState =
    NowPlayingRestrictionState(
        isRestricted = isRestricted
    )
