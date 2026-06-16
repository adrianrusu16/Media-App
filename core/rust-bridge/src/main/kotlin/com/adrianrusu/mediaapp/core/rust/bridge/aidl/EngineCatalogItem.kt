package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineCatalogItem(
    val mediaId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val sourceUri: String? = null,
    val mimeType: String? = null,
    val itemType: Int = TYPE_TRACK
) : Parcelable {
    init {
        require(mediaId.isNotBlank()) {
            "Engine catalog item mediaId must not be blank."
        }
        require(title.isNotBlank()) {
            "Engine catalog item title must not be blank."
        }
        require(itemType in knownItemTypes) {
            "Engine catalog item type must be a known engine media item type."
        }
    }

    constructor(parcel: Parcel) : this(
        mediaId = parcel.readString().orEmpty(),
        title = parcel.readString().orEmpty(),
        artist = parcel.readString(),
        album = parcel.readString(),
        artworkUri = parcel.readString(),
        sourceUri = parcel.readString(),
        mimeType = parcel.readString(),
        itemType = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeString(artworkUri)
        parcel.writeString(sourceUri)
        parcel.writeString(mimeType)
        parcel.writeInt(itemType)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TYPE_TRACK = 0
        const val TYPE_ARTIST = 1
        const val TYPE_ALBUM = 2
        const val TYPE_FOLDER = 3
        const val TYPE_PLAYLIST = 4
        const val TYPE_RADIO_STATION = 5

        private val knownItemTypes = setOf(
            TYPE_TRACK,
            TYPE_ARTIST,
            TYPE_ALBUM,
            TYPE_FOLDER,
            TYPE_PLAYLIST,
            TYPE_RADIO_STATION
        )

        @JvmField
        val CREATOR: Parcelable.Creator<EngineCatalogItem> =
            object : Parcelable.Creator<EngineCatalogItem> {
                override fun createFromParcel(parcel: Parcel): EngineCatalogItem = EngineCatalogItem(parcel)

                override fun newArray(size: Int): Array<EngineCatalogItem?> = arrayOfNulls(size)
            }
    }
}
