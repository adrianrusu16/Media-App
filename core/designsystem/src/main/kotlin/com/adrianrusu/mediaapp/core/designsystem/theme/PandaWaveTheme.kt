package com.adrianrusu.mediaapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.PandaWaveColorTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.ResourceDesignTokenProvider
import com.adrianrusu.mediaapp.core.designsystem.tokens.mediumCorner
import com.adrianrusu.mediaapp.core.designsystem.tokens.smallCorner

enum class PandaWaveThemeId(val displayName: String) {
    BambooGroveLight(displayName = "Bamboo Grove Light"),
    MoonlitBambooDark(displayName = "Moonlit Bamboo Dark")
}

data class PandaWaveThemeProfile(val id: PandaWaveThemeId, val isDark: Boolean)

val LocalPandaWaveThemeProfile = staticCompositionLocalOf {
    PandaWaveThemeProfile(
        id = PandaWaveThemeId.BambooGroveLight,
        isDark = false
    )
}

@Composable
fun PandaWaveTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themeProfile = remember(darkTheme) {
        if (darkTheme) {
            PandaWaveThemeProfile(
                id = PandaWaveThemeId.MoonlitBambooDark,
                isDark = true
            )
        } else {
            PandaWaveThemeProfile(
                id = PandaWaveThemeId.BambooGroveLight,
                isDark = false
            )
        }
    }
    val tokens = remember(context, darkTheme) {
        ResourceDesignTokenProvider(context).load()
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(tokens.shape.smallCorner),
        small = RoundedCornerShape(tokens.shape.smallCorner),
        medium = RoundedCornerShape(tokens.shape.mediumCorner),
        large = RoundedCornerShape(tokens.shape.mediumCorner),
        extraLarge = RoundedCornerShape(tokens.shape.mediumCorner)
    )

    CompositionLocalProvider(
        LocalPandaWaveDesignTokens provides tokens,
        LocalPandaWaveThemeProfile provides themeProfile
    ) {
        MaterialTheme(
            colorScheme = tokens.colors.toColorScheme(themeProfile.isDark),
            shapes = shapes,
            content = content
        )
    }
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
