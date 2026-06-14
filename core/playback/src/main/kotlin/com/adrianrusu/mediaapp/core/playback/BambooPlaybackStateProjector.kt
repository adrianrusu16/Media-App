package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText

internal object BambooPlaybackStateProjector {
    fun fromEngineSnapshot(current: BambooPlaybackState, snapshot: EngineSnapshot): BambooPlaybackState = current.copy(
        mediaId = snapshot.mediaId,
        title = snapshot.title ?: titleFor(snapshot.playbackState),
        artist = snapshot.artist ?: artistFor(snapshot.playbackState),
        playbackStatus = snapshot.playbackState.toPlaybackStatus(),
        updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
        positionMillis = snapshot.positionMillis,
        playbackSpeed = snapshot.playbackSpeed,
        hasActiveSession = snapshot.hasActiveSession,
        hasError = snapshot.hasError,
        errorType = snapshot.errorType,
        searchResultsCount = snapshot.searchResultsCount,
        browseResultsCount = snapshot.browseResultsCount,
        isBusy = snapshot.isBusy,
        canDispatch = snapshot.canDispatch,
        controls = snapshot.controls.toPlaybackControls()
    )

    fun fromEngineEvent(current: BambooPlaybackState, event: EngineEvent): BambooPlaybackState = current.copy(
        engineConnection = event.toConnectionUiState(current = current.engineConnection)
    )

    fun fromUxRestrictions(current: BambooPlaybackState, restrictions: AutomotiveUxRestrictions): BambooPlaybackState =
        current.copy(
            restriction = restrictions.toPlaybackRestrictionState()
        )

    private fun String.toPlaybackStatus(): BambooPlaybackStatus = when (this) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackStatus.Playing
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackStatus.Paused
        else -> BambooPlaybackStatus.Idle
    }

    private fun titleFor(playbackState: String): String = when (playbackState) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_TITLE
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_TITLE
        else -> BambooPlaybackText.FALLBACK_IDLE_TITLE
    }

    private fun artistFor(playbackState: String): String = when (playbackState) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_SUBTITLE
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_SUBTITLE
        else -> BambooPlaybackText.FALLBACK_IDLE_SUBTITLE
    }

    private fun EngineEvent.toConnectionUiState(current: BambooEngineConnectionUiState): BambooEngineConnectionUiState =
        when (type) {
            EngineEvent.TYPE_COMMAND_APPLIED,
            EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
            EngineEvent.TYPE_LISTENER_REGISTERED,
            EngineEvent.TYPE_SERVICE_CONNECTED -> BambooEngineConnectionUiState.Ready

            EngineEvent.TYPE_COMMAND_QUEUED,
            EngineEvent.TYPE_PLATFORM_EVENT_QUEUED -> BambooEngineConnectionUiState.Connecting

            EngineEvent.TYPE_SERVICE_BINDING_DIED -> BambooEngineConnectionUiState.Reconnecting

            EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
            EngineEvent.TYPE_SERVICE_DISCONNECTED,
            EngineEvent.TYPE_SERVICE_NULL_BINDING -> BambooEngineConnectionUiState.Unavailable

            else -> current
        }

    private fun AutomotiveUxRestrictions.toPlaybackRestrictionState(): BambooPlaybackRestrictionState {
        val label = when (source) {
            AutomotiveUxRestrictions.Source.AutomotivePlatform ->
                if (isRestricted) "Driver-safe mode" else "Unrestricted"

            AutomotiveUxRestrictions.Source.NotAutomotive ->
                "Standard device"

            AutomotiveUxRestrictions.Source.Unavailable ->
                "Safety status unavailable"
        }

        return BambooPlaybackRestrictionState(
            label = label,
            isRestricted = isRestricted
        )
    }

    private fun EnginePlayerControls.toPlaybackControls(): BambooPlaybackControls = BambooPlaybackControls(
        playPause = playPause.toPlaybackControlState(),
        skipNext = skipNext.toPlaybackControlState(),
        skipPrevious = skipPrevious.toPlaybackControlState(),
        showPlayIcon = showPlayIcon
    )

    private fun EngineControlState.toPlaybackControlState(): BambooControlState = BambooControlState(
        isVisible = isVisible,
        isEnabled = isEnabled,
        isActive = isActive
    )
}
