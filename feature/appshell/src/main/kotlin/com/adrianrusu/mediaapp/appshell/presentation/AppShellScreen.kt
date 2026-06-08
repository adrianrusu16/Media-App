package com.adrianrusu.mediaapp.appshell.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
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
            Column {
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
                NavigationBar {
                    state.destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == state.selectedDestination,
                            onClick = {
                                onIntent(AppShellIntent.SelectDestination(destination))
                            },
                            icon = {},
                            label = {
                                Text(text = destination.label)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppShellContent(
            state = state,
            onIntent = onIntent,
            contentPadding = innerPadding
        )
    }
}

@Composable
private fun AppShellContent(state: AppShellState, onIntent: (AppShellIntent) -> Unit, contentPadding: PaddingValues) {
    val tokens = LocalPandaWaveDesignTokens.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(tokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        item {
            Header(
                selectedDestination = state.selectedDestination,
                restrictionLabel = state.restriction.label,
                isRestricted = state.restriction.isRestricted,
                engineConnection = state.engineConnection
            )
        }

        item {
            DestinationContent(
                destination = state.selectedDestination
            )
        }

        item {
            QuickActions(onIntent = onIntent)
        }
    }
}

@Composable
private fun Header(
    selectedDestination: AppDestination,
    restrictionLabel: String,
    isRestricted: Boolean,
    engineConnection: BambooEngineConnectionUiState
) {
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
            StatusChip(
                label = engineConnection.label,
                isHighlighted = engineConnection.status != BambooEngineConnectionStatus.Ready
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
private fun QuickActions(onIntent: (AppShellIntent) -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        Button(
            onClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Library))
            }
        ) {
            Text(text = "Library")
        }
        Button(
            onClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Settings))
            }
        ) {
            Text(text = "Settings")
        }
    }
}

@Composable
private fun DestinationContent(destination: AppDestination) {
    when (destination) {
        AppDestination.Home -> HomeRoute()
        AppDestination.Library -> LibraryRoute()
        AppDestination.NowPlaying -> NowPlayingRoute()
        AppDestination.Search -> SearchRoute()
        AppDestination.Settings -> SettingsRoute()
        AppDestination.Profile -> ProfileRoute()
    }
}
