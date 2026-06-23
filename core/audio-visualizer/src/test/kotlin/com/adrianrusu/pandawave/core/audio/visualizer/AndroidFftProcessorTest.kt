package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AndroidFftProcessorTest {
    @Test
    fun `decodes dc nyquist and complex bins into finite normalized amplitudes`() {
        val processor = AndroidFftProcessor(targetBands = 4)

        val frame = processor.process(byteArrayOf(8, 4, 3, 4, 0, 0, 6, 8))

        assertEquals(4, frame.size)
        assertTrue(frame.all { it.isFinite() && it in 0f..1f })
    }

    @Test
    fun `decay is slower than attack`() {
        val processor = AndroidFftProcessor(targetBands = 2, attack = 0.5f, decay = 0.1f)

        val loud = processor.process(byteArrayOf(100, 0, 100, 0))
        val quiet = processor.process(ByteArray(4))

        assertTrue(quiet[0] > 0f)
        assertTrue(quiet[0] < loud[0])
    }

    @Test
    fun `preserves dc complex and nyquist bin order`() {
        val processor = AndroidFftProcessor(
            targetBands = 3,
            attack = 1f,
            decay = 1f,
            noiseFloor = 0f
        )

        val frame = processor.process(byteArrayOf(8, 4, 3, 4))

        assertTrue(frame[0] > frame[1])
        assertTrue(frame[1] > frame[2])
    }

    @Test
    fun `returns a new frame for every accepted fft sample`() {
        val processor = AndroidFftProcessor(targetBands = 2)

        val first = processor.process(byteArrayOf(8, 4, 3, 4))
        val second = processor.process(byteArrayOf(8, 4, 3, 4))

        assertNotSame(first, second)
    }
}
