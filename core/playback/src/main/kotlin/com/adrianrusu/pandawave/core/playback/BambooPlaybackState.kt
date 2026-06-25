package com.adrianrusu.pandawave.core.playback

data class BambooPlaybackState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val durationMillis: Long? = null,
    val artworkUri: String? = null,
    val sourceUri: String? = null,
    val mimeType: String? = null,
    val playbackStatus: BambooPlaybackStatus = BambooPlaybackStatus.Idle,
    val engineConnection: BambooEngineConnectionUiState = BambooEngineConnectionUiState.Connecting,
    val restriction: BambooPlaybackRestrictionState = BambooPlaybackRestrictionState.Unavailable,
    val vehicleSafety: BambooVehicleSafetyState = BambooVehicleSafetyState.Unknown,
    val updatedAtEpochMillis: Long = 0L,
    val positionMillis: Long = 0L,
    val playbackSpeed: Float = 1F,
    val hasActiveSession: Boolean = false,
    val hasError: Boolean = false,
    val errorType: String = "none",
    val searchResultsCount: Int = 0,
    val browseResultsCount: Int = 0,
    val isBusy: Boolean = false,
    val canDispatch: Boolean = true,
    val controls: BambooPlaybackControls = BambooPlaybackControls.default()
) {
    val isPlaying: Boolean
        get() = playbackStatus == BambooPlaybackStatus.Playing

    val canDispatchEngineCommands: Boolean
        get() = engineConnection.status == BambooEngineConnectionStatus.Ready && canDispatch
}

enum class BambooDrivingState {
    Unknown,
    Parked,
    Idling,
    Moving
}

enum class BambooRestrictionState {
    Unknown,
    Unrestricted,
    Restricted
}

data class BambooVehicleSafetyState(
    val drivingState: BambooDrivingState,
    val restrictionState: BambooRestrictionState
) {
    val isParked: Boolean
        get() = drivingState == BambooDrivingState.Parked

    val isUxUnrestricted: Boolean
        get() = restrictionState == BambooRestrictionState.Unrestricted

    companion object {
        val Unknown = BambooVehicleSafetyState(
            drivingState = BambooDrivingState.Unknown,
            restrictionState = BambooRestrictionState.Unknown
        )
    }
}

enum class BambooPlaybackStatus {
    Idle,
    Playing,
    Paused
}

data class BambooEngineConnectionUiState(val status: BambooEngineConnectionStatus) {
    companion object {
        val Connecting = BambooEngineConnectionUiState(
            status = BambooEngineConnectionStatus.Connecting
        )
        val Ready = BambooEngineConnectionUiState(
            status = BambooEngineConnectionStatus.Ready
        )
        val Reconnecting = BambooEngineConnectionUiState(
            status = BambooEngineConnectionStatus.Reconnecting
        )
        val Unavailable = BambooEngineConnectionUiState(
            status = BambooEngineConnectionStatus.Unavailable
        )
    }
}

enum class BambooEngineConnectionStatus {
    Connecting,
    Ready,
    Reconnecting,
    Unavailable
}

data class BambooPlaybackRestrictionState(val isRestricted: Boolean) {
    companion object {
        val Unavailable = BambooPlaybackRestrictionState(
            isRestricted = false
        )
    }
}

data class BambooControlState(val isVisible: Boolean, val isEnabled: Boolean, val isActive: Boolean) {
    companion object {
        fun hidden(): BambooControlState = BambooControlState(
            isVisible = false,
            isEnabled = false,
            isActive = false
        )

        fun enabled(): BambooControlState = BambooControlState(
            isVisible = true,
            isEnabled = true,
            isActive = false
        )
    }
}

data class BambooPlaybackControls(
    val playPause: BambooControlState,
    val skipNext: BambooControlState,
    val skipPrevious: BambooControlState,
    val showPlayIcon: Boolean
) {
    companion object {
        fun default(): BambooPlaybackControls = BambooPlaybackControls(
            playPause = BambooControlState.hidden(),
            skipNext = BambooControlState.hidden(),
            skipPrevious = BambooControlState.hidden(),
            showPlayIcon = true
        )
    }
}
