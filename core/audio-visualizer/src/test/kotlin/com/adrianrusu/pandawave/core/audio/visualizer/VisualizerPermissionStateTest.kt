package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.test.Test
import kotlin.test.assertEquals

class VisualizerPermissionStateTest {
    @Test
    fun `unknown platform result remains unknown`() {
        assertEquals(
            VisualizerPermissionState.Unknown,
            resolveVisualizerPermissionState(granted = null)
        )
    }

    @Test
    fun `granted platform permission maps to granted`() {
        assertEquals(
            VisualizerPermissionState.Granted,
            resolveVisualizerPermissionState(granted = true)
        )
    }

    @Test
    fun `ordinary denial remains requestable`() {
        assertEquals(
            VisualizerPermissionState.Denied(canRequest = true),
            resolveVisualizerPermissionState(granted = false)
        )
    }

    @Test
    fun `denial after a request follows the platform rationale state`() {
        assertEquals(
            VisualizerPermissionState.Denied(canRequest = false),
            resolveVisualizerPermissionState(
                granted = false,
                hasRequested = true,
                shouldShowRationale = false
            )
        )
        assertEquals(
            VisualizerPermissionState.Denied(canRequest = true),
            resolveVisualizerPermissionState(
                granted = false,
                hasRequested = true,
                shouldShowRationale = true
            )
        )
    }

    @Test
    fun `permission action follows denied requestability`() {
        assertEquals(
            VisualizerPermissionAction.Request,
            VisualizerPermissionState.Denied(canRequest = true).recommendedAction
        )
        assertEquals(
            VisualizerPermissionAction.OpenSettings,
            VisualizerPermissionState.Denied(canRequest = false).recommendedAction
        )
        assertEquals(VisualizerPermissionAction.None, VisualizerPermissionState.Granted.recommendedAction)
        assertEquals(VisualizerPermissionAction.None, VisualizerPermissionState.Unknown.recommendedAction)
    }
}
