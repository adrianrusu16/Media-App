package com.adrianrusu.pandawave.core.automotive.driving

import android.car.VehicleGear
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomotiveDrivingStateTest {
    @Test
    fun `maps known platform states and fails unknown values closed`() {
        assertEquals(
            AutomotiveDrivingState.Parked,
            AutomotiveDrivingState.fromVehicleSignals(
                gearSelection = VehicleGear.GEAR_PARK,
                speedMetersPerSecond = null
            )
        )
        assertEquals(
            AutomotiveDrivingState.Idling,
            AutomotiveDrivingState.fromVehicleSignals(
                gearSelection = VehicleGear.GEAR_DRIVE,
                speedMetersPerSecond = 0F
            )
        )
        assertEquals(
            AutomotiveDrivingState.Moving,
            AutomotiveDrivingState.fromVehicleSignals(
                gearSelection = VehicleGear.GEAR_DRIVE,
                speedMetersPerSecond = 2F
            )
        )
        assertEquals(
            AutomotiveDrivingState.Unknown,
            AutomotiveDrivingState.fromVehicleSignals(gearSelection = null, speedMetersPerSecond = 0F)
        )
        assertEquals(
            AutomotiveDrivingState.Unknown,
            AutomotiveDrivingState.fromVehicleSignals(
                gearSelection = VehicleGear.GEAR_DRIVE,
                speedMetersPerSecond = null
            )
        )
        assertEquals(
            AutomotiveDrivingState.Unknown,
            AutomotiveDrivingState.fromVehicleSignals(
                gearSelection = VehicleGear.GEAR_PARK,
                speedMetersPerSecond = Float.NaN
            )
        )
    }
}
