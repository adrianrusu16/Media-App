package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineLibraryItem(
    val relationshipId: String,
    val mediaId: String,
    val title: String,
    val artistId: String,
    val artist: String,
    val album: String? = null,
    val durationMillis: Long = 0L,
    val explicit: Boolean = false,
    val artworkId: String? = null,
    val relationshipAtEpochMillis: Long
) : Parcelable {
    init {
        require(relationshipId.isNotBlank() && mediaId.isNotBlank() && title.isNotBlank())
        require(durationMillis >= 0L && relationshipAtEpochMillis >= 0L)
    }

    constructor(parcel: Parcel) : this(
        relationshipId = parcel.readString().orEmpty(),
        mediaId = parcel.readString().orEmpty(),
        title = parcel.readString().orEmpty(),
        artistId = parcel.readString().orEmpty(),
        artist = parcel.readString().orEmpty(),
        album = parcel.readString(),
        durationMillis = parcel.readLong(),
        explicit = parcel.readInt() != 0,
        artworkId = parcel.readString(),
        relationshipAtEpochMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(relationshipId)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artistId)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeLong(durationMillis)
        parcel.writeInt(if (explicit) 1 else 0)
        parcel.writeString(artworkId)
        parcel.writeLong(relationshipAtEpochMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<EngineLibraryItem> = object : Parcelable.Creator<EngineLibraryItem> {
            override fun createFromParcel(parcel: Parcel): EngineLibraryItem = EngineLibraryItem(parcel)
            override fun newArray(size: Int): Array<EngineLibraryItem?> = arrayOfNulls(size)
        }
    }
}
