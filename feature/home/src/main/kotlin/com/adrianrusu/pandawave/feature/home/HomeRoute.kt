package com.adrianrusu.pandawave.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaCarouselSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaSectionSpacing
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaAction
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaTile
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons
import com.adrianrusu.pandawave.feature.home.domain.HomeState
import com.adrianrusu.pandawave.feature.home.domain.HomeTrack
import com.adrianrusu.pandawave.feature.home.presentation.HomeViewModel

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeRoute(state, viewModel::play, onOpenNowPlaying, modifier)
}

@Composable
fun HomeRoute(
    state: HomeState,
    onPlay: (mediaId: String, section: String, title: String) -> Unit,
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    BambooRotaryColumn(
        modifier = modifier.fillMaxWidth().testTag("home-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.components.mediaSectionSpacing),
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_home_greeting),
            subtitle = stringResource(R.string.pandawave_home_greeting_body),
        )
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_for_you),
            tracks = state.forYou,
            hero = true,
            testTag = "home-for-you",
            section = HOME_SECTION_FOR_YOU,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_recommendations),
            tracks = state.recommendations,
            testTag = "home-recommendations",
            section = HOME_SECTION_RECOMMENDATIONS,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_discover),
            tracks = state.discovery,
            testTag = "home-discover",
            section = HOME_SECTION_DISCOVERY,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying,
        )
    }
}

@Composable
private fun HomeFeedSection(
    title: String,
    tracks: List<HomeTrack>,
    testTag: String,
    section: String,
    onPlay: (mediaId: String, section: String, title: String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    hero: Boolean = false,
) {
    if (tracks.isEmpty()) return
    val tokens = LocalPandaWaveDesignTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
        BambooSectionHeader(title = title)
        BambooFocusableLazyRow(
            horizontalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing),
            contentPadding = PaddingValues(horizontal = tokens.components.mediaCarouselSpacing),
        ) {
            items(tracks, key = HomeTrack::id) { track ->
                val item = BambooMediaItem(
                    id = track.id,
                    title = track.title,
                    subtitle = track.artist,
                    description = track.album ?: track.artist,
                    action = BambooMediaAction.Play,
                )
                val onClick = {
                    onPlay(track.id, section, track.title)
                    onOpenNowPlaying()
                }
                if (hero) {
                    BambooMediaHeroCard(
                        modifier = Modifier.testTag("$testTag-${track.id}"),
                        item = item,
                        icon = PandaWaveIcons.Equalizer,
                        accentColor = Color(tokens.colors.primary),
                        onClick = onClick,
                    )
                } else {
                    BambooMediaTile(
                        modifier = Modifier.testTag("$testTag-${track.id}"),
                        item = item,
                        icon = PandaWaveIcons.MusicLibrary,
                        accentColor = Color(tokens.colors.secondary),
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

private const val HOME_SECTION_FOR_YOU = "for_you"
private const val HOME_SECTION_RECOMMENDATIONS = "recommendations"
private const val HOME_SECTION_DISCOVERY = "discovery"
