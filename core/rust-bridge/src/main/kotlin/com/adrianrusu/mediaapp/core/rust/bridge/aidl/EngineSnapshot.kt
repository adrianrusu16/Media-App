package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineSnapshot(
    val playbackState: String,
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val userId: String?,
    val restrictionState: String,
    val updatedAtEpochMillis: Long,
    val hasActiveSession: Boolean = false,
    val hasError: Boolean = false,
    val errorType: String = ERROR_NONE,
    val searchResultsCount: Int = 0,
    val playbackSpeed: Float = 1F,
    val positionMillis: Long = 0L,
    val isBusy: Boolean = false,
    val canDispatch: Boolean = true,
    val controls: EnginePlayerControls = EnginePlayerControls.default(),
    val hasVoiceHypothesis: Boolean = false,
    val browseResultsCount: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        playbackState = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        title = parcel.readString(),
        artist = parcel.readString(),
        userId = parcel.readString(),
        restrictionState = parcel.readString().orEmpty(),
        updatedAtEpochMillis = parcel.readLong(),
        hasActiveSession = parcel.readBooleanValue(),
        hasError = parcel.readBooleanValue(),
        errorType = parcel.readString() ?: ERROR_NONE,
        searchResultsCount = parcel.readInt(),
        playbackSpeed = parcel.readFloat(),
        positionMillis = parcel.readLong(),
        isBusy = parcel.readBooleanValue(),
        canDispatch = parcel.readBooleanValue(),
        controls = EnginePlayerControls(
            playPause = parcel.readControlState(),
            skipNext = parcel.readControlState(),
            skipPrevious = parcel.readControlState(),
            showPlayIcon = parcel.readBooleanValue()
        ),
        hasVoiceHypothesis = parcel.readBooleanValue(),
        browseResultsCount = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(playbackState)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(userId)
        parcel.writeString(restrictionState)
        parcel.writeLong(updatedAtEpochMillis)
        parcel.writeBooleanValue(hasActiveSession)
        parcel.writeBooleanValue(hasError)
        parcel.writeString(errorType)
        parcel.writeInt(searchResultsCount)
        parcel.writeFloat(playbackSpeed)
        parcel.writeLong(positionMillis)
        parcel.writeBooleanValue(isBusy)
        parcel.writeBooleanValue(canDispatch)
        parcel.writeControlState(controls.playPause)
        parcel.writeControlState(controls.skipNext)
        parcel.writeControlState(controls.skipPrevious)
        parcel.writeBooleanValue(controls.showPlayIcon)
        parcel.writeBooleanValue(hasVoiceHypothesis)
        parcel.writeInt(browseResultsCount)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val PLAYBACK_IDLE = "idle"
        const val PLAYBACK_PLAYING = "playing"
        const val PLAYBACK_PAUSED = "paused"
        const val PLAYBACK_BUFFERING = "buffering"
        const val PLAYBACK_ERROR = "error"
        const val RESTRICTION_UNKNOWN = "unknown"
        const val ERROR_NONE = "none"
        const val ERROR_NOT_FOUND = "not_found"
        const val ERROR_NETWORK = "network"
        const val ERROR_PLAYER = "player"
        const val ERROR_AUTHENTICATION = "authentication"
        const val ERROR_MEDIA_SKIPPED = "media_skipped"
        const val ERROR_UNKNOWN = "unknown"

        fun idle(nowMillis: Long): EngineSnapshot = EngineSnapshot(
            playbackState = PLAYBACK_IDLE,
            mediaId = null,
            title = null,
            artist = null,
            userId = null,
            restrictionState = RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = nowMillis,
            controls = EnginePlayerControls.defaultIdle()
        )

        @JvmField
        val CREATOR: Parcelable.Creator<EngineSnapshot> =
            object : Parcelable.Creator<EngineSnapshot> {
                override fun createFromParcel(parcel: Parcel): EngineSnapshot = EngineSnapshot(parcel)

                override fun newArray(size: Int): Array<EngineSnapshot?> = arrayOfNulls(size)
            }
    }
}

data class EngineControlState(val isVisible: Boolean, val isEnabled: Boolean, val isActive: Boolean) {
    companion object {
        fun hidden(): EngineControlState = EngineControlState(
            isVisible = false,
            isEnabled = false,
            isActive = false
        )

        fun enabled(): EngineControlState = EngineControlState(
            isVisible = true,
            isEnabled = true,
            isActive = false
        )
    }
}

data class EnginePlayerControls(
    val playPause: EngineControlState,
    val skipNext: EngineControlState,
    val skipPrevious: EngineControlState,
    val showPlayIcon: Boolean
) {
    companion object {
        fun default(): EnginePlayerControls = EnginePlayerControls(
            playPause = EngineControlState.hidden(),
            skipNext = EngineControlState.hidden(),
            skipPrevious = EngineControlState.hidden(),
            showPlayIcon = true
        )

        fun defaultIdle(): EnginePlayerControls = EnginePlayerControls(
            playPause = EngineControlState.enabled(),
            skipNext = EngineControlState.hidden(),
            skipPrevious = EngineControlState.hidden(),
            showPlayIcon = true
        )
    }
}

private fun Parcel.readBooleanValue(): Boolean = readInt() != 0

private fun Parcel.writeBooleanValue(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

private fun Parcel.readControlState(): EngineControlState = EngineControlState(
    isVisible = readBooleanValue(),
    isEnabled = readBooleanValue(),
    isActive = readBooleanValue()
)

private fun Parcel.writeControlState(controlState: EngineControlState) {
    writeBooleanValue(controlState.isVisible)
    writeBooleanValue(controlState.isEnabled)
    writeBooleanValue(controlState.isActive)
}
