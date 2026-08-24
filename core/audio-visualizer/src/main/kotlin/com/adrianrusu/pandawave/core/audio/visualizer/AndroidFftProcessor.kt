package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln1p

internal class AndroidFftProcessor(
    private val targetBands: Int,
    private val attack: Float = DEFAULT_ATTACK,
    private val decay: Float = DEFAULT_DECAY,
    private val noiseFloor: Float = DEFAULT_NOISE_FLOOR
) {
    private val previous: FloatArray
    private var magnitudesScratch = FloatArray(0)
    private var normalizedScratch = FloatArray(0)
    private val targetScratch: FloatArray

    init {
        require(targetBands > 0) { "targetBands must be greater than zero" }
        require(attack in 0f..1f) { "attack must be between zero and one" }
        require(decay in 0f..1f) { "decay must be between zero and one" }
        require(noiseFloor in 0f..<1f) { "noiseFloor must be between zero inclusive and one exclusive" }
        previous = FloatArray(targetBands)
        targetScratch = FloatArray(targetBands)
    }

    fun process(fft: ByteArray): FloatArray {
        if (fft.size < MIN_PACKED_FFT_SIZE || fft.size % COMPLEX_BIN_SIZE != 0) return EMPTY_FRAME

        val magnitudeCount = magnitudeCount(fft)
        val magnitudes = magnitudesScratch.ensureCapacity(magnitudeCount).also { magnitudesScratch = it }
        val normalized = normalizedScratch.ensureCapacity(magnitudeCount).also { normalizedScratch = it }

        decodeMagnitudes(fft = fft, out = magnitudes)
        mapToNormalizedAmplitudes(source = magnitudes, size = magnitudeCount, out = normalized)
        resampleTo(source = normalized, sourceSize = magnitudeCount, out = targetScratch)

        return FloatArray(targetBands) { index ->
            val target = targetScratch[index]
            val coefficient = if (target > previous[index]) attack else decay
            val smoothed = previous[index] + (target - previous[index]) * coefficient
            previous[index] = smoothed.coerceIn(0f, 1f)
            previous[index]
        }
    }

    private fun magnitudeCount(fft: ByteArray): Int {
        val complexBinCount = (fft.size - PACKED_HEADER_SIZE) / COMPLEX_BIN_SIZE
        return complexBinCount + PACKED_REAL_BIN_COUNT
    }

    private fun decodeMagnitudes(fft: ByteArray, out: FloatArray) {
        val complexBinCount = (fft.size - PACKED_HEADER_SIZE) / COMPLEX_BIN_SIZE
        out[0] = abs(fft[0].toInt()).toFloat()

        repeat(complexBinCount) { complexIndex ->
            val packedIndex = PACKED_HEADER_SIZE + complexIndex * COMPLEX_BIN_SIZE
            out[complexIndex + 1] = hypot(
                fft[packedIndex].toFloat(),
                fft[packedIndex + 1].toFloat()
            )
        }

        out[complexBinCount + 1] = abs(fft[1].toInt()).toFloat()
    }

    private fun mapToNormalizedAmplitudes(source: FloatArray, size: Int, out: FloatArray) {
        repeat(size) { index ->
            val logarithmic = ln1p(source[index].coerceAtLeast(0f)) / LOGARITHMIC_CEILING
            out[index] = ((logarithmic - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
        }
    }

    private fun resampleTo(source: FloatArray, sourceSize: Int, out: FloatArray) {
        if (sourceSize == targetBands) {
            repeat(targetBands) { index -> out[index] = source[index].coerceIn(0f, 1f) }
            return
        }
        if (targetBands == 1) {
            out[0] = source[0].coerceIn(0f, 1f)
            return
        }

        val lastSourceIndex = sourceSize - 1
        repeat(targetBands) { index ->
            val sourcePosition = index * (lastSourceIndex.toFloat() / (targetBands - 1))
            val leftIndex = sourcePosition.toInt().coerceIn(0, lastSourceIndex)
            val rightIndex = (leftIndex + 1).coerceIn(0, lastSourceIndex)
            val fraction = sourcePosition - leftIndex
            out[index] = (source[leftIndex] + (source[rightIndex] - source[leftIndex]) * fraction).coerceIn(0f, 1f)
        }
    }

    private fun FloatArray.ensureCapacity(size: Int): FloatArray =
        if (this.size >= size) this else FloatArray(size)

    private companion object {
        const val MIN_PACKED_FFT_SIZE = 2
        const val PACKED_HEADER_SIZE = 2
        const val PACKED_REAL_BIN_COUNT = 2
        const val COMPLEX_BIN_SIZE = 2
        const val DEFAULT_ATTACK = 0.65f
        const val DEFAULT_DECAY = 0.12f
        const val DEFAULT_NOISE_FLOOR = 0.06f
        val LOGARITHMIC_CEILING: Float = ln1p(hypot(128f, 128f))
        val EMPTY_FRAME = FloatArray(0)
    }
}
