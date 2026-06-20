package com.adrianrusu.mediaapp.appshell.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.appContentPadding
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationRailWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.miniplayer.BambooMiniPlayer
import com.adrianrusu.mediaapp.feature.home.HomeRoute
import com.adrianrusu.mediaapp.feature.library.LibraryRoute
import com.adrianrusu.mediaapp.feature.nowplaying.NowPlayingRoute
import com.adrianrusu.mediaapp.feature.profile.ProfileRoute
import com.adrianrusu.mediaapp.feature.search.SearchRoute
import com.adrianrusu.mediaapp.feature.settings.SettingsRoute

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

@Composable
private fun PandaWaveNavigationRail(
    destinations: List<AppDestination>,
    selectedRailDestination: AppDestination?,
    onLogoClick: () -> Unit,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    NavigationRail(
        modifier = Modifier.width(tokens.layout.navigationRailWidth),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            Surface(
                modifier = Modifier.padding(
                    top = tokens.spacing.xs,
                    bottom = tokens.spacing.xs
                ),
                onClick = onLogoClick,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.size(tokens.sizing.touchTargetLg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pandawave_logo),
                        contentDescription = "Open Now Playing",
                        modifier = Modifier.size(tokens.sizing.touchTargetLg)
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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = tokens.spacing.sm)
                    .width(tokens.layout.navigationSelectedIndicatorWidth)
                    .height(tokens.layout.navigationSelectedIndicatorHeight),
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { }
        }
        NavigationRailItem(
            selected = isSelected,
            onClick = {
                onDestinationSelected(destination)
            },
            icon = {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null
                )
            },
            label = {
                Text(text = destination.label)
            },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun AppShellContent(state: AppShellState, onIntent: (AppShellIntent) -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
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
        AppDestination.Home -> Icons.Filled.Home
        AppDestination.Library -> Icons.Filled.LibraryMusic
        AppDestination.NowPlaying -> Icons.Filled.PlayCircle
        AppDestination.Search -> Icons.Filled.Search
        AppDestination.ProfileSettings -> Icons.Filled.Settings
        AppDestination.Profile -> Icons.Filled.AccountCircle
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
