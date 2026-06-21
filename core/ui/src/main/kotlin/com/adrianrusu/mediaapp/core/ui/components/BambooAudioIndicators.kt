package com.adrianrusu.mediaapp.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.res.stringResource
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceBarGap
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceBarIdleHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceBarWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceIndicatorBarsHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceIndicatorBarsWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.voiceIndicatorBorderWidth
import com.adrianrusu.mediaapp.core.ui.R
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BambooVoiceIndicator(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.pandawave_voice_prompt),
    isActive: Boolean = true
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                tokens.components.voiceIndicatorBorderWidth,
                MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        BambooVoiceBars(
            isActive = isActive,
            modifier = Modifier.size(
                width = tokens.components.voiceIndicatorBarsWidth,
                height = tokens.components.voiceIndicatorBarsHeight
            )
        )

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun BambooVoiceBars(modifier: Modifier = Modifier, isActive: Boolean, barOffset: Int = 2) {
    val tokens = LocalPandaWaveDesignTokens.current
    val voiceBarWidth = tokens.components.voiceBarWidth
    val voiceBarGap = tokens.components.voiceBarGap
    val voiceBarIdleHeight = tokens.components.voiceBarIdleHeight
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = IDLE_COLOR_ALPHA)
    val infiniteTransition = rememberInfiniteTransition(label = "bambooVoiceClock")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = tokens.motion.voiceCycleMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "clock"
    )

    val activeLevel by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = tokens.motion.voiceActivationMillis,
            easing = LinearEasing
        ),
        label = "activeLevel"
    )

    val phases = remember(barOffset) {
        FloatArray(3) { index ->
            val indexWithOffset = (index + barOffset) % 3
            indexWithOffset * 0.85f
        }
    }

    Canvas(modifier = modifier) {
        val barCount = 3
        val barWidth = voiceBarWidth.toPx()
        val gap = voiceBarGap.toPx()
        val radius = barWidth / 2f

        val idleHeight = voiceBarIdleHeight.toPx()
        val maxHeight = size.height

        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f

        val cycle = progress * PI.toFloat() * 2f

        val idleWindow = idleEnvelope(progress)

        val visualActivity =
            activeLevel *
                lerpFloat(
                    start = 0.12f,
                    end = 1f,
                    fraction = 1f - idleWindow
                )

        val color = lerpColor(
            start = idleColor,
            stop = activeColor,
            fraction = visualActivity
        )

        val breathing = normalizedSin(cycle * 0.25f)
        val amplitude = 0.62f + breathing * 0.18f

        for (index in 0 until barCount) {
            val phase = phases[index]

            val wave = normalizedSin(cycle + phase)
            val secondaryWave = normalizedSin(cycle * 1.8f + phase + 0.4f)
            val detailWave = normalizedSin(cycle * 3.1f + phase + 1.2f)
            val flickerWave = normalizedSin(cycle * 4.4f + phase + 0.7f)

            val mixed = wave * 0.45f +
                secondaryWave * 0.27f +
                detailWave * 0.18f +
                flickerWave * 0.10f

            val activeHeightFraction = 0.22f + mixed * amplitude

            val activeHeight = lerpFloat(
                start = idleHeight,
                end = maxHeight,
                fraction = activeHeightFraction.coerceIn(0f, 1f)
            )

            val barHeight = lerpFloat(
                start = idleHeight,
                end = activeHeight,
                fraction = visualActivity
            )

            val left = startX + index * (barWidth + gap)
            val top = (size.height - barHeight) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

private fun idleEnvelope(progress: Float): Float = when {
    progress < 0.50f -> 0f

    progress < 0.60f ->
        (progress - 0.50f) / 0.10f

    progress < 0.80f ->
        1f

    progress < 0.90f ->
        1f - ((progress - 0.80f) / 0.10f)

    else -> 0f
}

private fun normalizedSin(value: Float): Float = ((sin(value) + 1f) / 2f)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private const val IDLE_COLOR_ALPHA = 0.28f
