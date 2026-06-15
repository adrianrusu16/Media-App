package com.adrianrusu.mediaapp.feature.nowplaying.data

import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgressAnchor
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRestrictionState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
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
    title = playback.title,
    artist = playback.artist,
    playbackState = playback.playbackStatus.toNowPlayingPlaybackState(),
    engineConnection = playback.engineConnection,
    restriction = playback.restriction.toNowPlayingRestrictionState(),
    updatedAtEpochMillis = playback.updatedAtEpochMillis,
    progressAnchor = BambooPlaybackProgressAnchor.fromPlaybackState(playback)
)

private fun BambooPlaybackStatus.toNowPlayingPlaybackState(): NowPlayingPlaybackState = when (this) {
    BambooPlaybackStatus.Playing -> NowPlayingPlaybackState.Playing
    BambooPlaybackStatus.Paused -> NowPlayingPlaybackState.Paused
    BambooPlaybackStatus.Idle -> NowPlayingPlaybackState.Idle
}

private fun BambooPlaybackRestrictionState.toNowPlayingRestrictionState(): NowPlayingRestrictionState =
    NowPlayingRestrictionState(
        label = label,
        isRestricted = isRestricted
    )
