package com.adrianrusu.pandawave.feature.nowplaying

import androidx.compose.ui.unit.Dp

internal enum class NowPlayingLayoutMode {
    Standard,
    Compact,
    ScrollableCompact
}

internal fun resolveNowPlayingLayout(
    availableHeight: Dp,
    compactHeightThreshold: Dp,
    scrollHeightThreshold: Dp
): NowPlayingLayoutMode = when {
    availableHeight < scrollHeightThreshold -> NowPlayingLayoutMode.ScrollableCompact
    availableHeight < compactHeightThreshold -> NowPlayingLayoutMode.Compact
    else -> NowPlayingLayoutMode.Standard
}

internal val NowPlayingLayoutMode.fillsRemainingArtworkSpace: Boolean
    get() = true

