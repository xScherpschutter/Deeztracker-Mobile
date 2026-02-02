package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
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
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.theme.Primary
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.components.MarqueeText
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistDetailScreen(
    playlist: LocalPlaylist,
    allTracks: List<LocalTrack>,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRemoveTrack: (LocalTrack) -> Unit,
    onShareTrack: (LocalTrack) -> Unit,
    onEditTrack: (LocalTrack) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    // Filter tracks belonging to this playlist
    val playlistTracks = playlist.trackIds.mapNotNull { id -> 
        allTracks.find { it.id == id }
    }
    
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp, 
            bottom = 16.dp + contentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    MarqueeText(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.stats_playlist_tracks_format, playlistTracks.size),
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // Play & Shuffle Buttons
        if (playlistTracks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = onPlayPlaylist,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_play_playlist))
                    }

                    // Shuffle Button
                    Button(
                        onClick = onShufflePlaylist,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) {
                         Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.White)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text("Shuffle", color = Color.White)
                    }
                }
            }
        }

        if (playlistTracks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.playlist_empty), color = TextGray)
                }
            }
        } else {
            items(playlistTracks) { track ->
                LocalTrackItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onShare = { onShareTrack(track) },
                    onDelete = { onRemoveTrack(track) },
                    onEdit = { onEditTrack(track) },
                    onAddToPlaylist = null,
                    deleteLabel = stringResource(R.string.action_remove)
                )
            }
        }
    }
}
