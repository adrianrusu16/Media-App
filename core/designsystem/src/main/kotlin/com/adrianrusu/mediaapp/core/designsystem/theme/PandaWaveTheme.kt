package com.adrianrusu.mediaapp.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.adrianrusu.mediaapp.core.designsystem.tokens.PandaWaveColorTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.ResourceDesignTokenProvider

@Composable
fun PandaWaveTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tokens = remember(context) {
        ResourceDesignTokenProvider(context).load()
    }

    MaterialTheme(
        colorScheme = tokens.colors.toColorScheme(darkTheme),
        content = content
    )
}

private fun PandaWaveColorTokens.toColorScheme(darkTheme: Boolean): ColorScheme {
    val primary = Color(this.primary)
    val onPrimary = Color(this.onPrimary)
    val secondary = Color(this.secondary)
    val onSecondary = Color(this.onSecondary)
    val surface = Color(this.surface)
    val onSurface = Color(this.onSurface)
    val surfaceVariant = Color(this.surfaceVariant)
    val onSurfaceVariant = Color(this.onSurfaceVariant)
    val error = Color(this.error)
    val onError = Color(this.onError)

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError
        )
    }
}
