package com.adrianrusu.mediaapp.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineEffect(
    val type: String,
    val mediaId: String? = null,
    val message: String? = null,
    val positionMillis: Long? = null,
    val speed: Float? = null
) : Parcelable {
    init {
        require(type.isNotBlank()) {
            "Engine effect type must not be blank."
        }
    }

    constructor(parcel: Parcel) : this(
        type = parcel.readString().orEmpty(),
        mediaId = parcel.readString(),
        message = parcel.readString(),
        positionMillis = parcel.readNullableLong(),
        speed = parcel.readNullableFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(mediaId)
        parcel.writeString(message)
        parcel.writeNullableLong(positionMillis)
        parcel.writeNullableFloat(speed)
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
        const val TYPE_PREPARE_PLAYBACK_SOURCE = "prepare_playback_source"
        const val TYPE_UNKNOWN = "unknown"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineEffect> =
            object : Parcelable.Creator<EngineEffect> {
                override fun createFromParcel(parcel: Parcel): EngineEffect = EngineEffect(parcel)

                override fun newArray(size: Int): Array<EngineEffect?> = arrayOfNulls(size)
            }
    }
}

private fun Parcel.readNullableLong(): Long? = when (readByte()) {
    VALUE_PRESENT -> readLong()
    else -> null
}

private fun Parcel.writeNullableLong(value: Long?) {
    when (value) {
        null -> writeByte(VALUE_ABSENT)

        else -> {
            writeByte(VALUE_PRESENT)
            writeLong(value)
        }
    }
}

private fun Parcel.readNullableFloat(): Float? = when (readByte()) {
    VALUE_PRESENT -> readFloat()
    else -> null
}

private fun Parcel.writeNullableFloat(value: Float?) {
    when (value) {
        null -> writeByte(VALUE_ABSENT)

        else -> {
            writeByte(VALUE_PRESENT)
            writeFloat(value)
        }
    }
}

private const val VALUE_ABSENT: Byte = 0
private const val VALUE_PRESENT: Byte = 1
