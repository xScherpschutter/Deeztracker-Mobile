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
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.formatDuration


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
    
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadState by downloadManager.downloadState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }
    
    // Handle download state changes
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                val message = if (state.failedCount > 0) {
                    "Downloaded ${state.successCount} tracks, ${state.failedCount} failed"
                } else {
                    "Downloaded: ${state.title}"
                }
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                downloadManager.resetState()
            }
            is DownloadState.Error -> {
                snackbarHostState.showSnackbar(
                    "Download failed: ${state.message}",
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            else -> {}
        }
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 100.dp) // Avoid overlapping floating elements
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
                    val isDownloading = downloadState is DownloadState.Downloading
                    Button(
                        onClick = {
                            if (!isDownloading) {
                                album?.let { albumData ->
                                    downloadManager.startAlbumDownload(albumData.id, albumData.title)
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
                        if (isDownloading && (downloadState as? DownloadState.Downloading)?.itemId == albumId.toString()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Album", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Tracks List
                items(tracks.size) { index ->
                    val track = tracks[index]
                    TrackListItem(
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
private fun TrackListItem(
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
