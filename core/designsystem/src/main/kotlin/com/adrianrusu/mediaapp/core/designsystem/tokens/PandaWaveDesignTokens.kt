package com.adrianrusu.mediaapp.core.designsystem.tokens

import androidx.annotation.ColorInt

data class PandaWaveDesignTokens(
    val colors: PandaWaveColorTokens,
    val spacing: PandaWaveSpacingTokens,
    val shape: PandaWaveShapeTokens,
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

data class PandaWaveRestrictionTokens(
    val maxBrowseColumnsUnrestricted: Int,
    val maxBrowseColumnsRestricted: Int,
    val maxVisibleActionsRestricted: Int
)
