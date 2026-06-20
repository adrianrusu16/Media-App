package com.adrianrusu.mediaapp.appshell.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.appContentPadding
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconLarge
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationItemHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationLogoSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationRailWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorInset
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.mediaapp.core.ui.focus.bambooFocusIndicator
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons
import com.adrianrusu.mediaapp.core.ui.miniplayer.BambooMiniPlayer
import com.adrianrusu.mediaapp.feature.home.HomeRoute
import com.adrianrusu.mediaapp.feature.library.LibraryRoute
import com.adrianrusu.mediaapp.feature.nowplaying.NowPlayingRoute
import com.adrianrusu.mediaapp.feature.profile.ProfileRoute
import com.adrianrusu.mediaapp.feature.search.SearchRoute
import com.adrianrusu.mediaapp.feature.settings.SettingsRoute

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
            PandaWaveNavigationRail(
                destinations = state.destinations,
                selectedRailDestination = state.selectedRailDestination,
                onLogoClick = {
                    onIntent(AppShellIntent.OpenNowPlaying)
                },
                onDestinationSelected = { destination ->
                    onIntent(AppShellIntent.SelectDestination(destination))
                }
            )
            AppShellContent(
                state = state,
                onIntent = onIntent
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PandaWaveNavigationRail(
    destinations: List<AppDestination>,
    selectedRailDestination: AppDestination?,
    onLogoClick: () -> Unit,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    NavigationRail(
        modifier = Modifier
            .width(tokens.layout.navigationRailWidth)
            .focusRestorer()
            .focusGroup()
            .testTag("navigation-rail-zone"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            Surface(
                modifier = Modifier.padding(
                    top = tokens.spacing.xs,
                    bottom = tokens.spacing.xs
                ).testTag("navigation-logo"),
                onClick = onLogoClick,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.size(tokens.components.navigationLogoSize),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pandawave_logo),
                        contentDescription = "Open Now Playing",
                        modifier = Modifier.size(tokens.components.iconLarge)
                    )
                }
            }
        }
    ) {
        destinations.filterNot { destination -> destination == AppDestination.Profile }.forEach { destination ->
            PandaWaveNavigationRailItem(
                destination = destination,
                selectedDestination = selectedRailDestination,
                onDestinationSelected = onDestinationSelected
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        PandaWaveNavigationRailItem(
            destination = AppDestination.Profile,
            selectedDestination = selectedRailDestination,
            onDestinationSelected = onDestinationSelected
        )
    }
}

@Composable
private fun PandaWaveNavigationRailItem(
    destination: AppDestination,
    selectedDestination: AppDestination?,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val isSelected = destination == selectedDestination
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.components.navigationItemHeight),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = tokens.components.navigationSelectedIndicatorInset)
                    .width(tokens.layout.navigationSelectedIndicatorWidth)
                    .height(tokens.layout.navigationSelectedIndicatorHeight),
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .bambooFocusIndicator()
                .bambooBringIntoViewOnFocus()
                .selectable(
                    selected = isSelected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onDestinationSelected(destination) }
                )
                .testTag("navigation-${destination.name}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(tokens.spacing.xs))
            Text(
                text = destination.label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
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
