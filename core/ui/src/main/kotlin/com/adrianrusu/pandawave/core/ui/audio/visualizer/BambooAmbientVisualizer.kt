package com.adrianrusu.pandawave.core.ui.audio.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerBarGap
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerBarRadius
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerBarWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerMaxBarHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerMinBarHeight

@Composable
fun BambooAmbientVisualizer(
    modifier: Modifier = Modifier,
    amplitudes: FloatArray,
    activeColor: Color? = null,
    idleColor: Color? = null,
    intensity: Float = 1f
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedActiveColor = activeColor ?: Color(tokens.colors.ambientVisualizerActive)
    val resolvedIdleColor = idleColor ?: Color(tokens.colors.ambientVisualizerIdle)
    val barWidthToken = tokens.components.ambientVisualizerBarWidth
    val gapToken = tokens.components.ambientVisualizerBarGap
    val radiusToken = tokens.components.ambientVisualizerBarRadius
    val minHeightToken = tokens.components.ambientVisualizerMinBarHeight
    val maxHeightToken = tokens.components.ambientVisualizerMaxBarHeight
    val idleAlpha = tokens.colors.ambientVisualizerIdleAlpha
    val activeMinAlpha = tokens.colors.ambientVisualizerActiveMinAlpha
    val activeMaxAlpha = tokens.colors.ambientVisualizerActiveMaxAlpha

    Canvas(modifier = modifier) {
        val barWidth = barWidthToken.toPx()
        val gap = gapToken.toPx()
        val barCount = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(0)
        if (barCount == 0) return@Canvas

        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val radius = radiusToken.toPx()
        val minHeight = minHeightToken.toPx().coerceAtMost(size.height)
        val maxHeight = maxHeightToken.toPx().coerceIn(minHeight, size.height)

        repeat(barCount) { index ->
            val amplitude = sampleAmplitude(amplitudes, targetCount = barCount, index = index)
            val shapedAmplitude = amplitude * amplitude * 0.35f + amplitude * 0.65f
            val barHeight = lerpFloat(
                start = minHeight,
                end = maxHeight,
                fraction = shapedAmplitude * intensity
            )
            val left = startX + index * (barWidth + gap)
            val top = (size.height - barHeight) / 2f
            val activeAlpha = lerpFloat(
                start = activeMinAlpha,
                end = activeMaxAlpha,
                fraction = shapedAmplitude
            )
            val color = lerpColor(
                start = resolvedIdleColor.copy(alpha = idleAlpha),
                stop = resolvedActiveColor.copy(alpha = activeAlpha),
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
    return FloatArray(targetCount) { index -> sampleAmplitude(amplitudes, targetCount, index) }
}

internal fun sampleAmplitude(amplitudes: FloatArray, targetCount: Int, index: Int): Float {
    if (targetCount <= 0 || amplitudes.isEmpty()) return 0f
    val targetIndex = index.coerceIn(0, targetCount - 1)
    if (targetCount == 1) return amplitudes.first().coerceIn(0f, 1f)
    if (amplitudes.size == targetCount) return amplitudes[targetIndex].coerceIn(0f, 1f)

    val sourcePosition = targetIndex * (amplitudes.lastIndex.toFloat() / (targetCount - 1))
    val leftIndex = sourcePosition.toInt().coerceIn(0, amplitudes.lastIndex)
    val rightIndex = (leftIndex + 1).coerceIn(0, amplitudes.lastIndex)
    val fraction = sourcePosition - leftIndex
    return lerpFloat(
        start = amplitudes[leftIndex],
        end = amplitudes[rightIndex],
        fraction = fraction
    ).coerceIn(0f, 1f)
}
