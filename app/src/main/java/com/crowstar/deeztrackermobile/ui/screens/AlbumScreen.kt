package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    viewModel: AlbumViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val album by viewModel.album.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Album Header
                item {
                    album?.let { albumData ->
                        AlbumHeader(albumData)
                    }
                }

                // Download Album Button
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { /* TODO: Implement album download */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Album", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Tracks List
                items(tracks.size) { index ->
                    TrackListItem(
                        track = tracks[index],
                        index = index + 1
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album Artwork
        AsyncImage(
            model = album.coverXl ?: album.coverBig,
            contentDescription = album.title,
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Album Title
        Text(
            text = album.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist Name
        Text(
            text = album.artist?.name ?: "Unknown Artist",
            fontSize = 16.sp,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Album Info
        Text(
            text = "${album.releaseDate?.take(4) ?: ""} • ${album.recordType?.uppercase() ?: ""} • ${album.nbTracks ?: 0} tracks",
            fontSize = 12.sp,
            color = TextGray
        )
    }
}

@Composable
private fun TrackListItem(track: Track, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (index % 2 == 0) SurfaceDark.copy(alpha = 0.3f) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Number
        Text(
            text = "$index",
            fontSize = 14.sp,
            color = TextGray,
            modifier = Modifier.width(32.dp)
        )

        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
            if (track.artist?.name != track.album?.title) {
                Text(
                    text = track.artist?.name ?: "Unknown Artist",
                    fontSize = 12.sp,
                    color = TextGray,
                    maxLines = 1
                )
            }
        }

        // Duration
        Text(
            text = formatDuration(track.duration ?: 0),
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Download Button
        IconButton(onClick = { /* TODO: Implement track download */ }) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Download track",
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}
