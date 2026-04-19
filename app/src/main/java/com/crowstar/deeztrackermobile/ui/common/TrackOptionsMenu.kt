package com.crowstar.deeztrackermobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack

@Composable
fun TrackOptionsMenu(
    track: SelectedTrack,
    onAddToQueue: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onShowDetails: (() -> Unit)? = null,
    deleteLabel: String = stringResource(R.string.action_delete),
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<SelectedTrack?>(null) }

    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = TextGray
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(SurfaceDark)
        ) {
            // Standard Options
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_playlist), color = Color.White) },
                onClick = {
                    showMenu = false
                    trackForPlaylist = track
                }
            )
            
            onAddToQueue?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_add_to_queue), color = Color.White) },
                    onClick = {
                        showMenu = false
                        action()
                    }
                )
            }

            // Optional Local-specific or extra options
            onShare?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share), color = Color.White) },
                    onClick = {
                        showMenu = false
                        action()
                    }
                )
            }

            onShowDetails?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_details), color = Color.White) },
                    onClick = {
                        showMenu = false
                        action()
                    }
                )
            }

            onEdit?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit), color = Color.White) },
                    onClick = {
                        showMenu = false
                        action()
                    }
                )
            }

            onDelete?.let { action ->
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = if (deleteLabel == stringResource(R.string.action_remove)) Color.White else Color.Red) },
                    onClick = {
                        showMenu = false
                        action()
                    }
                )
            }
        }
    }

    PlaylistActionHoster(
        track = trackForPlaylist,
        onDismiss = { trackForPlaylist = null }
    )
}
