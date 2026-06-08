package com.adrianrusu.mediaapp.core.designsystem.theme

import android.content.res.Configuration
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
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference

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
fun PandaWaveTheme(
    darkTheme: Boolean,
    themePreference: PandaWaveThemePreference = PandaWaveThemePreference.SystemDefault,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeProfile = remember(themePreference, darkTheme) {
        themePreference.toThemeProfile(systemDark = darkTheme)
    }
    val resourceContext = remember(context, themeProfile.isDark) {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (themeProfile.isDark) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                }
        }
        context.createConfigurationContext(configuration)
    }
    val tokens = remember(resourceContext) {
        ResourceDesignTokenProvider(resourceContext).load()
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

fun PandaWaveThemePreference.toThemeProfile(systemDark: Boolean): PandaWaveThemeProfile = when (this) {
    PandaWaveThemePreference.SystemDefault ->
        if (systemDark) {
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

    PandaWaveThemePreference.BambooGroveLight ->
        PandaWaveThemeProfile(
            id = PandaWaveThemeId.BambooGroveLight,
            isDark = false
        )

    PandaWaveThemePreference.MoonlitBambooDark ->
        PandaWaveThemeProfile(
            id = PandaWaveThemeId.MoonlitBambooDark,
            isDark = true
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
