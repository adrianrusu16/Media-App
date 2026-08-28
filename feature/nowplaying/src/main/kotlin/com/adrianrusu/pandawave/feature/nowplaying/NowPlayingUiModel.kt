package com.adrianrusu.pandawave.feature.nowplaying

import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingState

internal data class NowPlayingUiModel(
    val title: String,
    val detailLabel: String,
    val controlsEnabled: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val isDriveRestricted: Boolean,
    val primaryActionLabel: String,
    val primaryControlIcon: NowPlayingPrimaryControlIcon,
    val availabilityLabel: String,
    val volume: NowPlayingVolumeUiModel,
    val queueActionEnabled: Boolean
)

internal enum class NowPlayingPrimaryControlIcon {
    PandaPaw,
    Pause
}

internal data class NowPlayingVolumeUiModel(val value: Float) {
    val fraction: Float
        get() = value / MAX_VOLUME_VALUE

    companion object {
        fun from(value: Float): NowPlayingVolumeUiModel = NowPlayingVolumeUiModel(
            value = value.coerceIn(MIN_VOLUME_VALUE, MAX_VOLUME_VALUE)
        )
    }
}

internal fun NowPlayingState.toNowPlayingUiModel(
    volume: Float,
    playLabel: String,
    pauseLabel: String,
    controlsUnavailableLabel: String,
    playbackErrorLabel: String,
    fallbackTitle: String,
    fallbackDetail: String
): NowPlayingUiModel {
    val controlsEnabled = canDispatchEngineCommands && !hasPlaybackError
    val primaryActionLabel = if (isPlaying) pauseLabel else playLabel

    return NowPlayingUiModel(
        title = title.ifBlank { fallbackTitle },
        detailLabel = artist.ifBlank { fallbackDetail },
        controlsEnabled = controlsEnabled,
        canSkipPrevious = controlsEnabled && controls.skipPrevious.isEnabled,
        canSkipNext = controlsEnabled && controls.skipNext.isEnabled,
        isDriveRestricted = restriction.isRestricted,
        primaryActionLabel = primaryActionLabel,
        primaryControlIcon = if (isPlaying) {
            NowPlayingPrimaryControlIcon.Pause
        } else {
            NowPlayingPrimaryControlIcon.PandaPaw
        },
        availabilityLabel = when {
            hasPlaybackError -> playbackErrorLabel
            controlsEnabled -> primaryActionLabel
            else -> controlsUnavailableLabel
        },
        volume = NowPlayingVolumeUiModel.from(volume),
        queueActionEnabled = queue.canBrowse(
            engineReady = engineConnection.status == BambooEngineConnectionStatus.Ready
        )
    )
}

private const val MIN_VOLUME_VALUE = 0F
private const val MAX_VOLUME_VALUE = 100F
