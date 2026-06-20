package com.adrianrusu.mediaapp.feature.nowplaying

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingLayoutTest {
    @Test
    fun `uses standard layout on tall surfaces`() {
        assertEquals(
            NowPlayingLayoutMode.Standard,
            resolveNowPlayingLayout(
                availableHeight = 720.dp,
                compactHeightThreshold = 640.dp,
                scrollHeightThreshold = 480.dp
            )
        )
    }

    @Test
    fun `uses compact layout on PandaEmulator content surface`() {
        assertEquals(
            NowPlayingLayoutMode.Compact,
            resolveNowPlayingLayout(
                availableHeight = 572.dp,
                compactHeightThreshold = 640.dp,
                scrollHeightThreshold = 480.dp
            )
        )
    }

    @Test
    fun `uses scroll fallback only below compact safety threshold`() {
        assertEquals(
            NowPlayingLayoutMode.ScrollableCompact,
            resolveNowPlayingLayout(
                availableHeight = 420.dp,
                compactHeightThreshold = 640.dp,
                scrollHeightThreshold = 480.dp
            )
        )
    }
}
