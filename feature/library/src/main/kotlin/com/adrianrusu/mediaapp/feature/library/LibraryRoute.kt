package com.adrianrusu.mediaapp.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.discovery.BambooFilterChipRow
import com.adrianrusu.mediaapp.core.ui.discovery.BambooFilterOption
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaAction
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaItem
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaListRow
import com.adrianrusu.mediaapp.core.ui.discovery.BambooSectionHeader

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val featured = libraryFeaturedItems()
    val rows = libraryRows()
    var selectedFilter by remember { mutableStateOf("playlists") }
    val filters = BambooFilterOption.items(
        selectedId = selectedFilter,
        labels = listOf(
            "playlists" to "Playlists",
            "albums" to "Albums",
            "stations" to "Stations",
            "downloads" to "Downloads"
        )
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        BambooSectionHeader(
            title = "Library",
            subtitle = "Saved sounds, forest picks, and road-ready collections."
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "Panda Picks")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(featured, key = { it.id }) { item ->
                    BambooMediaHeroCard(
                        item = item,
                        icon = if (item.id == "bamboo-forest") Icons.Filled.Eco else Icons.Filled.Spa,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = {}
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooFilterChipRow(
                options = filters,
                onFilterSelected = { selectedFilter = it }
            )
            rows.forEach { item ->
                BambooMediaListRow(
                    item = item,
                    icon = when (selectedFilter) {
                        "albums" -> Icons.Filled.Album
                        "stations" -> Icons.Filled.Favorite
                        else -> Icons.Filled.LibraryMusic
                    },
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = {}
                )
            }
        }
    }
}

private fun libraryFeaturedItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "bamboo-forest",
        title = "Bamboo Forest Ambience",
        subtitle = "Editorial pick",
        description = "Layered field recordings and glassy pads.",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "lofi-green-tea",
        title = "Lo-Fi Green Tea",
        subtitle = "Slow rhythm",
        description = "A mellow catalog lane for work or late drives.",
        action = BambooMediaAction.Unavailable
    )
)

private fun libraryRows(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "saved-road-mix",
        title = "Saved Road Mix",
        subtitle = "42 tracks",
        description = "Your current long-drive queue.",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-focus",
        title = "Forest Focus",
        subtitle = "Playlist",
        description = "Soft instrumental focus with natural textures.",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "downloaded-calm",
        title = "Downloaded Calm",
        subtitle = "Offline",
        description = "Available without a connection.",
        action = BambooMediaAction.Unavailable
    )
)
