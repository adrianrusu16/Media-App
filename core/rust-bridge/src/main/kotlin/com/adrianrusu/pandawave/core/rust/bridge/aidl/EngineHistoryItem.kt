package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineHistoryItem(
    val historyId: String,
    val mediaId: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val playedAtEpochMillis: Long?,
    val listenedDurationMillis: Long,
    val completionRatio: Float,
    val playable: Boolean,
    val artworkId: String? = null,
    val artworkVersion: String? = null
) : Parcelable {
    init {
        require(historyId.isNotBlank()) { "Engine history item historyId must not be blank." }
        require(title.isNotBlank()) { "Engine history item title must not be blank." }
        require(completionRatio.isFinite()) { "Engine history item completionRatio must be finite." }
    }

    constructor(parcel: Parcel) : this(
        historyId = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        title = parcel.readString().orEmpty(),
        artist = parcel.readString(),
        album = parcel.readString(),
        artworkUri = parcel.readString(),
        playedAtEpochMillis = parcel.readNullableLong(),
        listenedDurationMillis = parcel.readLong(),
        completionRatio = parcel.readFloat(),
        playable = parcel.readBooleanValue(),
        artworkId = parcel.readString(),
        artworkVersion = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(historyId)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeString(artworkUri)
        parcel.writeNullableLong(playedAtEpochMillis)
        parcel.writeLong(listenedDurationMillis)
        parcel.writeFloat(completionRatio)
        parcel.writeBooleanValue(playable)
        parcel.writeString(artworkId)
        parcel.writeString(artworkVersion)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EngineHistoryItem> =
            object : Parcelable.Creator<EngineHistoryItem> {
                override fun createFromParcel(parcel: Parcel): EngineHistoryItem = EngineHistoryItem(parcel)

                override fun newArray(size: Int): Array<EngineHistoryItem?> = arrayOfNulls(size)
            }
    }
}

private fun Parcel.readBooleanValue(): Boolean = readInt() != 0

private fun Parcel.writeBooleanValue(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

private fun Parcel.readNullableLong(): Long? = if (readBooleanValue()) {
    readLong()
} else {
    null
}

private fun Parcel.writeNullableLong(value: Long?) {
    writeBooleanValue(value != null)
    if (value != null) {
        writeLong(value)
    }
}
