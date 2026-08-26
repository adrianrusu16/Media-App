package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineHistoryPage(val generation: Long, val items: List<EngineHistoryItem>) : Parcelable {
    constructor(parcel: Parcel) : this(
        generation = parcel.readLong(),
        items = parcel.createTypedArrayList(EngineHistoryItem.CREATOR).orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(generation)
        parcel.writeTypedList(items)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EngineHistoryPage> =
            object : Parcelable.Creator<EngineHistoryPage> {
                override fun createFromParcel(parcel: Parcel): EngineHistoryPage = EngineHistoryPage(parcel)

                override fun newArray(size: Int): Array<EngineHistoryPage?> = arrayOfNulls(size)
            }
    }
}
