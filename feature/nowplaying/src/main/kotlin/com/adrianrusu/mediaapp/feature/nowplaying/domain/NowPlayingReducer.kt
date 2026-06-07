package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.ui.playback.PlaybackDisplayText

internal object NowPlayingReducer {
    fun reduce(state: NowPlayingState, snapshot: EngineSnapshot): NowPlayingState {
        val playbackState = snapshot.toPlaybackState()

        return state.copy(
            mediaId = snapshot.mediaId,
            title = snapshot.title ?: playbackState.fallbackTitle,
            artist = snapshot.artist ?: playbackState.fallbackArtist,
            playbackState = playbackState,
            updatedAtEpochMillis = snapshot.updatedAtEpochMillis
        )
    }

    fun reduce(state: NowPlayingState, restriction: NowPlayingRestrictionState): NowPlayingState =
        state.copy(restriction = restriction)
}

private val NowPlayingPlaybackState.fallbackTitle: String
    get() = when (this) {
        NowPlayingPlaybackState.Playing -> PlaybackDisplayText.FALLBACK_PLAYING_TITLE
        NowPlayingPlaybackState.Paused -> PlaybackDisplayText.FALLBACK_PAUSED_TITLE
        NowPlayingPlaybackState.Idle -> PlaybackDisplayText.FALLBACK_IDLE_TITLE
    }

private val NowPlayingPlaybackState.fallbackArtist: String
    get() = when (this) {
        NowPlayingPlaybackState.Playing -> PlaybackDisplayText.FALLBACK_PLAYING_SUBTITLE
        NowPlayingPlaybackState.Paused -> PlaybackDisplayText.FALLBACK_PAUSED_SUBTITLE
        NowPlayingPlaybackState.Idle -> PlaybackDisplayText.FALLBACK_IDLE_SUBTITLE
    }

private fun EngineSnapshot.toPlaybackState(): NowPlayingPlaybackState = when (playbackState) {
    EngineSnapshot.PLAYBACK_PLAYING -> NowPlayingPlaybackState.Playing
    EngineSnapshot.PLAYBACK_PAUSED -> NowPlayingPlaybackState.Paused
    else -> NowPlayingPlaybackState.Idle
}
