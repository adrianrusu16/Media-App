package com.adrianrusu.pandawave.feature.nowplaying.domain

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls
import com.adrianrusu.pandawave.core.playback.BambooPlaybackProgress
import com.adrianrusu.pandawave.core.playback.BambooPlaybackProgressAnchor
import com.adrianrusu.pandawave.core.playback.BambooPlaybackProgressProjector

data class NowPlayingState(
    val mediaId: String? = null,
    val artworkUri: String? = null,
    val title: String = "",
    val artist: String = "",
    val playbackState: NowPlayingPlaybackState = NowPlayingPlaybackState.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: NowPlayingRestrictionState = NowPlayingRestrictionState.Unavailable,
    val isParked: Boolean = false,
    val isUxUnrestricted: Boolean = false,
    val ambientModeEnabled: Boolean = false,
    val ambientTimeoutSeconds: Int = 15,
    val visualizerPermissionState: VisualizerPermissionState = VisualizerPermissionState.Unknown,
    val hasPlaybackError: Boolean = false,
    val controls: BambooPlaybackControls = BambooPlaybackControls.default(),
    val updatedAtEpochMillis: Long = 0L,
    val progressAnchor: BambooPlaybackProgressAnchor = BambooPlaybackProgressAnchor(),
    val volume: Float = 1F
) {
    val isPlaying: Boolean
        get() = playbackState == NowPlayingPlaybackState.Playing

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready

    fun progressAt(nowMillis: Long): BambooPlaybackProgress = BambooPlaybackProgressProjector.fromAnchor(
        anchor = progressAnchor,
        nowMillis = nowMillis
    )
}
