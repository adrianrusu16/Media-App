package com.adrianrusu.pandawave.core.ui.audio.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.dp

@Composable
fun BambooAmbientVisualizer(
    amplitudes: FloatArray,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF8FE388),
    idleColor: Color = Color(0xFF2A3A28),
    intensity: Float = 1f
) {
    Canvas(modifier = modifier) {
        val barWidth = 6.dp.toPx()
        val gap = 4.dp.toPx()
        val barCount = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(0)
        if (barCount == 0) return@Canvas

        val resampledAmplitudes = resampleAmplitudes(amplitudes, barCount)
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val radius = barWidth / 2f
        val minHeight = size.height * 0.08f
        val maxHeight = size.height * 0.92f

        resampledAmplitudes.forEachIndexed { index, amplitude ->
            val shapedAmplitude = amplitude * amplitude * 0.35f + amplitude * 0.65f
            val barHeight = lerpFloat(
                start = minHeight,
                end = maxHeight,
                fraction = shapedAmplitude * intensity
            )
            val left = startX + index * (barWidth + gap)
            val top = (size.height - barHeight) / 2f
            val activeAlpha = lerpFloat(
                start = 0.35f,
                end = 0.95f,
                fraction = shapedAmplitude
            )
            val color = lerpColor(
                start = idleColor.copy(alpha = 0.45f),
                stop = activeColor.copy(alpha = activeAlpha),
                fraction = shapedAmplitude
            )

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

internal fun resampleAmplitudes(amplitudes: FloatArray, targetCount: Int): FloatArray {
    if (targetCount <= 0) return FloatArray(0)
    if (amplitudes.isEmpty()) return FloatArray(targetCount)
    if (targetCount == 1) return floatArrayOf(amplitudes.first().coerceIn(0f, 1f))
    if (amplitudes.size == targetCount) return FloatArray(targetCount) { amplitudes[it].coerceIn(0f, 1f) }

    return FloatArray(targetCount) { index ->
        val sourcePosition = index * (amplitudes.lastIndex.toFloat() / (targetCount - 1))
        val leftIndex = sourcePosition.toInt().coerceIn(0, amplitudes.lastIndex)
        val rightIndex = (leftIndex + 1).coerceIn(0, amplitudes.lastIndex)
        val fraction = sourcePosition - leftIndex
        lerpFloat(
            start = amplitudes[leftIndex],
            end = amplitudes[rightIndex],
            fraction = fraction
        ).coerceIn(0f, 1f)
    }
}
