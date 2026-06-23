package com.adrianrusu.pandawave.core.ui.audio.visualizer

import kotlin.math.sin

fun normalizedSin(value: Float): Float = ((sin(value) + 1f) / 2f)

fun lerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
