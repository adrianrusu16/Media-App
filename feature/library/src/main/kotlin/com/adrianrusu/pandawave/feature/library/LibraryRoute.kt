package com.adrianrusu.pandawave.feature.library

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.cardResting
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
import com.adrianrusu.pandawave.feature.library.domain.LibraryPlaylist
import com.adrianrusu.pandawave.feature.library.domain.PlaylistConflict
import com.adrianrusu.pandawave.feature.library.presentation.LibraryViewModel

@Composable
fun LibraryRoute(
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryRoute(
        state = state,
        modifier = modifier,
        onSelectTab = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onLoadNext = viewModel::loadNext,
        onPlay = viewModel::play,
        onOpenNowPlaying = onOpenNowPlaying,
        onSave = viewModel::save,
        onRemoveSaved = viewModel::removeSaved,
        onLike = viewModel::like,
        onUnlike = viewModel::unlike,
        onCreatePlaylist = viewModel::createPlaylist,
        onUpdatePlaylist = viewModel::updatePlaylist,
        onDeletePlaylist = viewModel::deletePlaylist,
        onSelectPlaylist = viewModel::selectPlaylist,
        onAddPlaylistTrack = viewModel::addPlaylistTrack,
        onRemovePlaylistTrack = viewModel::removePlaylistTrack,
        onReorderPlaylist = viewModel::reorderPlaylist,
    )
}

@Composable
fun LibraryRoute(
    state: LibraryState,
    onSelectTab: (LibraryTab) -> Unit,
    onRefresh: () -> Unit,
    onLoadNext: () -> Unit,
    onPlay: (String) -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
    onCreatePlaylist: (String, String?) -> Unit = { _, _ -> },
    onUpdatePlaylist: (String, String, String?, Long) -> Unit = { _, _, _, _ -> },
    onDeletePlaylist: (String) -> Unit = {},
    onSelectPlaylist: (String) -> Unit = {},
    onAddPlaylistTrack: (String, String) -> Unit = { _, _ -> },
    onRemovePlaylistTrack: (String, String) -> Unit = { _, _ -> },
    onReorderPlaylist: (String, List<String>, Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_library_title),
            subtitle = stringResource(R.string.pandawave_library_subtitle),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            LibraryTabButton(
                modifier = Modifier.weight(1f).testTag("library-tab-saved"),
                selected = state.selectedTab == LibraryTab.SAVED,
                text = stringResource(R.string.pandawave_library_saved),
                onClick = { onSelectTab(LibraryTab.SAVED) },
            )
            LibraryTabButton(
                modifier = Modifier.weight(1f).testTag("library-tab-liked"),
                selected = state.selectedTab == LibraryTab.LIKED,
                text = stringResource(R.string.pandawave_library_liked),
                onClick = { onSelectTab(LibraryTab.LIKED) },
            )
            LibraryTabButton(
                modifier = Modifier.weight(1f).testTag("library-tab-playlists"),
                selected = state.selectedTab == LibraryTab.PLAYLISTS,
                text = stringResource(R.string.pandawave_library_playlists),
                onClick = { onSelectTab(LibraryTab.PLAYLISTS) },
            )
        }

        if (state.isSignedOut) {
            Text(
                text = stringResource(R.string.pandawave_library_signed_out),
                modifier = Modifier.testTag("library-signed-out"),
            )
            return@BambooRotaryColumn
        }

        if (state.isLoading && state.selectedTracks.isEmpty() && state.playlists.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("library-loading"),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorType?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("library-error"),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(tokens.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (state.isRetryableError) R.string.pandawave_library_network_error
                            else R.string.pandawave_library_error
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isRetryableError) {
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.testTag("library-retry")) {
                            Text(stringResource(R.string.pandawave_library_retry))
                        }
                    }
                }
            }
        }

        if (!state.isLoading && state.selectedTracks.isEmpty() && state.selectedTab != LibraryTab.PLAYLISTS && state.errorType == null) {
            Text(
                text = stringResource(
                    if (state.selectedTab == LibraryTab.SAVED) R.string.pandawave_library_empty_saved
                    else R.string.pandawave_library_empty_liked
                ),
                modifier = Modifier.testTag("library-empty"),
            )
        }

        if (state.selectedTab == LibraryTab.PLAYLISTS) {
            PlaylistLibraryContent(
                state = state,
                onCreatePlaylist = onCreatePlaylist,
                onUpdatePlaylist = onUpdatePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                onSelectPlaylist = onSelectPlaylist,
                onAddPlaylistTrack = onAddPlaylistTrack,
                onRemovePlaylistTrack = onRemovePlaylistTrack,
                onReorderPlaylist = onReorderPlaylist,
                onPlay = onPlay,
                onOpenNowPlaying = onOpenNowPlaying,
            )
        } else {
            val savedIds = state.savedTracks.mapTo(mutableSetOf(), LibraryTrack::mediaId)
            val likedIds = state.likedTracks.mapTo(mutableSetOf(), LibraryTrack::mediaId)
            state.selectedTracks.forEach { track ->
                LibraryTrackRow(
                    track = track,
                    tab = state.selectedTab,
                    pending = track.mediaId in state.pendingMediaIds,
                    saved = track.mediaId in savedIds,
                    liked = track.mediaId in likedIds,
                    onSave = onSave,
                    onRemoveSaved = onRemoveSaved,
                    onLike = onLike,
                    onUnlike = onUnlike,
                    onPlay = onPlay,
                    onOpenNowPlaying = onOpenNowPlaying,
                )
            }
        }

        if (state.hasSelectedNextPage) {
            Button(
                onClick = onLoadNext,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("library-next-page"),
            ) {
                Text(stringResource(R.string.pandawave_library_load_more))
            }
        }
    }
}

@Composable
private fun PlaylistLibraryContent(
    state: LibraryState,
    onCreatePlaylist: (String, String?) -> Unit,
    onUpdatePlaylist: (String, String, String?, Long) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onAddPlaylistTrack: (String, String) -> Unit,
    onRemovePlaylistTrack: (String, String) -> Unit,
    onReorderPlaylist: (String, List<String>, Long) -> Unit,
    onPlay: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val selectedPlaylist = state.playlists.firstOrNull { it.id == state.selectedPlaylistId }
    var playlistName by remember { mutableStateOf("") }
    var playlistDescription by remember { mutableStateOf("") }
    var trackId by remember { mutableStateOf("") }

    OutlinedTextField(
        value = playlistName,
        onValueChange = { playlistName = it },
        modifier = Modifier.fillMaxWidth().testTag("library-playlist-name"),
        label = { Text(stringResource(R.string.pandawave_library_playlist_name)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = playlistDescription,
        onValueChange = { playlistDescription = it },
        modifier = Modifier.fillMaxWidth().testTag("library-playlist-description"),
        label = { Text(stringResource(R.string.pandawave_library_playlist_description)) },
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Button(
            onClick = {
                onCreatePlaylist(playlistName.trim(), playlistDescription.trim().ifBlank { null })
                playlistName = ""
                playlistDescription = ""
            },
            enabled = playlistName.isNotBlank(),
            modifier = Modifier.weight(1f).testTag("library-create-playlist"),
        ) { Text(stringResource(R.string.pandawave_library_create_playlist)) }
        selectedPlaylist?.let { playlist ->
            OutlinedButton(
                onClick = {
                    onUpdatePlaylist(playlist.id, playlistName.trim(), playlistDescription.trim().ifBlank { null }, playlist.revision)
                },
                enabled = playlistName.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("library-playlist-update"),
            ) { Text(stringResource(R.string.pandawave_library_update_playlist)) }
        }
    }

    if (state.playlists.isEmpty()) {
        Text(stringResource(R.string.pandawave_library_empty_playlists), modifier = Modifier.testTag("library-empty-playlists"))
    }
    state.playlists.forEach { playlist ->
        PlaylistRow(
            playlist = playlist,
            selected = playlist.id == state.selectedPlaylistId,
            onSelect = {
                playlistName = playlist.name
                playlistDescription = playlist.description.orEmpty()
                onSelectPlaylist(playlist.id)
            },
            onDelete = { onDeletePlaylist(playlist.id) },
        )
    }

    selectedPlaylist?.let { playlist ->
        BambooSectionHeader(
            title = playlist.name,
            subtitle = stringResource(R.string.pandawave_library_playlist_tracks),
        )
        OutlinedTextField(
            value = trackId,
            onValueChange = { trackId = it },
            modifier = Modifier.fillMaxWidth().testTag("library-playlist-track-id"),
            label = { Text(stringResource(R.string.pandawave_library_track_id)) },
            singleLine = true,
        )
        Button(
            onClick = {
                onAddPlaylistTrack(playlist.id, trackId.trim())
                trackId = ""
            },
            enabled = trackId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("library-playlist-add-track"),
        ) { Text(stringResource(R.string.pandawave_library_add_track)) }
        PlaylistTrackList(
            playlist = playlist,
            tracks = state.playlistTracks,
            reorderEnabled = !state.hasPlaylistTracksNextPage,
            onRemovePlaylistTrack = onRemovePlaylistTrack,
            onReorderPlaylist = onReorderPlaylist,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying,
        )
    }

    state.playlistConflict?.let { conflict ->
        PlaylistConflictCard(conflict = conflict, onReorderPlaylist = onReorderPlaylist)
    }
}

@Composable
private fun PlaylistRow(
    playlist: LibraryPlaylist,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("library-playlist-${playlist.id}"),
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
            playlist.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                if (selected) {
                    Button(onClick = onSelect, modifier = Modifier.weight(1f).testTag("library-playlist-select-${playlist.id}")) {
                        Text(stringResource(R.string.pandawave_library_selected_playlist))
                    }
                } else {
                    OutlinedButton(onClick = onSelect, modifier = Modifier.weight(1f).testTag("library-playlist-select-${playlist.id}")) {
                        Text(stringResource(R.string.pandawave_library_select_playlist))
                    }
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).testTag("library-playlist-delete-${playlist.id}")) {
                    Text(stringResource(R.string.pandawave_library_delete_playlist))
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackList(
    playlist: LibraryPlaylist,
    tracks: List<LibraryTrack>,
    reorderEnabled: Boolean,
    onRemovePlaylistTrack: (String, String) -> Unit,
    onReorderPlaylist: (String, List<String>, Long) -> Unit,
    onPlay: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    tracks.forEachIndexed { index, track ->
        PlaylistTrackRow(
            playlist = playlist,
            track = track,
            index = index,
            tracks = tracks,
            reorderEnabled = reorderEnabled,
            onRemovePlaylistTrack = onRemovePlaylistTrack,
            onReorderPlaylist = onReorderPlaylist,
            onPlay = onPlay,
            onOpenNowPlaying = onOpenNowPlaying,
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    playlist: LibraryPlaylist,
    track: LibraryTrack,
    index: Int,
    tracks: List<LibraryTrack>,
    reorderEnabled: Boolean,
    onRemovePlaylistTrack: (String, String) -> Unit,
    onReorderPlaylist: (String, List<String>, Long) -> Unit,
    onPlay: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    var draggedDistance by remember(track.relationshipId) { mutableFloatStateOf(0f) }
    val reorderModifier = if (reorderEnabled) {
        Modifier.pointerInput(track.relationshipId, tracks) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount -> draggedDistance += dragAmount },
                onDragEnd = {
                    val destination = when {
                        draggedDistance > DRAG_REORDER_THRESHOLD -> (index + 1).coerceAtMost(tracks.lastIndex)
                        draggedDistance < -DRAG_REORDER_THRESHOLD -> (index - 1).coerceAtLeast(0)
                        else -> index
                    }
                    if (destination != index) {
                        val proposed = tracks.toMutableList().also { members ->
                            members.add(destination, members.removeAt(index))
                        }
                        onReorderPlaylist(playlist.id, proposed.map(LibraryTrack::relationshipId), playlist.revision)
                    }
                    draggedDistance = 0f
                },
                onDragCancel = { draggedDistance = 0f },
            )
        }
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-playlist-track-${track.relationshipId}")
            .clickable {
                onPlay(track.mediaId)
                onOpenNowPlaying()
            }
            .then(reorderModifier),
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(track.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { onRemovePlaylistTrack(playlist.id, track.mediaId) },
                modifier = Modifier.testTag("library-playlist-remove-track-${track.relationshipId}"),
            ) { Text(stringResource(R.string.pandawave_library_remove_track)) }
        }
    }
}

@Composable
private fun PlaylistConflictCard(
    conflict: PlaylistConflict,
    onReorderPlaylist: (String, List<String>, Long) -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("library-playlist-conflict"),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(stringResource(R.string.pandawave_library_playlist_conflict), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pandawave_library_server_order, conflict.serverMembershipIds.joinToString(", ")))
            Text(stringResource(R.string.pandawave_library_your_order, conflict.proposedMembershipIds.joinToString(", ")))
            Button(
                onClick = { onReorderPlaylist(conflict.playlistId, conflict.proposedMembershipIds, conflict.serverRevision) },
                modifier = Modifier.testTag("library-playlist-confirm-reorder"),
            ) { Text(stringResource(R.string.pandawave_library_confirm_reorder)) }
        }
    }
}

private const val DRAG_REORDER_THRESHOLD = 48f

@Composable
private fun LibraryTabButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun LibraryTrackRow(
    track: LibraryTrack,
    tab: LibraryTab,
    pending: Boolean,
    saved: Boolean,
    liked: Boolean,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
    onPlay: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-track-${track.mediaId}")
            .clickable {
                onPlay(track.mediaId)
                onOpenNowPlaying()
            },
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(track.artist, track.album).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pending) {
                Text(
                    stringResource(R.string.pandawave_library_pending),
                    modifier = Modifier.testTag("library-pending-${track.mediaId}"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                if (tab == LibraryTab.SAVED) {
                    OutlinedButton(
                        onClick = { onRemoveSaved(track.mediaId) },
                        enabled = !pending,
                        modifier = Modifier.weight(1f).testTag("library-remove-${track.mediaId}"),
                    ) { Text(stringResource(R.string.pandawave_library_remove_saved)) }
                    LibraryLikeButton(track.mediaId, liked, pending, onLike, onUnlike, Modifier.weight(1f))
                } else {
                    OutlinedButton(
                        onClick = { onUnlike(track.mediaId) },
                        enabled = !pending,
                        modifier = Modifier.weight(1f).testTag("library-unlike-${track.mediaId}"),
                    ) { Text(stringResource(R.string.pandawave_library_unlike)) }
                    LibrarySaveButton(track.mediaId, saved, pending, onSave, onRemoveSaved, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LibraryLikeButton(
    mediaId: String,
    liked: Boolean,
    pending: Boolean,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = { if (liked) onUnlike(mediaId) else onLike(mediaId) },
        enabled = !pending,
        modifier = modifier.testTag("library-${if (liked) "unlike" else "like"}-$mediaId"),
    ) { Text(stringResource(if (liked) R.string.pandawave_library_unlike else R.string.pandawave_library_like)) }
}

@Composable
private fun LibrarySaveButton(
    mediaId: String,
    saved: Boolean,
    pending: Boolean,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = { if (saved) onRemoveSaved(mediaId) else onSave(mediaId) },
        enabled = !pending,
        modifier = modifier.testTag("library-${if (saved) "remove" else "save"}-$mediaId"),
    ) { Text(stringResource(if (saved) R.string.pandawave_library_remove_saved else R.string.pandawave_library_save)) }
}
