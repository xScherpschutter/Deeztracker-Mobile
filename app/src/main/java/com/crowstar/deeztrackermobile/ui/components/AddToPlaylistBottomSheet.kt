package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    playlists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212), // Dark Background
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                   // Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Add to playlist",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Create New Playlist Button
            Button(
                onClick = onCreateNewPlaylist,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = TextGray)
                Spacer(modifier = Modifier.width(12.dp))
                Text("New Playlist", color = TextGray, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Favorites Item (Special)
                val favorites = playlists.find { it.id == "favorites" }
                if (favorites != null) {
                    item {
                        PlaylistItem(
                            playlist = favorites, 
                            onClick = { onPlaylistClick(favorites) },
                            isFavorite = true
                        )
                    }
                }
                
                // Other Playlists
                items(playlists.filter { it.id != "favorites" }) { playlist ->
                    PlaylistItem(
                        playlist = playlist, 
                        onClick = { onPlaylistClick(playlist) },
                        isFavorite = false
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: LocalPlaylist,
    onClick: () -> Unit,
    isFavorite: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isFavorite) Brush.linearGradient(
                        colors = listOf(Color(0xFF2196F3), Color(0xFF64B5F6)) // Blue gradient
                    ) else Brush.linearGradient(
                        colors = listOf(Color(0xFFB0BEC5), Color(0xFFECEFF1)) // Grey/White
                         // Or use an image if we implement playlist covers
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
              if (isFavorite) {
                  Icon(
                      Icons.Default.Favorite, 
                      contentDescription = null, 
                      tint = Color.White,
                      modifier = Modifier.size(24.dp)
                  )
              } else {
                   Icon(
                      Icons.Default.MusicNote, 
                      contentDescription = null, 
                      tint = Color.Black.copy(alpha=0.5f),
                      modifier = Modifier.size(24.dp)
                  )
              }
        }

        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = "${playlist.trackIds.size} songs",
                fontSize = 12.sp,
                color = TextGray
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextGray
        )
    }
}
