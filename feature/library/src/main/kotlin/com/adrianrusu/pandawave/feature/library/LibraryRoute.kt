package com.adrianrusu.pandawave.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaCarouselSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaSectionSpacing
import com.adrianrusu.pandawave.core.ui.discovery.BambooFilterChipRow
import com.adrianrusu.pandawave.core.ui.discovery.BambooFilterOption
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaAction
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaListRow
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val featured = libraryFeaturedItems()
    val rows = libraryRows()
    var selectedFilter by remember { mutableStateOf("playlists") }
    val filters = BambooFilterOption.items(
        selectedId = selectedFilter,
        labels = listOf(
            "playlists" to stringResource(R.string.pandawave_library_filter_playlists),
            "albums" to stringResource(R.string.pandawave_library_filter_albums),
            "stations" to stringResource(R.string.pandawave_library_filter_stations),
            "downloads" to stringResource(R.string.pandawave_library_filter_downloads)
        )
    )

    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.components.mediaSectionSpacing)
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_library_title),
            subtitle = stringResource(R.string.pandawave_library_subtitle)
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_library_panda_picks))
            BambooFocusableLazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing),
                contentPadding = PaddingValues(horizontal = tokens.components.mediaCarouselSpacing)
            ) {
                items(featured, key = { it.id }) { item ->
                    BambooMediaHeroCard(
                        modifier = Modifier.testTag("library-featured-${item.id}"),
                        item = item,
                        icon = if (item.id == "bamboo-forest") {
                            PandaWaveIcons.Nature
                        } else {
                            PandaWaveIcons.Relax
                        },
                        accentColor = Color(tokens.colors.primary),
                        onClick = {}
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
            BambooFilterChipRow(
                modifier = Modifier.testTag("library-filters"),
                options = filters,
                onFilterSelected = { selectedFilter = it }
            )
            rows.forEach { item ->
                BambooMediaListRow(
                    modifier = Modifier.testTag("library-row-${item.id}"),
                    item = item,
                    icon = when (selectedFilter) {
                        "albums" -> PandaWaveIcons.Album
                        "stations" -> PandaWaveIcons.Favorite
                        else -> PandaWaveIcons.MusicLibrary
                    },
                    accentColor = Color(tokens.colors.secondary),
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun libraryFeaturedItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "bamboo-forest",
        title = stringResource(R.string.pandawave_library_bamboo_forest_title),
        subtitle = stringResource(R.string.pandawave_library_bamboo_forest_subtitle),
        description = stringResource(R.string.pandawave_library_bamboo_forest_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "lofi-green-tea",
        title = stringResource(R.string.pandawave_library_lofi_title),
        subtitle = stringResource(R.string.pandawave_library_lofi_subtitle),
        description = stringResource(R.string.pandawave_library_lofi_description),
        action = BambooMediaAction.Unavailable
    )
)

@Composable
private fun libraryRows(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "saved-road-mix",
        title = stringResource(R.string.pandawave_library_saved_road_mix_title),
        subtitle = stringResource(R.string.pandawave_library_track_count),
        description = stringResource(R.string.pandawave_library_saved_road_mix_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-focus",
        title = stringResource(R.string.pandawave_library_forest_focus_title),
        subtitle = stringResource(R.string.pandawave_library_playlist),
        description = stringResource(R.string.pandawave_library_forest_focus_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "downloaded-calm",
        title = stringResource(R.string.pandawave_library_downloaded_calm_title),
        subtitle = stringResource(R.string.pandawave_library_offline),
        description = stringResource(R.string.pandawave_library_downloaded_calm_description),
        action = BambooMediaAction.Unavailable
    )
)
