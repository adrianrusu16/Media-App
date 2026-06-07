package com.adrianrusu.mediaapp.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    FeatureOverviewScreen(
        modifier = modifier,
        items = listOf(
            FeatureOverviewItem(
                title = "Resume",
                body = "Pick up where the last drive left off."
            ),
            FeatureOverviewItem(
                title = "Recently played",
                body = "Your latest albums, stations, and playlists will appear here."
            )
        )
    )
}
