package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SleepingAmbientAmplitudeSourceTest {
    @Test
    fun `start emits deterministic bounded sleeping amplitudes`() = runTest {
        val source = SleepingAmbientAmplitudeSource(
            bandCount = 16,
            framesPerSecond = 30,
            intensity = 0.5F,
            seed = 7,
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        source.start()
        runCurrent()

        val frame = source.amplitudes.value
        assertTrue(frame.any { it > 0F })
        assertTrue(frame.all { it in 0F..1F })
        source.close()
    }

    @Test
    fun `duplicate start is idempotent and stop clears the frame`() = runTest {
        val source = SleepingAmbientAmplitudeSource(
            bandCount = 8,
            framesPerSecond = 30,
            intensity = 0.5F,
            seed = 11,
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        source.start()
        source.start()
        runCurrent()
        source.stop()

        assertContentEquals(FloatArray(8), source.amplitudes.value)
        source.close()
    }

    @Test
    fun `closed source cannot restart`() = runTest {
        val source = SleepingAmbientAmplitudeSource(
            bandCount = 8,
            framesPerSecond = 30,
            intensity = 0.5F,
            seed = 13,
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        source.close()
        source.start()
        runCurrent()

        assertContentEquals(FloatArray(8), source.amplitudes.value)
    }
}
