package com.adrianrusu.pandawave.core.ui.focus

import kotlin.math.abs

enum class RotaryFocusStep {
    Previous,
    Next
}

class RotaryFocusStepAccumulator(private val thresholdPixels: Float) {
    private var accumulatedPixels = 0F

    init {
        require(thresholdPixels.isFinite() && thresholdPixels > 0F) {
            "Rotary threshold must be finite and greater than zero."
        }
    }

    fun add(deltaPixels: Float): RotaryFocusStep? {
        if (!deltaPixels.isFinite() || deltaPixels == 0F) return null
        if (accumulatedPixels * deltaPixels < 0F) accumulatedPixels = 0F

        accumulatedPixels += deltaPixels
        if (abs(accumulatedPixels) < thresholdPixels) return null

        val step = if (accumulatedPixels > 0F) {
            RotaryFocusStep.Next
        } else {
            RotaryFocusStep.Previous
        }
        accumulatedPixels = 0F
        return step
    }
}
