package com.adrianrusu.pandawave.permission

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.playback.BambooVehicleSafetyState

internal fun shouldRequestVisualizerPermission(
    permissionState: VisualizerPermissionState,
    vehicleSafety: BambooVehicleSafetyState
): Boolean = permissionState == VisualizerPermissionState.Denied(canRequest = true) &&
    vehicleSafety.isParked &&
    vehicleSafety.isUxUnrestricted
