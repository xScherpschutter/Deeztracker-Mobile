package com.crowstar.deeztrackermobile.ui.album

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
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackPreviewButton
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
fun AlbumScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadRefreshTrigger by viewModel.downloadRefreshTrigger.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarController = remember { SnackbarController(snackbarHostState, scope) }

    val playingUrl by viewModel.playingUrl.collectAsState()
    val previewPosition by viewModel.previewPosition.collectAsState()
    var trackToAddToPlaylist by remember { mutableStateOf<com.crowstar.deeztrackermobile.features.deezer.Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.playlists.collectAsState()

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
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
                                album?.let { albumData ->
                                    viewModel.startAlbumDownload(albumData.id, albumData.title)
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
                            if (isDownloading && (downloadState as? DownloadState.Downloading)?.itemId == albumId.toString()) {
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
                                Text(stringResource(R.string.action_download_album), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Tracks List
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        
                        // FAST CHECK: O(1) in-memory check
                        val isDownloaded = viewModel.downloadManager.isTrackDownloadedFast(
                            track.title ?: "",
                            track.artist?.name ?: ""
                        )
                        
                        TrackListItem(
                            track = track,
                            index = index + 1,
                            isDownloaded = isDownloaded,
                            isDownloading = (downloadState is DownloadState.Downloading) && (
                                // Individual track download
                                (downloadState as? DownloadState.Downloading)?.itemId == track.id.toString() ||
                                // Part of bulk album download - check if this is the current track being downloaded
                                (downloadState as? DownloadState.Downloading)?.currentTrackId == track.id.toString()
                            ),
                            isPlaying = playingUrl == track.preview,
                            previewPosition = previewPosition,
                            onTogglePreview = { viewModel.togglePreview(it) },
                            onDownloadClick = {
                                viewModel.startTrackDownload(track.id, track.title)
                            },
                            onClick = { viewModel.playAlbum(index) },
                            onAddToPlaylist = { trackToAddToPlaylist = track }
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
                    val albumArt = album?.coverBig ?: album?.coverMedium
                    scope.launch {
                        viewModel.playlistRepository.addTrackToPlaylist(
                            playlist.id, 
                            trackToSave?.toPlaylistTrack(albumArtUri = albumArt) ?: return@launch
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
        MarqueeText(
            text = album.title ?: "",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist Name
        MarqueeText(
            text = album.artist?.name ?: "Unknown Artist",
            fontSize = 16.sp,
            color = TextGray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPlaying: Boolean = false,
    previewPosition: Long = 0,
    onTogglePreview: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (index % 2 == 0) SurfaceDark.copy(alpha = 0.3f) else Color.Transparent
            )
            .clickable(onClick = onClick)
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
            MarqueeText(
                text = track.title ?: "",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            if (track.artist?.name != track.album?.title) {
                MarqueeText(
                    text = track.artist?.name ?: "Unknown Artist",
                    fontSize = 12.sp,
                    color = TextGray,
                    modifier = Modifier.fillMaxWidth()
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

        TrackOptionsMenu(onAddToPlaylist = onAddToPlaylist)
    }
}
