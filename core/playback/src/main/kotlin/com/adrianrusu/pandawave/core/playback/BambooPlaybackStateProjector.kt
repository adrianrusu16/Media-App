package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

internal object BambooPlaybackStateProjector {
    fun fromEngineSnapshot(current: BambooPlaybackState, snapshot: EngineSnapshot): BambooPlaybackState = current.copy(
        mediaId = snapshot.mediaId,
        title = snapshot.title.orEmpty(),
        artist = snapshot.artist.orEmpty(),
        album = snapshot.album,
        durationMillis = snapshot.durationMillis,
        artworkUri = snapshot.artworkUri,
        sourceUri = snapshot.sourceUri,
        mimeType = snapshot.mimeType,
        playbackExpiresAtEpochMillis = snapshot.playbackExpiresAtEpochMillis,
        playbackStatus = snapshot.playbackState.toPlaybackStatus(),
        updatedAtEpochMillis = snapshot.lastProgressTickEpochMillis.takeIf { tick -> tick > 0L }
            ?: snapshot.updatedAtEpochMillis,
        positionMillis = snapshot.positionMillis,
        playbackSpeed = snapshot.playbackSpeed,
        hasActiveSession = snapshot.hasActiveSession,
        hasError = snapshot.hasError,
        errorType = snapshot.errorType,
        searchResultsCount = snapshot.searchResultsCount,
        browseResultsCount = snapshot.browseResultsCount,
        isBusy = snapshot.isBusy,
        canDispatch = snapshot.canDispatch,
        controls = snapshot.controls.toPlaybackControls(),
        restriction = BambooPlaybackRestrictionState(
            isRestricted = snapshot.restrictionState == EngineSnapshot.RESTRICTION_RESTRICTED
        ),
        vehicleSafety = BambooVehicleSafetyState(
            drivingState = snapshot.drivingState.toDrivingState(),
            restrictionState = snapshot.restrictionState.toRestrictionState()
        )
    )

    fun fromEngineEvent(current: BambooPlaybackState, event: EngineEvent): BambooPlaybackState = current.copy(
        engineConnection = event.toConnectionUiState(current = current.engineConnection)
    )

    private fun String.toPlaybackStatus(): BambooPlaybackStatus = when (this) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackStatus.Playing
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackStatus.Paused
        EngineSnapshot.PLAYBACK_RECOVERING -> BambooPlaybackStatus.Recovering
        EngineSnapshot.PLAYBACK_ENDED -> BambooPlaybackStatus.Ended
        else -> BambooPlaybackStatus.Idle
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

    private fun String.toDrivingState(): BambooDrivingState = when (this) {
        EngineSnapshot.DRIVING_PARKED -> BambooDrivingState.Parked
        EngineSnapshot.DRIVING_IDLING -> BambooDrivingState.Idling
        EngineSnapshot.DRIVING_MOVING -> BambooDrivingState.Moving
        else -> BambooDrivingState.Unknown
    }

    private fun String.toRestrictionState(): BambooRestrictionState = when (this) {
        EngineSnapshot.RESTRICTION_UNRESTRICTED -> BambooRestrictionState.Unrestricted
        EngineSnapshot.RESTRICTION_RESTRICTED -> BambooRestrictionState.Restricted
        else -> BambooRestrictionState.Unknown
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
