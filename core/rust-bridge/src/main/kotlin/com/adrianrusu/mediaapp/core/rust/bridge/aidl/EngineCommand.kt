package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineCommand(val type: String, val payload: String?) : Parcelable {
    constructor(parcel: Parcel) : this(
        type = parcel.readString().orEmpty(),
        payload = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(payload)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TYPE_BOOTSTRAP = "bootstrap"
        const val TYPE_PLAY = "play"
        const val TYPE_PAUSE = "pause"
        const val TYPE_SKIP_PREVIOUS = "skip_previous"
        const val TYPE_SKIP_NEXT = "skip_next"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineCommand> =
            object : Parcelable.Creator<EngineCommand> {
                override fun createFromParcel(parcel: Parcel): EngineCommand = EngineCommand(parcel)

                override fun newArray(size: Int): Array<EngineCommand?> = arrayOfNulls(size)
            }
    }
}
