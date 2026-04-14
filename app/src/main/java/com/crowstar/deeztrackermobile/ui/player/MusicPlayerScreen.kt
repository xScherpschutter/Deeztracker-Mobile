package com.crowstar.deeztrackermobile.ui.player

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
import com.crowstar.deeztrackermobile.features.player.RepeatMode
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import kotlinx.coroutines.launch
import com.crowstar.deeztrackermobile.ui.utils.formatTime
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    val playerController: PlayerController
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerScreen(
    onCollapse: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val playerController = viewModel.playerController
    val playerState by playerController.playerState.collectAsState()
    val currentQueue by playerController.currentQueue.collectAsState()
    val track = playerState.currentTrack ?: return
    
    // UI State
    val playlists by playerController.playlistRepository.playlists.collectAsState()
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarController = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    // Pager State
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background artwork
        com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
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
                            Color(0xFF000000)
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
                    modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .padding(horizontal = 20.dp), 
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
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.player_playing_from),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextGray,
                                letterSpacing = 1.sp
                            )
                            MarqueeText(
                                text = track.album,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.player_options), tint = Color.White)
                        }
                    }

                    // Album Art
                    com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                        model = track.albumArtUri,
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                    )

                    // Info and Controls
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Track Info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    text = track.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                MarqueeText(
                                    text = track.artist,
                                    fontSize = 17.sp,
                                    color = TextGray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            
                            IconButton(onClick = { playerController.toggleFavorite() }) {
                                Icon(
                                    imageVector = if (playerState.isCurrentTrackFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.player_like),
                                    tint = if (playerState.isCurrentTrackFavorite) Primary else Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress and Playback
                        PlayerControls(playerController, playerState)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Footer Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { playerController.setShuffle(!playerState.isShuffleEnabled) }) {
                                Icon(
                                    Icons.Default.Shuffle, 
                                    contentDescription = stringResource(R.string.player_shuffle),
                                    tint = if (playerState.isShuffleEnabled) Primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                            
                            IconButton(onClick = { showQueue = true }) {
                                Icon(Icons.Default.List, contentDescription = stringResource(R.string.player_queue), tint = Color.White.copy(alpha = 0.6f))
                            }
                            
                            IconButton(onClick = { playerController.toggleRepeatMode() }) {
                                val icon = when(playerState.repeatMode) {
                                    RepeatMode.ONE -> Icons.Default.RepeatOne
                                    RepeatMode.ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                }
                                Icon(
                                    icon, 
                                    contentDescription = stringResource(R.string.player_repeat),
                                    tint = if (playerState.repeatMode != RepeatMode.OFF) Primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
             } else {
                 // Lyrics Page
                 Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    // Centered Banner
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                            model = track.albumArtUri,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MarqueeText(
                            text = track.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MarqueeText(
                            text = track.artist,
                            color = TextGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

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

        // Dialogs and Sheets (Outside pager)
        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false },
                containerColor = Color(0xFF1A1A1A)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_add_to_playlist), color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White) },
                        modifier = Modifier.clickable { 
                            showMenu = false
                            showAddToPlaylist = true 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        if (showAddToPlaylist) {
             com.crowstar.deeztrackermobile.ui.playlist.AddToPlaylistBottomSheet(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onPlaylistClick = { playlist ->
                    scope.launch {
                        playerController.playlistRepository.addTrackToPlaylist(playlist.id, track.id)
                        snackbarController.showSnackbar(
                            context.getString(R.string.toast_added_to_playlist, playlist.name)
                        )
                    }
                    showAddToPlaylist = false
                },
                onCreateNewPlaylist = { showCreatePlaylistDialog = true }
            )
        }

        if (showQueue) {
            QueueBottomSheet(
                queue = currentQueue,
                currentTrack = playerState.currentTrack,
                onDismiss = { showQueue = false },
                onTrackClick = { index ->
                    playerController.seekToQueueIndex(index)
                },
                onMoveTrack = { from, to ->
                    playerController.moveTrack(from, to)
                },
                onRemoveTrack = { index ->
                    playerController.removeTrack(index)
                }
            )
        }
        
         if (showCreatePlaylistDialog) {
             var newPlaylistName by remember { mutableStateOf("") }
             AlertDialog(
                 onDismissRequest = { showCreatePlaylistDialog = false },
                 title = { Text(stringResource(R.string.new_playlist_title), color = Color.White) },
                 text = {
                     OutlinedTextField(
                         value = newPlaylistName,
                         onValueChange = { newPlaylistName = it },
                         label = { Text(stringResource(R.string.new_playlist_name)) },
                         singleLine = true,
                         colors = OutlinedTextFieldDefaults.colors(
                             focusedTextColor = Color.White,
                             unfocusedTextColor = Color.White,
                             focusedBorderColor = Primary,
                             unfocusedBorderColor = TextGray,
                             cursorColor = Primary,
                         )
                     )
                 },
                 confirmButton = {
                     Button(
                         onClick = {
                             if (newPlaylistName.isNotBlank()) {
                                 scope.launch {
                                     playerController.playlistRepository.createPlaylist(newPlaylistName)
                                     snackbarController.showSnackbar(
                                         context.getString(R.string.toast_playlist_created, newPlaylistName)
                                     )
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
                 containerColor = BackgroundDark
             )
         }
    }
}

@Composable
fun PlayerControls(
    playerController: PlayerController,
    playerState: com.crowstar.deeztrackermobile.features.player.PlayerState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val position = playerState.currentPosition.toFloat()
        val duration = playerState.duration.coerceAtLeast(1L).toFloat()
        
        Slider(
            value = position,
            onValueChange = { playerController.seekTo(it.toLong()) },
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(playerState.currentPosition), fontSize = 12.sp, color = TextGray)
            Text(text = formatTime(playerState.duration), fontSize = 12.sp, color = TextGray)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { playerController.previous() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(32.dp))
            }

            val playPauseScale by animateFloatAsState(if (playerState.isPlaying) 1.1f else 1f, label = "playPauseScale")
            
            IconButton(
                onClick = { playerController.togglePlayPause() },
                modifier = Modifier
                    .size(64.dp)
                    .scale(playPauseScale)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.player_play_pause),
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { playerController.next() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}
