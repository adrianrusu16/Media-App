package com.adrianrusu.pandawave.core.media.adapter.playback

/**
 * Trust policy for Media3 controllers.
 *
 * Accept the host app, Media3's notification controller, and Automotive
 * controllers discovered through Media3's own classifier — never a hardcoded
 * `com.google.android.car.media` package name.
 */
internal object BambooMediaSessionControllerPolicy {
    fun isAccepted(
        controllerPackageName: String?,
        sessionPackageName: String,
        isMediaNotificationController: Boolean,
        isAutomotiveController: Boolean,
        isAutoCompanionController: Boolean
    ): Boolean {
        if (controllerPackageName == sessionPackageName) return true
        return isMediaNotificationController || isAutomotiveController || isAutoCompanionController
    }
}
