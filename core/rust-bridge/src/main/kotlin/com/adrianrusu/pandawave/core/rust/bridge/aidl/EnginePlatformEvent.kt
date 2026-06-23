package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EnginePlatformEvent(val type: String, val payload: String?) : Parcelable {
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
        const val TYPE_APP_FOREGROUNDED = "app_foregrounded"
        const val TYPE_APP_BACKGROUNDED = "app_backgrounded"
        const val TYPE_SUSPEND_TO_RAM = "suspend_to_ram"
        const val TYPE_RESUME_FROM_RAM = "resume_from_ram"
        const val TYPE_UX_RESTRICTIONS_CHANGED = "ux_restrictions_changed"
        const val TYPE_VEHICLE_DRIVING_STATE_CHANGED = "vehicle_driving_state_changed"
        const val TYPE_AUDIO_FOCUS_CHANGED = "audio_focus_changed"
        const val TYPE_MEDIA_LOADED = "media_loaded"
        const val TYPE_MEDIA_ERROR = "media_error"

        const val PAYLOAD_UNKNOWN = "unknown"
        const val PAYLOAD_UNRESTRICTED = "unrestricted"
        const val PAYLOAD_RESTRICTED = "restricted"
        const val PAYLOAD_PARKED = "parked"
        const val PAYLOAD_IDLING = "idling"
        const val PAYLOAD_MOVING = "moving"

        @JvmField
        val CREATOR: Parcelable.Creator<EnginePlatformEvent> =
            object : Parcelable.Creator<EnginePlatformEvent> {
                override fun createFromParcel(parcel: Parcel): EnginePlatformEvent = EnginePlatformEvent(parcel)

                override fun newArray(size: Int): Array<EnginePlatformEvent?> = arrayOfNulls(size)
            }
    }
}
