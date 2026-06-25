package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.coroutines.CoroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SleepingAmbientAmplitudeSource internal constructor(
    private val bandCount: Int,
    private val framesPerSecond: Int,
    private val intensity: Float,
    seed: Int,
    coroutineContext: CoroutineContext
) : AmbientAmplitudeSource {
    constructor() : this(
        bandCount = DEFAULT_BAND_COUNT,
        framesPerSecond = DEFAULT_FRAMES_PER_SECOND,
        intensity = DEFAULT_INTENSITY,
        seed = DEFAULT_SEED,
        coroutineContext = Dispatchers.Default
    )

    private val mutableAmplitudes = MutableStateFlow(FloatArray(bandCount))
    override val amplitudes: StateFlow<FloatArray> = mutableAmplitudes.asStateFlow()

    private val sourceJob = SupervisorJob()
    private val scope = CoroutineScope(sourceJob + coroutineContext)
    private var animationJob: Job? = null
    private var elapsedSeconds = 0F
    private var closed = false

    private val random = Random(seed)
    private val phases = FloatArray(bandCount) { random.nextFloat() * TWO_PI }
    private val speeds = FloatArray(bandCount) { 0.8F + random.nextFloat() * 1.6F }
    private val previousValues = FloatArray(bandCount)

    override fun start() {
        if (closed || animationJob?.isActive == true) return

        val safeFramesPerSecond = framesPerSecond.coerceAtLeast(1)
        val frameDelayMillis = 1_000L / safeFramesPerSecond
        animationJob = scope.launch {
            while (isActive) {
                elapsedSeconds += 1F / safeFramesPerSecond
                mutableAmplitudes.value = generateFrame(elapsedSeconds)
                delay(frameDelayMillis.milliseconds)
            }
        }
    }

    override fun stop() {
        animationJob?.cancel()
        animationJob = null
        previousValues.fill(0F)
        mutableAmplitudes.value = FloatArray(bandCount)
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        scope.cancel()
    }

    private fun generateFrame(timeSeconds: Float): FloatArray {
        val values = FloatArray(bandCount)
        val globalBeat = normalizedSin(timeSeconds * 2.4F)
        val slowBreath = normalizedSin(timeSeconds * 0.45F)
        val sweepPosition = normalizedSin(timeSeconds * 0.28F)

        for (index in 0 until bandCount) {
            val x = if (bandCount <= 1) 0F else index / (bandCount - 1F)
            val frequencyWeight = lerpFloat(
                start = 1F,
                end = 0.45F,
                fraction = x
            )
            val primary = normalizedSin(timeSeconds * speeds[index] + phases[index])
            val secondary = normalizedSin(
                timeSeconds * speeds[index] * 2.15F + phases[index] * 0.7F
            )
            val detail = normalizedSin(
                timeSeconds * speeds[index] * 3.6F + phases[index] * 1.4F
            )
            val distanceFromSweep = abs(x - sweepPosition)
            val sweepBoost = (1F - distanceFromSweep * 3.2F).coerceIn(0F, 1F)
            val rawTarget =
                primary * 0.38F +
                    secondary * 0.24F +
                    detail * 0.10F +
                    globalBeat * 0.18F +
                    sweepBoost * 0.28F +
                    slowBreath * 0.08F
            val shapedTarget = rawTarget
                .coerceIn(0F, 1F)
                .let { it * it * 0.25F + it * 0.75F }
            val target = (0.05F + shapedTarget * frequencyWeight * intensity).coerceIn(0F, 1F)
            val smoothing = if (target > previousValues[index]) FAST_ATTACK else SLOW_DECAY
            val smoothed = lerpFloat(
                start = previousValues[index],
                end = target,
                fraction = smoothing
            )

            previousValues[index] = smoothed
            values[index] = smoothed
        }

        return values
    }

    private companion object {
        const val DEFAULT_BAND_COUNT = 64
        const val DEFAULT_FRAMES_PER_SECOND = 30
        const val DEFAULT_INTENSITY = 0.5F
        const val DEFAULT_SEED = 42
        const val FAST_ATTACK = 0.42F
        const val SLOW_DECAY = 0.16F
        const val TWO_PI = (PI * 2.0).toFloat()
    }
}

private fun normalizedSin(value: Float): Float = (sin(value) + 1F) / 2F

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    val safeFraction = fraction.coerceIn(0F, 1F)
    return start + (end - start) * safeFraction
}
