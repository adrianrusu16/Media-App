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

    init {
        require(targetBands > 0) { "targetBands must be greater than zero" }
        require(attack in 0f..1f) { "attack must be between zero and one" }
        require(decay in 0f..1f) { "decay must be between zero and one" }
        require(noiseFloor in 0f..<1f) { "noiseFloor must be between zero inclusive and one exclusive" }
        previous = FloatArray(targetBands)
    }

    fun process(fft: ByteArray): FloatArray {
        if (fft.size < MIN_PACKED_FFT_SIZE || fft.size % COMPLEX_BIN_SIZE != 0) return FloatArray(0)

        val normalizedBins = decodeMagnitudes(fft)
            .mapToNormalizedAmplitudes()
        val targets = normalizedBins.resampleTo(targetBands)

        return FloatArray(targetBands) { index ->
            val target = targets[index]
            val coefficient = if (target > previous[index]) attack else decay
            val smoothed = previous[index] + (target - previous[index]) * coefficient
            previous[index] = smoothed.coerceIn(0f, 1f)
            previous[index]
        }
    }

    private fun decodeMagnitudes(fft: ByteArray): FloatArray {
        val complexBinCount = (fft.size - PACKED_HEADER_SIZE) / COMPLEX_BIN_SIZE
        return FloatArray(complexBinCount + PACKED_REAL_BIN_COUNT).also { magnitudes ->
            magnitudes[0] = abs(fft[0].toInt()).toFloat()

            repeat(complexBinCount) { complexIndex ->
                val packedIndex = PACKED_HEADER_SIZE + complexIndex * COMPLEX_BIN_SIZE
                magnitudes[complexIndex + 1] = hypot(
                    fft[packedIndex].toFloat(),
                    fft[packedIndex + 1].toFloat()
                )
            }

            magnitudes[magnitudes.lastIndex] = abs(fft[1].toInt()).toFloat()
        }
    }

    private fun FloatArray.mapToNormalizedAmplitudes(): FloatArray = FloatArray(size) { index ->
        val logarithmic = ln1p(this[index].coerceAtLeast(0f)) / LOGARITHMIC_CEILING
        ((logarithmic - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
    }

    private fun FloatArray.resampleTo(targetSize: Int): FloatArray {
        if (size == targetSize) return copyOf()
        if (targetSize == 1) return floatArrayOf(first().coerceIn(0f, 1f))

        return FloatArray(targetSize) { index ->
            val sourcePosition = index * (lastIndex.toFloat() / (targetSize - 1))
            val leftIndex = sourcePosition.toInt().coerceIn(0, lastIndex)
            val rightIndex = (leftIndex + 1).coerceIn(0, lastIndex)
            val fraction = sourcePosition - leftIndex
            (this[leftIndex] + (this[rightIndex] - this[leftIndex]) * fraction).coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val MIN_PACKED_FFT_SIZE = 2
        const val PACKED_HEADER_SIZE = 2
        const val PACKED_REAL_BIN_COUNT = 2
        const val COMPLEX_BIN_SIZE = 2
        const val DEFAULT_ATTACK = 0.65f
        const val DEFAULT_DECAY = 0.12f
        const val DEFAULT_NOISE_FLOOR = 0.06f
        val LOGARITHMIC_CEILING: Float = ln1p(hypot(128f, 128f))
    }
}
