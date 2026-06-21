package com.adrianrusu.mediaapp.feature.nowplaying

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState

internal data class NowPlayingUiModel(
    val title: String,
    val detailLabel: String,
    val controlsEnabled: Boolean,
    val isDriveRestricted: Boolean,
    val primaryActionLabel: String,
    val primaryControlIcon: NowPlayingPrimaryControlIcon,
    val availabilityLabel: String,
    val volume: NowPlayingVolumeUiModel
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
    fallbackTitle: String,
    fallbackDetail: String
): NowPlayingUiModel {
    val controlsEnabled = engineConnection.status == BambooEngineConnectionStatus.Ready
    val primaryActionLabel = if (isPlaying) pauseLabel else playLabel

    return NowPlayingUiModel(
        title = title.ifBlank { fallbackTitle },
        detailLabel = artist.ifBlank { fallbackDetail },
        controlsEnabled = controlsEnabled,
        isDriveRestricted = restriction.isRestricted,
        primaryActionLabel = primaryActionLabel,
        primaryControlIcon = if (isPlaying) {
            NowPlayingPrimaryControlIcon.Pause
        } else {
            NowPlayingPrimaryControlIcon.PandaPaw
        },
        availabilityLabel = if (controlsEnabled) {
            primaryActionLabel
        } else {
            controlsUnavailableLabel
        },
        volume = NowPlayingVolumeUiModel.from(volume)
    )
}

private const val MIN_VOLUME_VALUE = 0F
private const val MAX_VOLUME_VALUE = 100F
