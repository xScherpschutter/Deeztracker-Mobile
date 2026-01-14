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

@Composable
fun MusicPlayerScreen(
    onCollapse: () -> Unit,
    playerController: PlayerController = PlayerController.getInstance(LocalContext.current)
) {
    val playerState by playerController.playerState.collectAsState()
    val track = playerState.currentTrack ?: return

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
                .padding(top = 16.dp, bottom = 16.dp) // Optimized screen padding
                .verticalScroll(rememberScrollState()), // Enable scrolling for large art
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
                        text = "Local Library",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { /* Menu */ },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Options", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp)) // Optimized spacing

            // Album Art
            Box(
                modifier = Modifier
                    // Removed weight(1f) to force width-based sizing
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square based on WIDTH
                    .padding(horizontal = 8.dp), // Minimal safety padding
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize() // Fill the parent which is already square
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.DarkGray)
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp))
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

            Spacer(modifier = Modifier.height(24.dp)) // Optimized spacing above title

            // Track Info & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp) // Added local padding
            ) {
                // Info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 28.sp, // Slightly larger title
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = track.artist,
                            color = TextGray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { /* Favorite */ }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Primary)
                    }
                }

                // Progress
                Slider(
                    value = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration else 0f,
                    onValueChange = { 
                         // Seek logic here
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

                    // Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
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
                            modifier = Modifier.size(40.dp)
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

                    IconButton(onClick = { /* Repeat */ }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = TextGray)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                 // Bottom Utility Bar (Lyrics, Hi-Fi, Queue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
