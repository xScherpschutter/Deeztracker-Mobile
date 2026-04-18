package com.crowstar.deeztrackermobile.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.deezer.Playlist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackPreviewButton
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import com.crowstar.deeztrackermobile.ui.utils.SnackbarController
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    onBackClick: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadedKeys by viewModel.downloadManager.downloadedKeys.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarController = remember { SnackbarController(snackbarHostState, scope) }

    val playingUrl by viewModel.playingUrl.collectAsState()
    val previewPosition by viewModel.previewPosition.collectAsState()
    var trackToAddToPlaylist by remember { mutableStateOf<com.crowstar.deeztrackermobile.features.deezer.Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.playlists.collectAsState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }
    
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

    CompositionLocalProvider(LocalSnackbarController provides snackbarController) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
                        }
                    },
                    actions = {
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
                                    viewModel.startPlaylistDownload(playlistData.id, playlistData.title)
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
                            // FAST CHECK: O(1) in-memory check
                            val isDownloaded = downloadedKeys.contains(
                                viewModel.downloadManager.generateTrackKey(
                                    track.title,
                                    track.artist?.name ?: ""
                                )
                            )
                            
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
                                isPlaying = playingUrl == track.preview,
                                previewPosition = previewPosition,
                                onTogglePreview = { viewModel.togglePreview(it) },
                                onDownloadClick = {
                                    viewModel.startTrackDownload(track.id, track.title)
                                },
                                onStreamClick = {
                                    viewModel.playPlaylist(index)
                                },
                                onAddToPlaylist = { trackToAddToPlaylist = track },
                                onAddToQueue = { viewModel.playerController.addToQueue(track) }
                            )

                        }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        if (trackToAddToPlaylist != null) {
            com.crowstar.deeztrackermobile.ui.playlist.AddToPlaylistBottomSheet(
                playlists = localPlaylists,
                onDismiss = { trackToAddToPlaylist = null },
                onPlaylistClick = { playlist ->
                    val trackToSave = trackToAddToPlaylist
                    scope.launch {
                        viewModel.playlistRepository.addTrackToPlaylist(
                            playlist.id, 
                            trackToSave?.toPlaylistTrack() ?: return@launch
                        )
                    }
                    trackToAddToPlaylist = null
                },
                onCreateNewPlaylist = { showCreatePlaylistDialog = true }
            )
        }

        if (showCreatePlaylistDialog) {
            com.crowstar.deeztrackermobile.ui.playlist.CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { newPlaylistName ->
                    scope.launch {
                        viewModel.playlistRepository.createPlaylist(newPlaylistName)
                        snackbarController.showSnackbar(
                            context.getString(R.string.toast_playlist_created, newPlaylistName)
                        )
                    }
                    showCreatePlaylistDialog = false
                }
            )
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
            text = playlist.title ?: "",
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
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPlaying: Boolean = false,
    previewPosition: Long = 0,
    onTogglePreview: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onStreamClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onAddToQueue: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (index % 2 == 0) SurfaceDark.copy(alpha = 0.3f) else Color.Transparent
            )
            .clickable(onClick = onStreamClick)
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

        TrackOptionsMenu(onAddToPlaylist = onAddToPlaylist, onAddToQueue = onAddToQueue)
    }
}
