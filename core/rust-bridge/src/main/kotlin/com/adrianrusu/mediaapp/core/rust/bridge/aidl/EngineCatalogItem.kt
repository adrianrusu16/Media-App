package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineCatalogItem(val mediaId: String, val title: String) : Parcelable {
    init {
        require(mediaId.isNotBlank()) {
            "Engine catalog item mediaId must not be blank."
        }
        require(title.isNotBlank()) {
            "Engine catalog item title must not be blank."
        }
    }

    constructor(parcel: Parcel) : this(
        mediaId = parcel.readString().orEmpty(),
        title = parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(mediaId)
        parcel.writeString(title)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EngineCatalogItem> =
            object : Parcelable.Creator<EngineCatalogItem> {
                override fun createFromParcel(parcel: Parcel): EngineCatalogItem = EngineCatalogItem(parcel)

                override fun newArray(size: Int): Array<EngineCatalogItem?> = arrayOfNulls(size)
            }
    }
}
