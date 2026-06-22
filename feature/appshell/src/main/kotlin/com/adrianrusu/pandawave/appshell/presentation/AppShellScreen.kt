package com.adrianrusu.pandawave.appshell.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.adrianrusu.pandawave.appshell.domain.AppDestination
import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.core.designsystem.R as DesignSystemR
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.appContentPadding
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons
import com.adrianrusu.pandawave.core.ui.miniplayer.BambooMiniPlayer
import com.adrianrusu.pandawave.core.ui.navigation.BambooNavigationItemModel
import com.adrianrusu.pandawave.core.ui.navigation.BambooNavigationRail
import com.adrianrusu.pandawave.feature.appshell.R
import com.adrianrusu.pandawave.feature.home.HomeRoute
import com.adrianrusu.pandawave.feature.library.LibraryRoute
import com.adrianrusu.pandawave.feature.nowplaying.NowPlayingRoute
import com.adrianrusu.pandawave.feature.profile.ProfileRoute
import com.adrianrusu.pandawave.feature.search.SearchRoute
import com.adrianrusu.pandawave.feature.settings.SettingsRoute

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppShellScreen(state: AppShellState, onIntent: (AppShellIntent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(enabled = !state.selectedDestination.isPrimary) {
        onIntent(AppShellIntent.NavigateBack)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (state.shouldShowMiniPlayer) {
                BambooMiniPlayer(
                    modifier = Modifier
                        .focusRestorer()
                        .focusGroup()
                        .testTag("mini-player-zone"),
                    state = state.miniPlayer,
                    controlsEnabled = state.canDispatchEngineCommands,
                    onClick = {
                        onIntent(AppShellIntent.OpenNowPlaying)
                    },
                    onSkipPreviousClick = {
                        onIntent(AppShellIntent.SkipPrevious)
                    },
                    onPlayPauseClick = {
                        onIntent(AppShellIntent.TogglePlayback)
                    },
                    onSkipNextClick = {
                        onIntent(AppShellIntent.SkipNext)
                    }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val navigationItems = state.destinations.map { destination ->
                BambooNavigationItemModel(
                    id = destination.name,
                    label = destination.localizedLabel(),
                    icon = destination.icon,
                    selected = destination == state.selectedRailDestination,
                    showLabel = true
                )
            }
            BambooNavigationRail(
                items = navigationItems,
                logo = painterResource(DesignSystemR.drawable.pandawave_ic_logo),
                logoContentDescription = stringResource(R.string.pandawave_navigation_open_now_playing),
                onLogoClick = { onIntent(AppShellIntent.OpenNowPlaying) },
                onItemClick = { destinationId ->
                    AppDestination.entries
                        .firstOrNull { it.name == destinationId }
                        ?.let { onIntent(AppShellIntent.SelectDestination(it)) }
                },
                bottomItemId = AppDestination.Profile.name
            )
            AppShellContent(
                state = state,
                onIntent = onIntent
            )
        }
    }
}

@Composable
private fun AppDestination.localizedLabel(): String = when (this) {
    AppDestination.Home -> stringResource(R.string.pandawave_navigation_home)
    AppDestination.Library -> stringResource(R.string.pandawave_navigation_library)
    AppDestination.Search -> stringResource(R.string.pandawave_navigation_search)
    AppDestination.Profile -> stringResource(R.string.pandawave_navigation_profile)
    AppDestination.NowPlaying -> stringResource(R.string.pandawave_navigation_now_playing)
    AppDestination.ProfileSettings -> stringResource(R.string.pandawave_navigation_settings)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppShellContent(state: AppShellState, onIntent: (AppShellIntent) -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
            .focusGroup()
            .testTag("destination-content-zone")
            .padding(tokens.layout.appContentPadding)
    ) {
        DestinationContent(
            destination = state.selectedDestination,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Home -> PandaWaveIcons.Home
        AppDestination.Library -> PandaWaveIcons.Library
        AppDestination.NowPlaying -> PandaWaveIcons.NowPlaying
        AppDestination.Search -> PandaWaveIcons.Search
        AppDestination.ProfileSettings -> PandaWaveIcons.Settings
        AppDestination.Profile -> PandaWaveIcons.Profile
    }

@Composable
private fun DestinationContent(
    destination: AppDestination,
    onIntent: (AppShellIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    when (destination) {
        AppDestination.Home -> HomeRoute(modifier = modifier)

        AppDestination.Library -> LibraryRoute(modifier = modifier)

        AppDestination.NowPlaying -> NowPlayingRoute(
            modifier = modifier,
            onLibraryClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Library))
            }
        )

        AppDestination.Search -> SearchRoute(modifier = modifier)

        AppDestination.ProfileSettings -> SettingsRoute(modifier = modifier)

        AppDestination.Profile -> ProfileRoute(
            modifier = modifier,
            onSettingsClick = {
                onIntent(AppShellIntent.OpenProfileSettings)
            }
        )
    }
}
