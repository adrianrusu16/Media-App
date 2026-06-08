package com.adrianrusu.mediaapp.core.designsystem.tokens

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

data class PandaWaveDesignTokens(
    val colors: PandaWaveColorTokens,
    val spacing: PandaWaveSpacingTokens,
    val shape: PandaWaveShapeTokens,
    val sizing: PandaWaveSizingTokens,
    val elevation: PandaWaveElevationTokens,
    val restrictions: PandaWaveRestrictionTokens
)

data class PandaWaveColorTokens(
    @param:ColorInt val primary: Int,
    @param:ColorInt val onPrimary: Int,
    @param:ColorInt val secondary: Int,
    @param:ColorInt val onSecondary: Int,
    @param:ColorInt val surface: Int,
    @param:ColorInt val onSurface: Int,
    @param:ColorInt val surfaceVariant: Int,
    @param:ColorInt val onSurfaceVariant: Int,
    @param:ColorInt val error: Int,
    @param:ColorInt val onError: Int
)

data class PandaWaveSpacingTokens(val xsPx: Int, val smPx: Int, val mdPx: Int, val lgPx: Int, val xlPx: Int)

data class PandaWaveShapeTokens(val smallCornerPx: Int, val mediumCornerPx: Int, val miniPlayerHeightPx: Int)

data class PandaWaveSizingTokens(val touchTargetMdPx: Int, val touchTargetLgPx: Int)

data class PandaWaveElevationTokens(val cardRestingPx: Int)

data class PandaWaveRestrictionTokens(
    val maxBrowseColumnsUnrestricted: Int,
    val maxBrowseColumnsRestricted: Int,
    val maxVisibleActionsRestricted: Int
)

val LocalPandaWaveDesignTokens = staticCompositionLocalOf<PandaWaveDesignTokens> {
    error("PandaWaveDesignTokens are not available. Wrap content in PandaWaveTheme.")
}

val PandaWaveSpacingTokens.xs: Dp
    @Composable get() = xsPx.toDp()

val PandaWaveSpacingTokens.sm: Dp
    @Composable get() = smPx.toDp()

val PandaWaveSpacingTokens.md: Dp
    @Composable get() = mdPx.toDp()

val PandaWaveSpacingTokens.lg: Dp
    @Composable get() = lgPx.toDp()

val PandaWaveSpacingTokens.xl: Dp
    @Composable get() = xlPx.toDp()

val PandaWaveShapeTokens.smallCorner: Dp
    @Composable get() = smallCornerPx.toDp()

val PandaWaveShapeTokens.mediumCorner: Dp
    @Composable get() = mediumCornerPx.toDp()

val PandaWaveShapeTokens.miniPlayerHeight: Dp
    @Composable get() = miniPlayerHeightPx.toDp()

val PandaWaveSizingTokens.touchTargetMd: Dp
    @Composable get() = touchTargetMdPx.toDp()

val PandaWaveSizingTokens.touchTargetLg: Dp
    @Composable get() = touchTargetLgPx.toDp()

val PandaWaveElevationTokens.cardResting: Dp
    @Composable get() = cardRestingPx.toDp()

@Composable
private fun Int.toDp(): Dp = with(LocalDensity.current) {
    this@toDp.toDp()
}
