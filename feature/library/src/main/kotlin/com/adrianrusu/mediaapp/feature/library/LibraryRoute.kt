package com.adrianrusu.mediaapp.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun LibraryRoute(
    modifier: Modifier = Modifier,
) {
    FeatureOverviewScreen(
        modifier = modifier,
        items = listOf(
            FeatureOverviewItem(
                title = "Saved music",
                body = "Albums, artists, and playlists stay organized for quick browsing.",
            ),
            FeatureOverviewItem(
                title = "Downloaded content",
                body = "Offline listening will be available for supported content.",
            ),
        ),
    )
}
