package com.adrianrusu.pandawave.permission

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.playback.BambooDrivingState
import com.adrianrusu.pandawave.core.playback.BambooRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooVehicleSafetyState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisualizerPermissionStartupGateTest {
    @Test
    fun `requests only when parked unrestricted and not previously requested`() {
        val safe = BambooVehicleSafetyState(
            drivingState = BambooDrivingState.Parked,
            restrictionState = BambooRestrictionState.Unrestricted
        )

        assertTrue(
            shouldRequestVisualizerPermission(
                permissionState = VisualizerPermissionState.Denied(canRequest = true),
                vehicleSafety = safe
            )
        )
        assertFalse(
            shouldRequestVisualizerPermission(
                permissionState = VisualizerPermissionState.Denied(canRequest = false),
                vehicleSafety = safe
            )
        )
        assertFalse(
            shouldRequestVisualizerPermission(
                permissionState = VisualizerPermissionState.Denied(canRequest = true),
                vehicleSafety = safe.copy(drivingState = BambooDrivingState.Unknown)
            )
        )
        assertFalse(
            shouldRequestVisualizerPermission(
                permissionState = VisualizerPermissionState.Denied(canRequest = true),
                vehicleSafety = safe.copy(restrictionState = BambooRestrictionState.Restricted)
            )
        )
    }
}
