package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineEvent(
    val type: String,
    val message: String?,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        type = parcel.readString().orEmpty(),
        message = parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(message)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TYPE_COMMAND_APPLIED = "command_applied"
        const val TYPE_LISTENER_REGISTERED = "listener_registered"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineEvent> =
            object : Parcelable.Creator<EngineEvent> {
                override fun createFromParcel(parcel: Parcel): EngineEvent =
                    EngineEvent(parcel)

                override fun newArray(size: Int): Array<EngineEvent?> =
                    arrayOfNulls(size)
            }
    }
}
