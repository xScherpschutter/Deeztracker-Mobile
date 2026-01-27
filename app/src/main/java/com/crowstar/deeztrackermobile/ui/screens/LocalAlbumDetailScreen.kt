package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import androidx.compose.ui.text.style.TextAlign
import com.crowstar.deeztrackermobile.ui.components.MarqueeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAlbumDetailScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onPlayAlbum: (List<LocalTrack>) -> Unit,
    viewModel: LocalMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalMusicViewModelFactory(LocalContext.current)
    )
) {
    val albums by viewModel.albums.collectAsState()
    val tracks by viewModel.loadedAlbumTracks.collectAsState()
    
    // Find album metadata from the list (or we could fetch single album)
    val album = albums.find { it.id == albumId }

    LaunchedEffect(albumId) {
        viewModel.loadTracksForAlbum(albumId)
    }

    if (album == null) {
         Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
             CircularProgressIndicator(color = Primary)
         }
         return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (album?.albumArtUri != null) {
                         AsyncImage(
                             model = album.albumArtUri,
                             contentDescription = null,
                             modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)),
                             contentScale = ContentScale.Crop
                         )
                         Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    MarqueeText(
                        text = album?.title ?: "",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MarqueeText(
                        text = album?.artist ?: "",
                        color = TextGray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                     Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onPlayAlbum(tracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                         Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text(stringResource(R.string.action_play_album), color = Color.White)
                    }
                     Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Tracks
            items(tracks) { track ->
                LocalTrackItemSimple(track = track, onClick = { onTrackClick(track, tracks) })
            }
        }
    }
}

@Composable
fun LocalTrackItemSimple(track: LocalTrack, onClick: () -> Unit) {
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
            MarqueeText(text = track.title, color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            MarqueeText(text = track.artist, color = TextGray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = track.getFormattedDuration(),
            color = TextGray,
            fontSize = 12.sp
        )
    }
}
