package com.adrianrusu.pandawave.core.rust.bridge.engine

import android.os.Parcel
import android.os.Parcelable
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

data class EngineDispatchResult(
    val snapshot: EngineSnapshot,
    val event: EngineEvent,
    val effects: List<EngineEffect> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        snapshot = parcel.readParcelable(
            EngineSnapshot::class.java.classLoader,
            EngineSnapshot::class.java
        ) ?: EngineSnapshot.idle(nowMillis = 0L),
        event = parcel.readParcelable(
            EngineEvent::class.java.classLoader,
            EngineEvent::class.java
        ) ?: EngineEvent(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, null),
        effects = mutableListOf<EngineEffect>().also { values ->
            parcel.readTypedList(values, EngineEffect.CREATOR)
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(snapshot, flags)
        parcel.writeParcelable(event, flags)
        parcel.writeTypedList(effects)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EngineDispatchResult> =
            object : Parcelable.Creator<EngineDispatchResult> {
                override fun createFromParcel(parcel: Parcel): EngineDispatchResult = EngineDispatchResult(parcel)

                override fun newArray(size: Int): Array<EngineDispatchResult?> = arrayOfNulls(size)
            }
    }
}
