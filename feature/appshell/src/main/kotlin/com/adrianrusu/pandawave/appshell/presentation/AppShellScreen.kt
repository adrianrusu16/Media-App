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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.appshell.navigation.HomeDestination
import com.adrianrusu.pandawave.appshell.navigation.LibraryDestination
import com.adrianrusu.pandawave.appshell.navigation.NowPlayingDestination
import com.adrianrusu.pandawave.appshell.navigation.PandaWaveDestination
import com.adrianrusu.pandawave.appshell.navigation.PandaWaveNavigator
import com.adrianrusu.pandawave.appshell.navigation.PreferencesDestination
import com.adrianrusu.pandawave.appshell.navigation.ProfileDestination
import com.adrianrusu.pandawave.appshell.navigation.SearchDestination
import com.adrianrusu.pandawave.appshell.navigation.navigationId
import com.adrianrusu.pandawave.appshell.navigation.primaryDestinations
import com.adrianrusu.pandawave.appshell.navigation.selectedRailDestination
import com.adrianrusu.pandawave.appshell.navigation.shouldShowMiniPlayer
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
fun AppShellScreen(
    state: AppShellState,
    onIntent: (AppShellIntent) -> Unit,
    onMoveTaskToBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(HomeDestination)
    val navigator = remember(backStack) { PandaWaveNavigator(backStack) }
    val currentDestination = navigator.currentDestination

    BackHandler(enabled = navigator.isAtRoot, onBack = onMoveTaskToBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentDestination.shouldShowMiniPlayer) {
                BambooMiniPlayer(
                    modifier = Modifier
                        .focusRestorer()
                        .focusGroup()
                        .testTag("mini-player-zone"),
                    state = state.miniPlayer,
                    controlsEnabled = state.canDispatchEngineCommands,
                    onClick = navigator::openNowPlaying,
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
            val navigationItems = primaryDestinations.map { destination ->
                BambooNavigationItemModel(
                    id = destination.navigationId,
                    label = destination.localizedLabel(),
                    icon = destination.icon,
                    selected = destination == currentDestination.selectedRailDestination,
                    showLabel = true
                )
            }
            BambooNavigationRail(
                items = navigationItems,
                logo = painterResource(DesignSystemR.drawable.pandawave_ic_logo),
                logoContentDescription = stringResource(
                    R.string.pandawave_navigation_open_now_playing
                ),
                onLogoClick = navigator::openNowPlaying,
                onItemClick = { destinationId ->
                    primaryDestinations
                        .firstOrNull { it.navigationId == destinationId }
                        ?.let(navigator::selectPrimary)
                },
                bottomItemId = ProfileDestination.navigationId
            )
            AppShellContent(
                backStack = backStack,
                navigator = navigator
            )
        }
    }
}

@Composable
private fun PandaWaveDestination.localizedLabel(): String = when (this) {
    HomeDestination -> stringResource(R.string.pandawave_navigation_home)
    LibraryDestination -> stringResource(R.string.pandawave_navigation_library)
    SearchDestination -> stringResource(R.string.pandawave_navigation_search)
    ProfileDestination -> stringResource(R.string.pandawave_navigation_profile)
    NowPlayingDestination -> stringResource(R.string.pandawave_navigation_now_playing)
    PreferencesDestination -> stringResource(R.string.pandawave_navigation_settings)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppShellContent(backStack: List<androidx.navigation3.runtime.NavKey>, navigator: PandaWaveNavigator) {
    val tokens = LocalPandaWaveDesignTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
            .focusGroup()
            .testTag("destination-content-zone")
            .padding(tokens.layout.appContentPadding)
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<HomeDestination> {
                    HomeRoute(modifier = Modifier.fillMaxSize())
                }
                entry<LibraryDestination> {
                    LibraryRoute(modifier = Modifier.fillMaxSize())
                }
                entry<SearchDestination> {
                    SearchRoute(modifier = Modifier.fillMaxSize())
                }
                entry<ProfileDestination> {
                    ProfileRoute(
                        modifier = Modifier.fillMaxSize(),
                        onSettingsClick = navigator::openPreferences
                    )
                }
                entry<PreferencesDestination> {
                    SettingsRoute(modifier = Modifier.fillMaxSize())
                }
                entry<NowPlayingDestination> {
                    NowPlayingRoute(
                        modifier = Modifier.fillMaxSize(),
                        onLibraryClick = {
                            navigator.selectPrimary(LibraryDestination)
                        }
                    )
                }
            }
        )
    }
}

private val PandaWaveDestination.icon: ImageVector
    get() = when (this) {
        HomeDestination -> PandaWaveIcons.Home
        LibraryDestination -> PandaWaveIcons.Library
        SearchDestination -> PandaWaveIcons.Search
        ProfileDestination -> PandaWaveIcons.Profile
        NowPlayingDestination -> PandaWaveIcons.NowPlaying
        PreferencesDestination -> PandaWaveIcons.Settings
    }
