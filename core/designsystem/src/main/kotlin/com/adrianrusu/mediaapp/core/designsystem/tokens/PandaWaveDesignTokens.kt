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
    val focus: PandaWaveFocusTokens,
    val components: PandaWaveComponentTokens,
    val layout: PandaWaveLayoutTokens,
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

data class PandaWaveFocusTokens(val outlineWidthPx: Int, val outlinePaddingPx: Int)

data class PandaWaveComponentTokens(
    val iconSmallPx: Int,
    val iconMediumPx: Int,
    val iconLargePx: Int,
    val rotaryStepThresholdPx: Int,
    val navigationLogoSizePx: Int,
    val navigationItemHeightPx: Int,
    val navigationItemSpacingPx: Int,
    val navigationSelectedIndicatorInsetPx: Int,
    val mediaTileCompactMinWidthPx: Int,
    val mediaTileCompactMaxWidthPx: Int,
    val mediaTileCompactMinHeightPx: Int,
    val mediaTileStandardMinWidthPx: Int,
    val mediaTileStandardMaxWidthPx: Int,
    val mediaTileStandardMinHeightPx: Int,
    val mediaTileCompactArtworkHeightPx: Int,
    val mediaTileStandardArtworkHeightPx: Int,
    val mediaRowArtworkSizePx: Int,
    val mediaRowMinHeightPx: Int,
    val categoryCardMinWidthPx: Int,
    val categoryCardMaxWidthPx: Int,
    val categoryCardMinHeightPx: Int,
    val cardPaddingPx: Int,
    val actionableCardMinHeightPx: Int,
    val miniPlayerArtworkSizePx: Int,
    val miniPlayerTransportButtonSizePx: Int,
    val miniPlayerInternalSpacingPx: Int,
    val progressTrackHeightPx: Int,
    val progressThumbSizePx: Int,
    val volumeControlHeightPx: Int,
    val waveformHeightPx: Int,
    val waveformBarWidthPx: Int,
    val voiceIndicatorBorderWidthPx: Int,
    val voiceIndicatorBarsWidthPx: Int,
    val voiceIndicatorBarsHeightPx: Int,
    val voiceBarWidthPx: Int,
    val voiceBarGapPx: Int,
    val voiceBarIdleHeightPx: Int,
    val nowPlayingSecondaryTransportSizePx: Int,
    val nowPlayingTransportSpacingPx: Int,
    val nowPlayingFooterHeightPx: Int,
    val nowPlayingQuickActionWidthPx: Int,
    val nowPlayingQuickActionHeightPx: Int
)

data class PandaWaveLayoutTokens(
    val appContentPaddingPx: Int,
    val navigationRailWidthPx: Int,
    val navigationSelectedIndicatorWidthPx: Int,
    val navigationSelectedIndicatorHeightPx: Int,
    val nowPlayingArtworkCompactPx: Int,
    val nowPlayingArtworkStandardPx: Int,
    val nowPlayingPrimaryButtonPx: Int,
    val nowPlayingCompactHeightThresholdPx: Int,
    val nowPlayingScrollHeightThresholdPx: Int
)

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

val PandaWaveFocusTokens.outlineWidth: Dp
    @Composable get() = outlineWidthPx.toDp()

val PandaWaveFocusTokens.outlinePadding: Dp
    @Composable get() = outlinePaddingPx.toDp()

val PandaWaveComponentTokens.iconSmall: Dp
    @Composable get() = iconSmallPx.toDp()

val PandaWaveComponentTokens.iconMedium: Dp
    @Composable get() = iconMediumPx.toDp()

val PandaWaveComponentTokens.iconLarge: Dp
    @Composable get() = iconLargePx.toDp()

val PandaWaveComponentTokens.rotaryStepThreshold: Dp
    @Composable get() = rotaryStepThresholdPx.toDp()

val PandaWaveComponentTokens.navigationLogoSize: Dp
    @Composable get() = navigationLogoSizePx.toDp()

val PandaWaveComponentTokens.navigationItemHeight: Dp
    @Composable get() = navigationItemHeightPx.toDp()

val PandaWaveComponentTokens.navigationItemSpacing: Dp
    @Composable get() = navigationItemSpacingPx.toDp()

val PandaWaveComponentTokens.navigationSelectedIndicatorInset: Dp
    @Composable get() = navigationSelectedIndicatorInsetPx.toDp()

val PandaWaveComponentTokens.mediaTileCompactMinWidth: Dp
    @Composable get() = mediaTileCompactMinWidthPx.toDp()

val PandaWaveComponentTokens.mediaTileCompactMaxWidth: Dp
    @Composable get() = mediaTileCompactMaxWidthPx.toDp()

val PandaWaveComponentTokens.mediaTileCompactMinHeight: Dp
    @Composable get() = mediaTileCompactMinHeightPx.toDp()

val PandaWaveComponentTokens.mediaTileStandardMinWidth: Dp
    @Composable get() = mediaTileStandardMinWidthPx.toDp()

val PandaWaveComponentTokens.mediaTileStandardMaxWidth: Dp
    @Composable get() = mediaTileStandardMaxWidthPx.toDp()

val PandaWaveComponentTokens.mediaTileStandardMinHeight: Dp
    @Composable get() = mediaTileStandardMinHeightPx.toDp()

val PandaWaveComponentTokens.mediaTileCompactArtworkHeight: Dp
    @Composable get() = mediaTileCompactArtworkHeightPx.toDp()

val PandaWaveComponentTokens.mediaTileStandardArtworkHeight: Dp
    @Composable get() = mediaTileStandardArtworkHeightPx.toDp()

val PandaWaveComponentTokens.mediaRowArtworkSize: Dp
    @Composable get() = mediaRowArtworkSizePx.toDp()

val PandaWaveComponentTokens.mediaRowMinHeight: Dp
    @Composable get() = mediaRowMinHeightPx.toDp()

val PandaWaveComponentTokens.categoryCardMinWidth: Dp
    @Composable get() = categoryCardMinWidthPx.toDp()

val PandaWaveComponentTokens.categoryCardMaxWidth: Dp
    @Composable get() = categoryCardMaxWidthPx.toDp()

val PandaWaveComponentTokens.categoryCardMinHeight: Dp
    @Composable get() = categoryCardMinHeightPx.toDp()

val PandaWaveComponentTokens.cardPadding: Dp
    @Composable get() = cardPaddingPx.toDp()

val PandaWaveComponentTokens.actionableCardMinHeight: Dp
    @Composable get() = actionableCardMinHeightPx.toDp()

val PandaWaveComponentTokens.miniPlayerArtworkSize: Dp
    @Composable get() = miniPlayerArtworkSizePx.toDp()

val PandaWaveComponentTokens.miniPlayerTransportButtonSize: Dp
    @Composable get() = miniPlayerTransportButtonSizePx.toDp()

val PandaWaveComponentTokens.miniPlayerInternalSpacing: Dp
    @Composable get() = miniPlayerInternalSpacingPx.toDp()

val PandaWaveComponentTokens.progressTrackHeight: Dp
    @Composable get() = progressTrackHeightPx.toDp()

val PandaWaveComponentTokens.progressThumbSize: Dp
    @Composable get() = progressThumbSizePx.toDp()

val PandaWaveComponentTokens.volumeControlHeight: Dp
    @Composable get() = volumeControlHeightPx.toDp()

val PandaWaveComponentTokens.waveformHeight: Dp
    @Composable get() = waveformHeightPx.toDp()

val PandaWaveComponentTokens.waveformBarWidth: Dp
    @Composable get() = waveformBarWidthPx.toDp()

val PandaWaveComponentTokens.voiceIndicatorBorderWidth: Dp
    @Composable get() = voiceIndicatorBorderWidthPx.toDp()

val PandaWaveComponentTokens.voiceIndicatorBarsWidth: Dp
    @Composable get() = voiceIndicatorBarsWidthPx.toDp()

val PandaWaveComponentTokens.voiceIndicatorBarsHeight: Dp
    @Composable get() = voiceIndicatorBarsHeightPx.toDp()

val PandaWaveComponentTokens.voiceBarWidth: Dp
    @Composable get() = voiceBarWidthPx.toDp()

val PandaWaveComponentTokens.voiceBarGap: Dp
    @Composable get() = voiceBarGapPx.toDp()

val PandaWaveComponentTokens.voiceBarIdleHeight: Dp
    @Composable get() = voiceBarIdleHeightPx.toDp()

val PandaWaveComponentTokens.nowPlayingSecondaryTransportSize: Dp
    @Composable get() = nowPlayingSecondaryTransportSizePx.toDp()

val PandaWaveComponentTokens.nowPlayingTransportSpacing: Dp
    @Composable get() = nowPlayingTransportSpacingPx.toDp()

val PandaWaveComponentTokens.nowPlayingFooterHeight: Dp
    @Composable get() = nowPlayingFooterHeightPx.toDp()

val PandaWaveComponentTokens.nowPlayingQuickActionWidth: Dp
    @Composable get() = nowPlayingQuickActionWidthPx.toDp()

val PandaWaveComponentTokens.nowPlayingQuickActionHeight: Dp
    @Composable get() = nowPlayingQuickActionHeightPx.toDp()

val PandaWaveLayoutTokens.appContentPadding: Dp
    @Composable get() = appContentPaddingPx.toDp()

val PandaWaveLayoutTokens.navigationRailWidth: Dp
    @Composable get() = navigationRailWidthPx.toDp()

val PandaWaveLayoutTokens.navigationSelectedIndicatorWidth: Dp
    @Composable get() = navigationSelectedIndicatorWidthPx.toDp()

val PandaWaveLayoutTokens.navigationSelectedIndicatorHeight: Dp
    @Composable get() = navigationSelectedIndicatorHeightPx.toDp()

val PandaWaveLayoutTokens.nowPlayingArtworkCompact: Dp
    @Composable get() = nowPlayingArtworkCompactPx.toDp()

val PandaWaveLayoutTokens.nowPlayingArtworkStandard: Dp
    @Composable get() = nowPlayingArtworkStandardPx.toDp()

val PandaWaveLayoutTokens.nowPlayingPrimaryButton: Dp
    @Composable get() = nowPlayingPrimaryButtonPx.toDp()

val PandaWaveLayoutTokens.nowPlayingCompactHeightThreshold: Dp
    @Composable get() = nowPlayingCompactHeightThresholdPx.toDp()

val PandaWaveLayoutTokens.nowPlayingScrollHeightThreshold: Dp
    @Composable get() = nowPlayingScrollHeightThresholdPx.toDp()

val PandaWaveElevationTokens.cardResting: Dp
    @Composable get() = cardRestingPx.toDp()

@Composable
private fun Int.toDp(): Dp = with(LocalDensity.current) {
    this@toDp.toDp()
}
