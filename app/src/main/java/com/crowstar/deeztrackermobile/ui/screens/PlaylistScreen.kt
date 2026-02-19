package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.crowstar.deeztrackermobile.ui.components.MarqueeText
import com.crowstar.deeztrackermobile.ui.components.TrackPreviewButton
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


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
    val downloadRefreshTrigger by downloadManager.downloadRefreshTrigger.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }



    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }
    
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                val message = buildString {
                    append(state.title)
                    if (state.successCount > 0) append(": ${state.successCount} downloaded")
                    if (state.skippedCount > 0) append(", ${state.skippedCount} already had")
                    if (state.failedCount > 0) append(", ${state.failedCount} failed")
                    if (state.successCount == 0 && state.skippedCount == 0 && state.failedCount == 0) {
                        append(" downloaded successfully")
                    }
                }
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            is DownloadState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "Error: ${state.message}",
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            else -> { /* Idle or Downloading - no snackbar */ }
        }
    }

    // Stop preview when leaving this screen
    DisposableEffect(Unit) {
        onDispose { PreviewPlayer.stop() }
    }

    // Stop preview when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) PreviewPlayer.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                            playlist?.let { playlistData ->
                                downloadManager.startPlaylistDownload(playlistData.id, playlistData.title)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary
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

                // Tracks List
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        var isDownloaded by remember { mutableStateOf(false) }
                        
                        // Check if track is downloaded, re-check when refresh trigger changes
                        LaunchedEffect(track.id, downloadRefreshTrigger) {
                            isDownloaded = downloadManager.isTrackDownloaded(
                                track.title,
                                track.artist?.name ?: ""
                            )
                        }
                        
                        PlaylistTrackItem(
                            track = track,
                            index = index + 1,
                            isDownloaded = isDownloaded,
                            isDownloading = (downloadState is DownloadState.Downloading) && (
                                // Individual track download
                                (downloadState as? DownloadState.Downloading)?.itemId == track.id.toString() ||
                                // Part of bulk playlist download - check if this is the current track being downloaded
                                (downloadState as? DownloadState.Downloading)?.currentTrackId == track.id.toString()
                            ),
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
        MarqueeText(
            text = playlist.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Creator Name
        MarqueeText(
            text = playlist.creator?.name ?: "Unknown Creator",
            fontSize = 16.sp,
            color = TextGray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    isDownloaded: Boolean,
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
            MarqueeText(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            MarqueeText(
                text = track.artist?.name ?: "Unknown Artist",
                fontSize = 12.sp,
                color = TextGray,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Duration
        Text(
            text = formatDuration(track.duration ?: 0),
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Preview Button
        TrackPreviewButton(previewUrl = track.preview)

        // Download Button
        IconButton(
            onClick = onDownloadClick,
            enabled = !isDownloading && !isDownloaded
        ) {
            when {
                isDownloaded -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = Color.Green,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download track",
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
