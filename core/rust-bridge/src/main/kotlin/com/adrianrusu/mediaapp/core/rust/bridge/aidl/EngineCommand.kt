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
        const val TYPE_SEARCH = "search"
        const val TYPE_BROWSE = "browse"
        const val TYPE_SET_SPEED = "set_speed"
        const val TYPE_SEEK = "seek"
        const val TYPE_PLAY_MEDIA_BY_ID = "play_media_by_id"
        const val TYPE_HYDRATE_THEME_PREFERENCE = "hydrate_theme_preference"
        const val TYPE_SET_THEME_PREFERENCE = "set_theme_preference"
        const val TYPE_APPLY_REMOTE_THEME_PREFERENCE = "apply_remote_theme_preference"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineCommand> =
            object : Parcelable.Creator<EngineCommand> {
                override fun createFromParcel(parcel: Parcel): EngineCommand = EngineCommand(parcel)

                override fun newArray(size: Int): Array<EngineCommand?> = arrayOfNulls(size)
            }
    }
}
