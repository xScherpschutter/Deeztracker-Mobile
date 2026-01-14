package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistDetailScreen(
    playlist: LocalPlaylist,
    allTracks: List<LocalTrack>,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack) -> Unit,
    onRemoveTrack: (LocalTrack) -> Unit
) {
    // Filter tracks belonging to this playlist
    // Note: This preserves the order in playlist.trackIds if we wanted to, 
    // but effectively we just find the tracks.
    // If order matters, we should map trackIds to tracks.
    val playlistTracks = playlist.trackIds.mapNotNull { id -> 
        allTracks.find { it.id == id }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${playlistTracks.size} tracks",
                    color = TextGray,
                    fontSize = 14.sp
                )
            }
        }

        if (playlistTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tracks in this playlist", color = TextGray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlistTracks) { track ->
                    LocalTrackItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onShare = { },
                        onDelete = { onRemoveTrack(track) },
                        onAddToPlaylist = { },
                        deleteLabel = "Remove"
                    )
                }
            }
        }
    }
}
