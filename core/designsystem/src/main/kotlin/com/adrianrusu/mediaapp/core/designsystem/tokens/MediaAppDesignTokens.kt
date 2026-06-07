package com.adrianrusu.mediaapp.core.designsystem.tokens

import androidx.annotation.ColorInt

data class MediaAppDesignTokens(
    val colors: MediaAppColorTokens,
    val spacing: MediaAppSpacingTokens,
    val shape: MediaAppShapeTokens,
    val restrictions: MediaAppRestrictionTokens,
)

data class MediaAppColorTokens(
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val secondary: Int,
    @ColorInt val onSecondary: Int,
    @ColorInt val surface: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val surfaceVariant: Int,
    @ColorInt val onSurfaceVariant: Int,
    @ColorInt val error: Int,
    @ColorInt val onError: Int,
)

data class MediaAppSpacingTokens(
    val xsPx: Int,
    val smPx: Int,
    val mdPx: Int,
    val lgPx: Int,
    val xlPx: Int,
)

data class MediaAppShapeTokens(
    val smallCornerPx: Int,
    val mediumCornerPx: Int,
    val miniPlayerHeightPx: Int,
)

data class MediaAppRestrictionTokens(
    val maxBrowseColumnsUnrestricted: Int,
    val maxBrowseColumnsRestricted: Int,
    val maxVisibleActionsRestricted: Int,
)
