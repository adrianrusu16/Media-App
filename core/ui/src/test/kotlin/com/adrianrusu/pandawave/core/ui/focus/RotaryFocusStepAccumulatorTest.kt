package com.adrianrusu.pandawave.core.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RotaryFocusStepAccumulatorTest {
    @Test
    fun `positive threshold emits next once`() {
        val accumulator = RotaryFocusStepAccumulator(thresholdPixels = 20F)

        assertNull(accumulator.add(8F))
        assertEquals(RotaryFocusStep.Next, accumulator.add(12F))
        assertNull(accumulator.add(1F))
    }

    @Test
    fun `negative threshold emits previous once`() {
        val accumulator = RotaryFocusStepAccumulator(thresholdPixels = 20F)

        assertNull(accumulator.add(-8F))
        assertEquals(RotaryFocusStep.Previous, accumulator.add(-12F))
        assertNull(accumulator.add(-1F))
    }

    @Test
    fun `direction reversal resets partial accumulation`() {
        val accumulator = RotaryFocusStepAccumulator(thresholdPixels = 20F)

        assertNull(accumulator.add(15F))
        assertNull(accumulator.add(-10F))
        assertEquals(RotaryFocusStep.Previous, accumulator.add(-10F))
    }

    @Test
    fun `zero delta preserves partial accumulation`() {
        val accumulator = RotaryFocusStepAccumulator(thresholdPixels = 20F)

        assertNull(accumulator.add(15F))
        assertNull(accumulator.add(0F))
        assertEquals(RotaryFocusStep.Next, accumulator.add(5F))
    }

    @Test
    fun `non positive threshold is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RotaryFocusStepAccumulator(thresholdPixels = 0F)
        }
    }
}
