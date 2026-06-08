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
    val updatedAtEpochMillis: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        playbackState = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        title = parcel.readString(),
        artist = parcel.readString(),
        userId = parcel.readString(),
        restrictionState = parcel.readString().orEmpty(),
        updatedAtEpochMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(playbackState)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(userId)
        parcel.writeString(restrictionState)
        parcel.writeLong(updatedAtEpochMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val PLAYBACK_IDLE = "idle"
        const val PLAYBACK_PLAYING = "playing"
        const val PLAYBACK_PAUSED = "paused"
        const val PLAYBACK_BUFFERING = "buffering"
        const val PLAYBACK_ERROR = "error"
        const val RESTRICTION_UNKNOWN = "unknown"

        fun idle(nowMillis: Long): EngineSnapshot = EngineSnapshot(
            playbackState = PLAYBACK_IDLE,
            mediaId = null,
            title = null,
            artist = null,
            userId = null,
            restrictionState = RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = nowMillis
        )

        @JvmField
        val CREATOR: Parcelable.Creator<EngineSnapshot> =
            object : Parcelable.Creator<EngineSnapshot> {
                override fun createFromParcel(parcel: Parcel): EngineSnapshot = EngineSnapshot(parcel)

                override fun newArray(size: Int): Array<EngineSnapshot?> = arrayOfNulls(size)
            }
    }
}
