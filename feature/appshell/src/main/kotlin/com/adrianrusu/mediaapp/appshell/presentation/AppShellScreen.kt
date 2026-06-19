package com.adrianrusu.mediaapp.appshell.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (state.shouldShowMiniPlayer) {
                BambooMiniPlayer(
                    state = state.miniPlayer,
                    controlsEnabled = state.canDispatchEngineCommands,
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
                selectedDestination = state.selectedDestination,
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
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            Box(
                modifier = Modifier.padding(
                    top = tokens.spacing.md,
                    bottom = tokens.spacing.lg
                ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pandawave_logo),
                    contentDescription = "PandaWave",
                    modifier = Modifier.size(tokens.sizing.touchTargetLg)
                )
            }
        }
    ) {
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = destination == selectedDestination,
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
                }
            )
        }
    }
}

@Composable
private fun AppShellContent(state: AppShellState, onIntent: (AppShellIntent) -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(tokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        item {
            Header(
                selectedDestination = state.selectedDestination,
                restrictionLabel = state.restriction.label,
                isRestricted = state.restriction.isRestricted
            )
        }

        item {
            DestinationContent(
                destination = state.selectedDestination,
                onIntent = onIntent
            )
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Home -> Icons.Filled.Home
        AppDestination.Library -> Icons.Filled.LibraryMusic
        AppDestination.NowPlaying -> Icons.Filled.PlayCircle
        AppDestination.Search -> Icons.Filled.Search
        AppDestination.Settings -> Icons.Filled.Settings
        AppDestination.Profile -> Icons.Filled.AccountCircle
    }

@Composable
private fun Header(selectedDestination: AppDestination, restrictionLabel: String, isRestricted: Boolean) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
        ) {
            Text(
                text = "PandaWave",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = selectedDestination.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            StatusChip(
                label = restrictionLabel,
                isHighlighted = isRestricted
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, isHighlighted: Boolean) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.md,
                vertical = tokens.spacing.sm
            ),
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun DestinationContent(destination: AppDestination, onIntent: (AppShellIntent) -> Unit) {
    when (destination) {
        AppDestination.Home -> HomeRoute()

        AppDestination.Library -> LibraryRoute()

        AppDestination.NowPlaying -> NowPlayingRoute(
            onLibraryClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Library))
            }
        )

        AppDestination.Search -> SearchRoute()

        AppDestination.Settings -> SettingsRoute()

        AppDestination.Profile -> ProfileRoute()
    }
}
