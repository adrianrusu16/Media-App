package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot

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
        NowPlayingPlaybackState.Playing -> "Sample station"
        NowPlayingPlaybackState.Paused -> "Paused"
        NowPlayingPlaybackState.Idle -> "Nothing playing"
    }

private val NowPlayingPlaybackState.fallbackArtist: String
    get() = when (this) {
        NowPlayingPlaybackState.Playing -> "Preview queue"
        NowPlayingPlaybackState.Paused -> "Ready to resume"
        NowPlayingPlaybackState.Idle -> "Ready when you are"
    }

private fun EngineSnapshot.toPlaybackState(): NowPlayingPlaybackState = when (playbackState) {
    EngineSnapshot.PLAYBACK_PLAYING -> NowPlayingPlaybackState.Playing
    EngineSnapshot.PLAYBACK_PAUSED -> NowPlayingPlaybackState.Paused
    else -> NowPlayingPlaybackState.Idle
}
