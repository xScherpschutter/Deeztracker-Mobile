package com.crowstar.deeztrackermobile.ui.common

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.playlist.AddToPlaylistBottomSheet
import com.crowstar.deeztrackermobile.ui.playlist.CreatePlaylistDialog
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.crowstar.deeztrackermobile.ui.library.LocalMusicViewModel

/**
 * A central component to handle the "Add to Playlist" flow for a single track.
 * This includes showing the bottom sheet of playlists and the "Create New Playlist" dialog.
 */
@Composable
fun PlaylistActionHoster(
    track: SelectedTrack?,
    onDismiss: () -> Unit,
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    if (track == null) return

    val context = LocalContext.current
    val snackbarController = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var isSheetVisible by remember { mutableStateOf(true) }

    // If sheet was dismissed but create dialog is not showing, reset state
    LaunchedEffect(isSheetVisible, showCreateDialog) {
        if (!isSheetVisible && !showCreateDialog) {
            onDismiss()
        }
    }

    if (isSheetVisible) {
        AddToPlaylistBottomSheet(
            playlists = playlists,
            onDismiss = { isSheetVisible = false },
            onPlaylistClick = { playlist ->
                scope.launch {
                    val playlistTrack = when (track) {
                        is SelectedTrack.Local -> track.track.toPlaylistTrack()
                        is SelectedTrack.Remote -> track.track.toPlaylistTrack(
                            albumArtUri = track.backupAlbumArt,
                            albumTitle = track.source
                        )
                    }
                    viewModel.playlistRepository.addTrackToPlaylist(playlist.id, playlistTrack)
                    snackbarController.showSnackbar(
                        context.getString(R.string.toast_added_to_playlist, playlist.name)
                    )
                }
                isSheetVisible = false
            },
            onCreateNewPlaylist = {
                isSheetVisible = false
                showCreateDialog = true
            }
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { 
                showCreateDialog = false
                onDismiss()
            },
            onCreate = { newName ->
                scope.launch {
                    val newId = viewModel.playlistRepository.createPlaylist(newName)
                    val playlistTrack = when (track) {
                        is SelectedTrack.Local -> track.track.toPlaylistTrack()
                        is SelectedTrack.Remote -> track.track.toPlaylistTrack(
                            albumArtUri = track.backupAlbumArt,
                            albumTitle = track.source
                        )
                    }
                    viewModel.playlistRepository.addTrackToPlaylist(newId, playlistTrack)
                    snackbarController.showSnackbar(
                        context.getString(R.string.toast_playlist_created, newName)
                    )
                    showCreateDialog = false
                    onDismiss()
                }
            }
        )
    }
}
