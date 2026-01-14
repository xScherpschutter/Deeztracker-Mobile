package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import kotlinx.coroutines.launch
import com.crowstar.deeztrackermobile.ui.utils.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    onCollapse: () -> Unit,
    playerController: PlayerController = PlayerController.getInstance(LocalContext.current)
) {
    val playerState by playerController.playerState.collectAsState()
    val track = playerState.currentTrack ?: return
    
    // Playlist State
    val playlists by playerController.playlistRepository.playlists.collectAsState()
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Background Layer
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurry Background
        if (track.albumArtUri != null) {
            AsyncImage(
                model = track.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
                    .scale(1.5f),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            )
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color(0xFF000000) // Background Dark
                        )
                    )
                )
        )
        // Darken overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        color = TextGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = playerState.playingSource,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Options", tint = Color.White)
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showAddToPlaylist = true
                            }
                        )
                    }
                }
            }
            
            // Album Art
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.DarkGray)
                ) {
                    if (track.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            // Track Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = track.artist,
                        color = TextGray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { playerController.toggleFavorite() }) {
                    Icon(
                        if(playerState.isCurrentTrackFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        contentDescription = "Like", 
                        tint = Primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Slider(
                    value = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration else 0f,
                    onValueChange = { 
                         playerController.seekTo((it * playerState.duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playerState.currentPosition),
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(playerState.duration),
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerController.setShuffle(!playerState.isShuffleEnabled) }) {
                    Icon(
                        Icons.Default.Shuffle, 
                        contentDescription = "Shuffle", 
                        tint = if(playerState.isShuffleEnabled) Primary else TextGray
                    )
                }
                
                IconButton(onClick = { playerController.previous() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious, 
                        contentDescription = "Previous", 
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Primary, Color(0xFF0066CC))
                            )
                        )
                        .clickable { playerController.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if(playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { playerController.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipNext, 
                        contentDescription = "Next", 
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { playerController.toggleRepeatMode() }) {
                    val (icon, tint) = when (playerState.repeatMode) {
                        com.crowstar.deeztrackermobile.features.player.RepeatMode.ONE -> Icons.Default.RepeatOne to Primary
                        com.crowstar.deeztrackermobile.features.player.RepeatMode.ALL -> Icons.Default.Repeat to Primary
                        else -> Icons.Default.Repeat to TextGray
                    }
                    Icon(icon, contentDescription = "Repeat", tint = tint)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
             // Bottom Utility Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Lyrics",
                    tint = TextGray
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HI-FI", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                Icon(
                    Icons.Default.List,
                    contentDescription = "Queue",
                    tint = TextGray
                )
            }
        }
        
        // Playlist Sheet
        if (showAddToPlaylist) {
             com.crowstar.deeztrackermobile.ui.components.AddToPlaylistBottomSheet(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onPlaylistClick = { playlist ->
                    kotlinx.coroutines.GlobalScope.launch {
                        playerController.playlistRepository.addTrackToPlaylist(playlist.id, track.id)
                    }
                    showAddToPlaylist = false
                },
                onCreateNewPlaylist = { showCreatePlaylistDialog = true }
            )
        }
        
         if (showCreatePlaylistDialog) {
             var newPlaylistName by remember { mutableStateOf("") }
             AlertDialog(
                 onDismissRequest = { showCreatePlaylistDialog = false },
                 title = { Text("New Playlist", color = Color.White) },
                 text = {
                     OutlinedTextField(
                         value = newPlaylistName,
                         onValueChange = { newPlaylistName = it },
                         label = { Text("Playlist Name") },
                         singleLine = true,
                         colors = TextFieldDefaults.outlinedTextFieldColors(
                             focusedTextColor = Color.White,
                             unfocusedTextColor = Color.White,
                             focusedBorderColor = Primary,
                             unfocusedBorderColor = TextGray,
                             cursorColor = Primary
                         )
                     )
                 },
                 confirmButton = {
                     Button(
                         onClick = {
                             if (newPlaylistName.isNotBlank()) {
                                 kotlinx.coroutines.GlobalScope.launch {
                                    playerController.playlistRepository.createPlaylist(newPlaylistName)
                                 }
                                 showCreatePlaylistDialog = false
                             }
                         },
                         colors = ButtonDefaults.buttonColors(containerColor = Primary)
                     ) {
                         Text("Create")
                     }
                 },
                 dismissButton = {
                     TextButton(onClick = { showCreatePlaylistDialog = false }) {
                         Text("Cancel", color = TextGray)
                     }
                 },
                 containerColor = com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
             )
        }
    }
}
