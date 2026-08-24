package com.adrianrusu.pandawave.core.ui.audio.visualizer

import kotlin.test.Test
import kotlin.test.assertContentEquals

class AmbientAmplitudeResamplerTest {
    @Test
    fun `returns empty amplitudes when no bars fit`() {
        assertContentEquals(FloatArray(0), resampleAmplitudes(floatArrayOf(0.5f), targetCount = 0))
    }

    @Test
    fun `returns one clamped amplitude when one bar fits`() {
        assertContentEquals(floatArrayOf(1f), resampleAmplitudes(floatArrayOf(2f), targetCount = 1))
    }

    @Test
    fun `interpolates amplitudes to the requested bar count`() {
        assertContentEquals(
            floatArrayOf(0f, 0.5f, 1f),
            resampleAmplitudes(floatArrayOf(0f, 1f), targetCount = 3)
        )
    }

    @Test
    fun `samples amplitude for one bar without materializing the full bar frame`() {
        assertContentEquals(
            floatArrayOf(0f, 0.5f, 1f),
            FloatArray(3) { index ->
                sampleAmplitude(amplitudes = floatArrayOf(0f, 1f), targetCount = 3, index = index)
            }
        )
    }
}
