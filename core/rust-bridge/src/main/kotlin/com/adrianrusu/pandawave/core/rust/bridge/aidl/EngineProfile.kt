package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineProfile(
    val id: String,
    val externalUserId: String,
    val displayName: String?,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readString().orEmpty(),
        externalUserId = parcel.readString().orEmpty(),
        displayName = if (parcel.readInt() != 0) parcel.readString().orEmpty() else null,
        createdAtEpochMillis = parcel.readOptionalLong(),
        updatedAtEpochMillis = parcel.readOptionalLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(externalUserId)
        parcel.writeInt(if (displayName != null) 1 else 0)
        if (displayName != null) parcel.writeString(displayName)
        parcel.writeOptionalLong(createdAtEpochMillis)
        parcel.writeOptionalLong(updatedAtEpochMillis)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EngineProfile> =
            object : Parcelable.Creator<EngineProfile> {
                override fun createFromParcel(parcel: Parcel): EngineProfile = EngineProfile(parcel)
                override fun newArray(size: Int): Array<EngineProfile?> = arrayOfNulls(size)
            }
    }
}

private fun Parcel.readOptionalLong(): Long? = if (readInt() != 0) readLong() else null

private fun Parcel.writeOptionalLong(value: Long?) {
    writeInt(if (value != null) 1 else 0)
    if (value != null) writeLong(value)
}
