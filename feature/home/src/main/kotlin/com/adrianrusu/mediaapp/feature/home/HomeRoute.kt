package com.adrianrusu.mediaapp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaAction
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaItem
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaTile
import com.adrianrusu.mediaapp.core.ui.discovery.BambooSectionHeader

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val forYou = homeForYouItems()
    val recent = homeRecentItems()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        BambooSectionHeader(
            title = "Good drive",
            subtitle = "Forest-tuned mixes, quiet focus, and recent sounds are ready."
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "For You")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(forYou, key = { it.id }) { item ->
                    BambooMediaHeroCard(
                        item = item,
                        icon = when (item.id) {
                            "bamboo-beats" -> Icons.Filled.GraphicEq
                            "quiet-canopy" -> Icons.Filled.Spa
                            else -> Icons.Filled.Eco
                        },
                        accentColor = when (item.id) {
                            "bamboo-beats" -> MaterialTheme.colorScheme.primary
                            "quiet-canopy" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                        onClick = {}
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "Recent")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(recent, key = { it.id }) { item ->
                    BambooMediaTile(
                        item = item,
                        icon = Icons.Filled.LibraryMusic,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = {}
                    )
                }
            }
        }
    }
}

private fun homeForYouItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "bamboo-beats",
        title = "Bamboo Beats",
        subtitle = "Fresh picks",
        description = "Soft percussion and green-room synths for the road.",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "quiet-canopy",
        title = "Quiet Canopy",
        subtitle = "Focus mode",
        description = "Warm ambient layers that stay out of your way.",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-radio",
        title = "Forest Radio",
        subtitle = "Live station",
        description = "A leafy stream of downtempo discoveries.",
        action = BambooMediaAction.Unavailable
    )
)

private fun homeRecentItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "eucalyptus-dreams",
        title = "Eucalyptus Dreams",
        subtitle = "Album",
        description = "Lush instrumentals",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "night-drive",
        title = "Night Drive",
        subtitle = "Playlist",
        description = "Low-light momentum",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "rainforest-echo",
        title = "Rainforest Echo",
        subtitle = "Station",
        description = "Nature textures",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "highland-mist",
        title = "Highland Mist",
        subtitle = "Mix",
        description = "Calm acoustic air",
        action = BambooMediaAction.Unavailable
    )
)
