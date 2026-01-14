package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.text.style.TextAlign
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalArtistDetailScreen(
    artistName: String,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onPlayArtist: (List<LocalTrack>) -> Unit,
    viewModel: LocalMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalMusicViewModelFactory(LocalContext.current)
    )
) {
    val tracks by viewModel.loadedArtistTracks.collectAsState()
    
    // Decode artist name if passing via URL changed special chars (though navigation arguments handle this, safe to ensure)
    // Actually typically handled by Navigation key decoding.
    
    LaunchedEffect(artistName) {
        viewModel.loadTracksForArtist(artistName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
             modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                     Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = artistName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${tracks.size} tracks",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                     Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onPlayArtist(tracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                         Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text("Play Artist", color = Color.White)
                    }
                     Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Tracks
            items(tracks) { track ->
                ArtistTrackItem(track = track, onClick = { onTrackClick(track, tracks) })
            }
        }
    }
}

@Composable
private fun ArtistTrackItem(track: LocalTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val trackNumber = track.track?.rem(1000) ?: 0
        Text(
            text = if (trackNumber > 0) "$trackNumber" else "",
            color = TextGray,
            fontSize = 14.sp,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, color = Color.White, fontSize = 16.sp, maxLines = 1)
            Text(text = track.artist, color = TextGray, fontSize = 12.sp, maxLines = 1)
        }
        Text(
            text = track.getFormattedDuration(),
            color = TextGray,
            fontSize = 12.sp
        )
    }
}
