package com.adrianrusu.mediaapp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaAction
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaItem
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaTile
import com.adrianrusu.mediaapp.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.mediaapp.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.mediaapp.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val forYou = homeForYouItems()
    val recent = homeRecentItems()

    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_home_greeting),
            subtitle = stringResource(R.string.pandawave_home_greeting_body)
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_home_for_you))
            BambooFocusableLazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(forYou, key = { it.id }) { item ->
                    BambooMediaHeroCard(
                        modifier = Modifier.testTag("home-for-you-${item.id}"),
                        item = item,
                        icon = when (item.id) {
                            "bamboo-beats" -> PandaWaveIcons.Equalizer
                            "quiet-canopy" -> PandaWaveIcons.Relax
                            else -> PandaWaveIcons.Nature
                        },
                        accentColor = when (item.id) {
                            "bamboo-beats" -> Color(tokens.colors.primary)
                            "quiet-canopy" -> Color(tokens.colors.secondary)
                            else -> Color(tokens.colors.secondary)
                        },
                        onClick = {}
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_home_recent))
            BambooFocusableLazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(recent, key = { it.id }) { item ->
                    BambooMediaTile(
                        modifier = Modifier.testTag("home-recent-${item.id}"),
                        item = item,
                        icon = PandaWaveIcons.MusicLibrary,
                        accentColor = Color(tokens.colors.secondary),
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun homeForYouItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "bamboo-beats",
        title = stringResource(R.string.pandawave_home_bamboo_beats_title),
        subtitle = stringResource(R.string.pandawave_home_bamboo_beats_subtitle),
        description = stringResource(R.string.pandawave_home_bamboo_beats_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "quiet-canopy",
        title = stringResource(R.string.pandawave_home_quiet_canopy_title),
        subtitle = stringResource(R.string.pandawave_home_quiet_canopy_subtitle),
        description = stringResource(R.string.pandawave_home_quiet_canopy_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-radio",
        title = stringResource(R.string.pandawave_home_forest_radio_title),
        subtitle = stringResource(R.string.pandawave_home_forest_radio_subtitle),
        description = stringResource(R.string.pandawave_home_forest_radio_description),
        action = BambooMediaAction.Unavailable
    )
)

@Composable
private fun homeRecentItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "eucalyptus-dreams",
        title = stringResource(R.string.pandawave_home_eucalyptus_dreams_title),
        subtitle = stringResource(R.string.pandawave_home_album),
        description = stringResource(R.string.pandawave_home_lush_instrumentals),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "night-drive",
        title = stringResource(R.string.pandawave_home_night_drive_title),
        subtitle = stringResource(R.string.pandawave_home_playlist),
        description = stringResource(R.string.pandawave_home_low_light_momentum),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "rainforest-echo",
        title = stringResource(R.string.pandawave_home_rainforest_echo_title),
        subtitle = stringResource(R.string.pandawave_home_station),
        description = stringResource(R.string.pandawave_home_nature_textures),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "highland-mist",
        title = stringResource(R.string.pandawave_home_highland_mist_title),
        subtitle = stringResource(R.string.pandawave_home_mix),
        description = stringResource(R.string.pandawave_home_calm_acoustic_air),
        action = BambooMediaAction.Unavailable
    )
)
