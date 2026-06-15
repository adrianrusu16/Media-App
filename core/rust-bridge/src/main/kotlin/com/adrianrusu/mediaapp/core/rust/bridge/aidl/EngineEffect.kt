package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineEffect(val type: String, val mediaId: String? = null, val message: String? = null) : Parcelable {
    init {
        require(type.isNotBlank()) {
            "Engine effect type must not be blank."
        }
    }

    constructor(parcel: Parcel) : this(
        type = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        message = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(mediaId)
        parcel.writeString(message)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TYPE_PLAY = "play"
        const val TYPE_PAUSE = "pause"
        const val TYPE_STOP = "stop"
        const val TYPE_SEEK = "seek"
        const val TYPE_REQUEST_AUDIO_FOCUS = "request_audio_focus"
        const val TYPE_ABANDON_AUDIO_FOCUS = "abandon_audio_focus"
        const val TYPE_UPDATE_METADATA = "update_metadata"
        const val TYPE_SESSION_STARTED = "session_started"
        const val TYPE_SESSION_ENDED = "session_ended"
        const val TYPE_SET_SPEED = "set_speed"
        const val TYPE_NOTIFY_USER = "notify_user"
        const val TYPE_START_AUDIO_CAPTURE = "start_audio_capture"
        const val TYPE_STOP_AUDIO_CAPTURE = "stop_audio_capture"
        const val TYPE_DUCK_AUDIO = "duck_audio"
        const val TYPE_UNDUCK_AUDIO = "unduck_audio"
        const val TYPE_UNKNOWN = "unknown"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineEffect> =
            object : Parcelable.Creator<EngineEffect> {
                override fun createFromParcel(parcel: Parcel): EngineEffect = EngineEffect(parcel)

                override fun newArray(size: Int): Array<EngineEffect?> = arrayOfNulls(size)
            }
    }
}
