package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Artist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.components.MarqueeText
import com.crowstar.deeztrackermobile.ui.components.TrackPreviewButton
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import com.crowstar.deeztrackermobile.ui.utils.formatDuration


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    onBackClick: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    viewModel: ArtistViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadState by downloadManager.downloadState.collectAsState()
    val downloadRefreshTrigger by downloadManager.downloadRefreshTrigger.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()



    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }
    
    // Handle download state changes and show snackbar
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

    Scaffold(
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    // Artist Header
                    item {
                        artist?.let { artistData ->
                            ArtistHeader(artistData)
                        }
                    }

                    // Albums Section
                    if (albums.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Albums",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums) { album ->
                                    AlbumCard(album, onAlbumClick)
                                }
                            }
                        }
                    }

                    // Top Tracks Section
                    if (topTracks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Top Tracks",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(topTracks.size) { index ->
                            val track = topTracks[index]
                            var isDownloaded by remember { mutableStateOf(false) }
                            
                            // Check if track is downloaded, re-check when refresh trigger changes
                            LaunchedEffect(track.id, downloadRefreshTrigger) {
                                isDownloaded = downloadManager.isTrackDownloaded(
                                    track.title,
                                    track.artist?.name ?: ""
                                )
                            }
                            
                            ArtistTrackItem(
                                track = track,
                                index = index,
                                isDownloaded = isDownloaded,
                                isDownloading = downloadState is DownloadState.Downloading && 
                                    (downloadState as? DownloadState.Downloading)?.itemId == track.id.toString(),
                                onDownloadClick = {
                                    downloadManager.startTrackDownload(track.id, track.title)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
            
            // Floating Back Button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ArtistHeader(artist: Artist) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Background Image with gradient
        AsyncImage(
            model = artist.pictureXl ?: artist.pictureBig,
            contentDescription = "Artist image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.7f),
                            BackgroundDark
                        )
                    )
                )
        )

        // Artist Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Featured Artist",
                fontSize = 12.sp,
                color = Primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            MarqueeText(
                text = artist.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${artist.nbFan} Fans",
                fontSize = 14.sp,
                color = TextGray
            )
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onAlbumClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onAlbumClick(album.id) }
    ) {
        AsyncImage(
            model = album.coverMedium,
            contentDescription = album.title,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = album.releaseDate?.take(4) ?: "",
            fontSize = 12.sp,
            color = TextGray
        )
    }
}

@Composable
private fun ArtistTrackItem(
    track: Track,
    index: Int,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Number
        Text(
            text = "${index + 1}",
            fontSize = 14.sp,
            color = TextGray,
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Album Cover
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
            color = TextGray
        )

        Spacer(modifier = Modifier.width(4.dp))

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
                        tint = Color.Green
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
                        contentDescription = "Download",
                        tint = Primary
                    )
                }
            }
        }
    }
}
