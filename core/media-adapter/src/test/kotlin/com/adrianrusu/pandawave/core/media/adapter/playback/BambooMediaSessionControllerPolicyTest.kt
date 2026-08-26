package com.adrianrusu.pandawave.core.media.adapter.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooMediaSessionControllerPolicyTest {
    @Test
    fun `same package controllers are accepted`() {
        assertTrue(
            BambooMediaSessionControllerPolicy.isAccepted(
                controllerPackageName = "com.adrianrusu.pandawave",
                sessionPackageName = "com.adrianrusu.pandawave",
                isMediaNotificationController = false,
                isAutomotiveController = false,
                isAutoCompanionController = false
            )
        )
    }

    @Test
    fun `automotive and notification controllers are accepted without a hardcoded package`() {
        assertTrue(
            BambooMediaSessionControllerPolicy.isAccepted(
                controllerPackageName = "com.example.car.media",
                sessionPackageName = "com.adrianrusu.pandawave",
                isMediaNotificationController = false,
                isAutomotiveController = true,
                isAutoCompanionController = false
            )
        )
        assertTrue(
            BambooMediaSessionControllerPolicy.isAccepted(
                controllerPackageName = "androidx.media3.session",
                sessionPackageName = "com.adrianrusu.pandawave",
                isMediaNotificationController = true,
                isAutomotiveController = false,
                isAutoCompanionController = false
            )
        )
    }

    @Test
    fun `unknown third party packages are rejected`() {
        assertFalse(
            BambooMediaSessionControllerPolicy.isAccepted(
                controllerPackageName = "com.unknown.controller",
                sessionPackageName = "com.adrianrusu.pandawave",
                isMediaNotificationController = false,
                isAutomotiveController = false,
                isAutoCompanionController = false
            )
        )
    }
}
