package com.crowstar.deeztrackermobile.ui.playlist

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
import com.crowstar.deeztrackermobile.features.localmusic.toLocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.theme.Primary
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.library.LocalTrackItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.text.style.TextAlign

import com.crowstar.deeztrackermobile.ui.library.PlaylistTrackUiState

import androidx.compose.foundation.ExperimentalFoundationApi
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
import com.crowstar.deeztrackermobile.ui.common.PlaylistMosaic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistDetailScreen(
    playlist: LocalPlaylist,
    playlistTracks: List<PlaylistTrackUiState>,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRemoveTrack: (PlaylistTrackUiState) -> Unit,
    onShareTrack: (LocalTrack) -> Unit,
    onEditTrack: (LocalTrack) -> Unit,
    onAddToQueue: ((LocalTrack) -> Unit)? = null,
    selectionViewModel: SelectionViewModel,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val selectedTracks by selectionViewModel.selectedTracks.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    
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
        // Top App Bar like Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val covers = playlistTracks.map { it.track.albumArtUri }.distinct().take(4)
                
                PlaylistMosaic(
                    covers = covers,
                    modifier = Modifier.size(240.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                MarqueeText(
                    text = if (playlist.id == "favorites" || playlist.name == "Favorites") stringResource(R.string.playlist_favorites) else playlist.name,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.stats_playlist_tracks_format, playlistTracks.size),
                    color = TextGray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
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
            items(playlistTracks, key = { it.track.id }) { uiState ->
                LocalTrackItem(
                    track = uiState.track,
                    isSelected = selectedTracks.any { it.id == uiState.track.id },
                    inSelectionMode = isSelectionMode,
                    onClick = { 
                        if (isSelectionMode) {
                            selectionViewModel.toggleSelection(SelectedTrack.Local(uiState.track))
                        } else {
                            onTrackClick(uiState.track)
                        }
                    },
                    onLongClick = {
                        selectionViewModel.enterSelectionMode(
                            context = SelectionContext.LOCAL_PLAYLIST, 
                            initialTrack = SelectedTrack.Local(uiState.track),
                            playlistId = playlist.id
                        )
                    },
                    onShare = if (uiState.isDownloaded) { { onShareTrack(uiState.track) } } else null,
                    onDelete = { onRemoveTrack(uiState) },
                    onEdit = if (uiState.isDownloaded) { { onEditTrack(uiState.track) } } else null,
                    onAddToQueue = { onAddToQueue?.invoke(uiState.track) },
                    deleteLabel = stringResource(R.string.action_remove),
                    showAllOptions = uiState.isDownloaded
                )
            }
        }
    }
}
