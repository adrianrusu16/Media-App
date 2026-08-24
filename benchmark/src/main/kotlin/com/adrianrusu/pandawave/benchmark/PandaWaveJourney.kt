package com.adrianrusu.pandawave.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.adrianrusu.pandawave"
private const val UI_TIMEOUT_MILLIS = 10_000L

internal fun MacrobenchmarkScope.openDestination(label: String) {
    val destination = checkNotNull(device.wait(Until.findObject(By.text(label)), UI_TIMEOUT_MILLIS)) {
        "Navigation destination '$label' was not visible"
    }
    destination.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollContent(repetitions: Int = 3) {
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(repetitions) {
        device.swipe(
            width * 3 / 4,
            height * 4 / 5,
            width * 3 / 4,
            height / 5,
            20
        )
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.runLibraryAndProfileJourney() {
    openDestination("Library")
    scrollContent()
    openDestination("Profile")
    scrollContent()
}
