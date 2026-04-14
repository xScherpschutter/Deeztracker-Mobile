package com.crowstar.deeztrackermobile.ui.artist

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Artist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackPreviewButton
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import com.crowstar.deeztrackermobile.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    onBackClick: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadRefreshTrigger by viewModel.downloadRefreshTrigger.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val playingUrl by viewModel.playingUrl.collectAsState()
    val previewPosition by viewModel.previewPosition.collectAsState()

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }
    
    // Handle download state changes and show snackbar
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.notification_download_completed, state.title),
                    duration = SnackbarDuration.Short
                )
                viewModel.resetDownloadState()
            }
            is DownloadState.Error -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.notification_download_failed, state.message),
                    duration = SnackbarDuration.Short
                )
                viewModel.resetDownloadState()
            }
            else -> { /* Idle or Downloading - no snackbar */ }
        }
    }

    // Stop preview when leaving this screen
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    // Stop preview when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stopPreview()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                                viewModel.isTrackDownloaded(
                                    track.title,
                                    track.artist?.name ?: ""
                                ) { result ->
                                    isDownloaded = result
                                }
                            }
                            
                            ArtistTrackItem(
                                track = track,
                                index = index,
                                isDownloaded = isDownloaded,
                                isDownloading = downloadState is DownloadState.Downloading && 
                                    (downloadState as? DownloadState.Downloading)?.itemId == track.id.toString(),
                                isPlaying = playingUrl == track.preview,
                                previewPosition = previewPosition,
                                onTogglePreview = { viewModel.togglePreview(it) },
                                onDownloadClick = {
                                    viewModel.startTrackDownload(track.id, track.title)
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
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPlaying: Boolean = false,
    previewPosition: Long = 0,
    onTogglePreview: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {}
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
        TrackPreviewButton(
            previewUrl = track.preview,
            isPlaying = isPlaying,
            positionMs = previewPosition,
            onToggle = onTogglePreview
        )

        // Download Button
        IconButton(
            onClick = onDownloadClick,
            enabled = !isDownloading && !isDownloaded
        ) {
            when {
                isDownloaded -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.desc_downloaded),
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
