package com.adrianrusu.pandawave.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkFallback
import com.adrianrusu.pandawave.core.ui.artwork.toBambooArtworkModel
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaAction
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaHeroCard
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaTile
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.feature.home.domain.HomeState
import com.adrianrusu.pandawave.feature.home.domain.HomeTrack
import com.adrianrusu.pandawave.feature.home.presentation.HomeViewModel

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeRoute(modifier, state, viewModel::play, onOpenNowPlaying)
}

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    state: HomeState,
    onPlay: (mediaId: String, section: String, title: String) -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val tokens = LocalPandaWaveDesignTokens.current
    BambooRotaryColumn(
        modifier = modifier.fillMaxWidth().testTag("home-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.components.mediaSectionSpacing)
    ) {
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_for_you),
            tracks = state.forYou,
            hero = true,
            testTag = "home-for-you",
            section = HOME_SECTION_FOR_YOU,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying
        )
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_recommendations),
            tracks = state.recommendations,
            testTag = "home-recommendations",
            section = HOME_SECTION_RECOMMENDATIONS,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying
        )
        HomeFeedSection(
            title = stringResource(R.string.pandawave_home_discover),
            tracks = state.discovery,
            testTag = "home-discover",
            section = HOME_SECTION_DISCOVERY,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying
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
    hero: Boolean = false
) {
    val tokens = LocalPandaWaveDesignTokens.current
    Column(
        modifier = Modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)
    ) {
        BambooSectionHeader(title = title)
        if (tracks.isEmpty()) {
            Text(
                text = stringResource(R.string.pandawave_home_empty_feed),
                modifier = Modifier.testTag("$testTag-empty"),
                color = Color(tokens.colors.onSurfaceVariant)
            )
            return@Column
        }
        BambooFocusableLazyRow(
            horizontalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing),
            contentPadding = PaddingValues(horizontal = tokens.components.mediaCarouselSpacing)
        ) {
            items(tracks, key = HomeTrack::id) { track ->
                val item = BambooMediaItem(
                    id = track.id,
                    title = track.title,
                    subtitle = track.artist,
                    description = track.album ?: track.artist,
                    action = BambooMediaAction.Play,
                    artwork = toBambooArtworkModel(
                        id = track.artworkId,
                        version = track.artworkVersion,
                        uri = track.artworkUri
                    ),
                    artworkFallback = BambooArtworkFallback.Track
                )
                val onClick = {
                    onPlay(track.id, section, track.title)
                    onOpenNowPlaying()
                }
                if (hero) {
                    BambooMediaHeroCard(
                        modifier = Modifier.testTag("$testTag-${track.id}"),
                        item = item,
                        accentColor = Color(tokens.colors.primary),
                        onClick = onClick
                    )
                } else {
                    BambooMediaTile(
                        modifier = Modifier.testTag("$testTag-${track.id}"),
                        item = item,
                        accentColor = Color(tokens.colors.secondary),
                        onClick = onClick
                    )
                }
            }
        }
    }
}

private const val HOME_SECTION_FOR_YOU = "for_you"
private const val HOME_SECTION_RECOMMENDATIONS = "recommendations"
private const val HOME_SECTION_DISCOVERY = "discovery"
