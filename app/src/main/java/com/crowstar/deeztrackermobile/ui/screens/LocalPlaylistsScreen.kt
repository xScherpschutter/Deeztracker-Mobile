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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun LocalPlaylistsScreen(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit
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
                     "${playlists.size} Playlists",
                     color = TextGray,
                     fontSize = 12.sp,
                     fontWeight = FontWeight.Bold
                 )
            }
        }
        
        // Favorites First
        val favorites = playlists.find { it.id == "favorites" }
        if (favorites != null) {
            item {
                PlaylistCard(
                    playlist = favorites,
                    isFavorite = true,
                    onClick = { onPlaylistClick(favorites) }
                )
            }
        }

        items(playlists.filter { it.id != "favorites" }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                isFavorite = false,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: LocalPlaylist,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isFavorite) Brush.linearGradient(
                        colors = listOf(Color(0xFFFF2D55), Color(0xFFFF5E7D)) // Red/Pink for Favorites (from prototype)
                    ) else Brush.linearGradient(
                        colors = listOf(Color(0xFF424242), Color(0xFF616161))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
              if (isFavorite) {
                  Icon(
                      Icons.Default.Favorite, 
                      contentDescription = null, 
                      tint = Color.White,
                      modifier = Modifier.size(28.dp)
                  )
              } else {
                   Icon(
                      Icons.Default.MusicNote, 
                      contentDescription = null, 
                      tint = Color.White.copy(alpha=0.5f),
                      modifier = Modifier.size(28.dp)
                  )
              }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${playlist.trackIds.size} tracks",
                color = TextGray,
                fontSize = 14.sp
            )
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextGray
        )
    }
}
