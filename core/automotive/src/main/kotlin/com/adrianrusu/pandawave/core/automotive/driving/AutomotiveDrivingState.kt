package com.adrianrusu.pandawave.core.automotive.driving

import android.car.VehicleGear
import kotlin.math.abs

enum class AutomotiveDrivingState {
    Unknown,
    Parked,
    Idling,
    Moving;

    companion object {
        fun fromVehicleSignals(gearSelection: Int?, speedMetersPerSecond: Float?): AutomotiveDrivingState {
            if (speedMetersPerSecond != null && !speedMetersPerSecond.isFinite()) return Unknown
            if (speedMetersPerSecond != null && abs(speedMetersPerSecond) > MOVING_SPEED_THRESHOLD_MPS) {
                return Moving
            }
            if (gearSelection == null || speedMetersPerSecond == null) return Unknown
            return if (gearSelection == VehicleGear.GEAR_PARK) Parked else Idling
        }

        private const val MOVING_SPEED_THRESHOLD_MPS = 0.1F
    }
}
