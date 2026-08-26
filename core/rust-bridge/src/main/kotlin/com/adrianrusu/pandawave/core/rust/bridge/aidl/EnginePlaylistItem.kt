package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EnginePlaylistItem(
    val id: String,
    val name: String,
    val description: String?,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readString().orEmpty(),
        name = parcel.readString().orEmpty(),
        description = parcel.readString(),
        revision = parcel.readLong(),
        createdAtEpochMillis = parcel.readLong(),
        updatedAtEpochMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeLong(revision)
        parcel.writeLong(createdAtEpochMillis)
        parcel.writeLong(updatedAtEpochMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EnginePlaylistItem> =
            object : Parcelable.Creator<EnginePlaylistItem> {
                override fun createFromParcel(parcel: Parcel): EnginePlaylistItem = EnginePlaylistItem(parcel)

                override fun newArray(size: Int): Array<EnginePlaylistItem?> = arrayOfNulls(size)
            }
    }
}

data class EnginePlaylistTrackItem(
    val membershipId: String,
    val playlistId: String,
    val mediaId: String,
    val title: String,
    val artistId: String,
    val artist: String,
    val album: String?,
    val durationMillis: Long,
    val explicit: Boolean,
    val artworkId: String?,
    val position: Int,
    val addedAtEpochMillis: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        membershipId = parcel.readString().orEmpty(),
        playlistId = parcel.readString().orEmpty(),
        mediaId = parcel.readString().orEmpty(),
        title = parcel.readString().orEmpty(),
        artistId = parcel.readString().orEmpty(),
        artist = parcel.readString().orEmpty(),
        album = parcel.readString(),
        durationMillis = parcel.readLong(),
        explicit = parcel.readInt() != 0,
        artworkId = parcel.readString(),
        position = parcel.readInt(),
        addedAtEpochMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(membershipId)
        parcel.writeString(playlistId)
        parcel.writeString(mediaId)
        parcel.writeString(title)
        parcel.writeString(artistId)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeLong(durationMillis)
        parcel.writeInt(if (explicit) 1 else 0)
        parcel.writeString(artworkId)
        parcel.writeInt(position)
        parcel.writeLong(addedAtEpochMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EnginePlaylistTrackItem> =
            object : Parcelable.Creator<EnginePlaylistTrackItem> {
                override fun createFromParcel(parcel: Parcel): EnginePlaylistTrackItem = EnginePlaylistTrackItem(parcel)

                override fun newArray(size: Int): Array<EnginePlaylistTrackItem?> = arrayOfNulls(size)
            }
    }
}

data class EnginePlaylistReconciliation(
    val playlistId: String,
    val expectedRevision: Long,
    val serverRevision: Long,
    val serverMembershipIds: List<String>,
    val proposedMembershipIds: List<String>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        playlistId = parcel.readString().orEmpty(),
        expectedRevision = parcel.readLong(),
        serverRevision = parcel.readLong(),
        serverMembershipIds = parcel.createStringArrayList().orEmpty(),
        proposedMembershipIds = parcel.createStringArrayList().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(playlistId)
        parcel.writeLong(expectedRevision)
        parcel.writeLong(serverRevision)
        parcel.writeStringList(serverMembershipIds)
        parcel.writeStringList(proposedMembershipIds)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EnginePlaylistReconciliation> =
            object : Parcelable.Creator<EnginePlaylistReconciliation> {
                override fun createFromParcel(parcel: Parcel): EnginePlaylistReconciliation =
                    EnginePlaylistReconciliation(parcel)

                override fun newArray(size: Int): Array<EnginePlaylistReconciliation?> = arrayOfNulls(size)
            }
    }
}
