package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.components.MarqueeText

@Composable
fun MiniPlayer(
    onClick: () -> Unit,
    playerController: PlayerController = PlayerController.getInstance(LocalContext.current)
) {
    val playerState by playerController.playerState.collectAsState()
    val track = playerState.currentTrack

    if (track != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp) // Lift slightly above bottom bar or edge
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E).copy(alpha = 0.90f)) // Glassy look
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Art
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark)
                    ) {
                        if (track.albumArtUri != null) {
                            AsyncImage(
                                model = track.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Fallback
                            Box(modifier = Modifier.fillMaxSize().background(TextGray))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MarqueeText(
                            text = track.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MarqueeText(
                            text = track.artist,
                            color = TextGray,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }



//...

                    // Controls
                    IconButton(onClick = { playerController.togglePlayPause() }) {
                        Icon(
                            if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_play_pause),
                            tint = Color.White
                        )
                    }
                    
                    IconButton(onClick = { playerController.next() }) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tint = Color.White
                        )
                    }
                }
                
                // Progress Bar at bottom of MiniPlayer
                if (playerState.duration > 0) {
                     Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playerState.currentPosition.toFloat() / playerState.duration)
                                .height(2.dp)
                                .background(Primary)
                        )
                    }
                }
            }
        }
    }
}
