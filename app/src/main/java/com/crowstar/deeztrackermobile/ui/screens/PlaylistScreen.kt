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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.deezer.Playlist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    onBackClick: () -> Unit,
    viewModel: PlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadState by downloadManager.downloadState.collectAsState()



    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }
    
    // Handle download state changes


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
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
                // Playlist Header
                item {
                    playlist?.let { playlistData ->
                        PlaylistHeader(playlistData)
                    }
                }

                // Download Playlist Button
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    val isDownloading = downloadState is DownloadState.Downloading
                    Button(
                        onClick = {
                            if (!isDownloading) {
                                playlist?.let { playlistData ->
                                    downloadManager.startPlaylistDownload(playlistData.id, playlistData.title)
                                }
                            }
                        },
                        enabled = !isDownloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Primary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (isDownloading && (downloadState as? DownloadState.Downloading)?.itemId == playlistId.toString()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_downloading), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_download_playlist), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Tracks Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.action_queue),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray
                        )
                        Text(
                            text = stringResource(R.string.action_date_added),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray
                        )
                    }
                }

                // Tracks List
                items(tracks.size) { index ->
                    val track = tracks[index]
                    PlaylistTrackItem(
                        track = track,
                        index = index + 1,
                        isDownloading = downloadState is DownloadState.Downloading,
                        onDownloadClick = {
                            downloadManager.startTrackDownload(track.id, track.title)
                        }
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
private fun PlaylistHeader(playlist: Playlist) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Playlist Cover
        AsyncImage(
            model = playlist.pictureXl ?: playlist.pictureBig,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Playlist Title
        Text(
            text = playlist.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Creator Name
        Text(
            text = playlist.creator?.name ?: "Unknown Creator",
            fontSize = 16.sp,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Playlist Info
        val duration = playlist.duration?.let { 
            val hours = it / 3600
            val minutes = (it % 3600) / 60
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        } ?: ""
        
        Text(
            text = "${playlist.nbTracks ?: 0} Tracks • $duration",
            fontSize = 12.sp,
            color = TextGray
        )
    }
}

@Composable
private fun PlaylistTrackItem(
    track: Track,
    index: Int,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (index % 2 == 0) SurfaceDark.copy(alpha = 0.3f) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Cover
        AsyncImage(
            model = track.album?.coverSmall,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = track.artist?.name ?: "Unknown Artist",
                fontSize = 12.sp,
                color = TextGray,
                maxLines = 1
            )
        }

        // Duration
        Text(
            text = formatDuration(track.duration ?: 0),
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Download Button
        IconButton(
            onClick = onDownloadClick,
            enabled = !isDownloading
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Download track",
                tint = if (isDownloading) TextGray else Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
