package com.adrianrusu.mediaapp.appshell.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.core.ui.miniplayer.MediaAppMiniPlayer

@Composable
fun AppShellScreen(
    state: AppShellState,
    onIntent: (AppShellIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MediaAppMiniPlayer(
                    state = state.miniPlayer,
                    onPlayPauseClick = {
                        onIntent(AppShellIntent.TogglePlayback)
                    },
                )
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
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AppShellContent(
            state = state,
            onIntent = onIntent,
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun AppShellContent(
    state: AppShellState,
    onIntent: (AppShellIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Header(
                selectedDestination = state.selectedDestination,
                restrictionLabel = state.restriction.label,
                isRestricted = state.restriction.isRestricted,
            )
        }

        items(items = contentRows(state.selectedDestination)) { row ->
            InfoRow(
                title = row.title,
                body = row.body,
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Media App",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = selectedDestination.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Surface(
            color = if (isRestricted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = restrictionLabel,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun InfoRow(
    title: String,
    body: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun QuickActions(
    onIntent: (AppShellIntent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Library))
            },
        ) {
            Text(text = "Library")
        }
        Button(
            onClick = {
                onIntent(AppShellIntent.SelectDestination(AppDestination.Settings))
            },
        ) {
            Text(text = "Settings")
        }
    }
}

private data class ContentRow(
    val title: String,
    val body: String,
)

private fun contentRows(destination: AppDestination): List<ContentRow> =
    when (destination) {
        AppDestination.Home -> listOf(
            ContentRow(
                title = "Resume",
                body = "Pick up where the last drive left off.",
            ),
            ContentRow(
                title = "Recently played",
                body = "Your latest albums, stations, and playlists will appear here.",
            ),
        )

        AppDestination.Library -> listOf(
            ContentRow(
                title = "Saved music",
                body = "Albums, artists, and playlists stay organized for quick browsing.",
            ),
            ContentRow(
                title = "Downloaded content",
                body = "Offline listening will be available for supported content.",
            ),
        )

        AppDestination.Search -> listOf(
            ContentRow(
                title = "Safe search",
                body = "Search adapts to the current driving safety state.",
            ),
            ContentRow(
                title = "Providers",
                body = "More music sources can be enabled as the catalog grows.",
            ),
        )

        AppDestination.Settings -> listOf(
            ContentRow(
                title = "Privacy",
                body = "Control diagnostics, personalization, and data choices.",
            ),
            ContentRow(
                title = "Vehicle mode",
                body = "Review display, safety, and vehicle-specific behavior.",
            ),
        )

        AppDestination.Profile -> listOf(
            ContentRow(
                title = "Account",
                body = "Sign-in and account details will live here.",
            ),
            ContentRow(
                title = "Session",
                body = "Manage active sessions and trusted devices.",
            ),
        )
    }
