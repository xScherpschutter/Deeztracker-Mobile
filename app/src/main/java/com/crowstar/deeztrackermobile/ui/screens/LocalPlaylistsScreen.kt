package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R

@Composable
fun LocalPlaylistsScreen(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats Header
        item {
             Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                 Icon(
                     Icons.Default.MusicNote, 
                     contentDescription = null, 
                     tint = TextGray.copy(alpha = 0.5f),
                     modifier = Modifier.size(48.dp)
                 )
                 Text(
                     stringResource(R.string.stats_playlists_format, playlists.size),
                     color = TextGray,
                     fontSize = 12.sp,
                     fontWeight = FontWeight.Bold
                 )
            }
        }
        
        items(playlists) { playlist ->
            var showMenu by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .clickable { onPlaylistClick(playlist) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.stats_playlist_tracks_format, playlist.trackIds.size),
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.player_options),
                            tint = TextGray
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDeletePlaylist(playlist)
                            }
                        )
                    }
                }
            }
        }
    }
}
