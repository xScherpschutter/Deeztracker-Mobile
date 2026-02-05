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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import kotlinx.coroutines.launch
import com.crowstar.deeztrackermobile.ui.utils.formatTime
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.components.MarqueeText

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Pager State
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Background Layer
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurry Background
        com.crowstar.deeztrackermobile.ui.components.TrackArtwork(
            model = track.albumArtUri,
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp)
                .scale(1.5f),
            contentScale = ContentScale.Crop
        )

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

        // Main Content Pager
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
             if (page == 0) {
                 // Player Page
                 Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), tint = Color.White)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.player_playing_from),
                                color = TextGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            MarqueeText(
                                text = playerState.playingSource,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.player_options), tint = Color.White)
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_add_to_playlist), color = Color.White) }, // Used common action string
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
                            .fillMaxWidth()
                            .padding(vertical = 42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f) 
                                .aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.DarkGray) // Base background
                        ) {
                             com.crowstar.deeztrackermobile.ui.components.TrackArtwork(
                                model = track.albumArtUri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Track Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 34.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MarqueeText(
                                text = track.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            MarqueeText(
                                text = track.artist,
                                color = TextGray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(onClick = { playerController.toggleFavorite() }) {
                            Icon(
                                if(playerState.isCurrentTrackFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                contentDescription = stringResource(R.string.player_like), 
                                tint = Primary
                            )
                        }
                    }
                    

                    
                    Spacer(modifier = Modifier.height(42.dp))

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

                    Spacer(modifier = Modifier.height(32.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { playerController.setShuffle(!playerState.isShuffleEnabled) }) {
                            Icon(
                                Icons.Default.Shuffle, 
                                contentDescription = stringResource(R.string.player_shuffle), 
                                tint = if(playerState.isShuffleEnabled) Primary else TextGray
                            )
                        }
                        
                        IconButton(onClick = { playerController.previous() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious, 
                                contentDescription = stringResource(R.string.player_previous), 
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
                                contentDescription = stringResource(R.string.player_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { playerController.next() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipNext, 
                                contentDescription = stringResource(R.string.player_next), 
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
                            Icon(icon, contentDescription = stringResource(R.string.player_repeat), tint = tint)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
             } else {
                 // Lyrics Page
                 Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, bottom = 16.dp)
                 ) {
                     // Centered Header for Lyrics Page
                     Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(48.dp) // Match button size/height roughly
                    ) {
                         IconButton(
                            onClick = onCollapse,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                .align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), tint = Color.White)
                        }
                        
                        Text(
                            text = stringResource(R.string.lyrics_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                     }
                     
                     LyricsScreen(
                         lyrics = playerState.lyrics,
                         currentIndex = playerState.currentLyricIndex,
                         isLoading = playerState.isLoadingLyrics,
                         onLineClick = { position ->
                             playerController.seekTo(position)
                         }
                     )
                 }
             }
        }
        
        // Page Indicators (Optional but good for UX)
        // Leaving out for now as user just asked for swipe, and minimalistic is key.

        
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
                 title = { Text(stringResource(R.string.new_playlist_title), color = Color.White) }, // Reusing strings from LocalMusicScreen.kt additions
                 text = {
                     OutlinedTextField(
                         value = newPlaylistName,
                         onValueChange = { newPlaylistName = it },
                         label = { Text(stringResource(R.string.new_playlist_name)) },
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
                         Text(stringResource(R.string.action_create))
                     }
                 },
                 dismissButton = {
                     TextButton(onClick = { showCreatePlaylistDialog = false }) {
                         Text(stringResource(R.string.action_cancel), color = TextGray)
                     }
                 },
                 containerColor = com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
             )
        }
    }
}
