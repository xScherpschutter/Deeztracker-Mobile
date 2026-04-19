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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
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
import com.crowstar.deeztrackermobile.ui.common.PlaylistActionHoster
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.R
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    selectionViewModel: SelectionViewModel,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val context = LocalContext.current
    val downloadManager = viewModel.downloadManager
    val downloadedKeys by downloadManager.downloadedKeys.collectAsState()
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val selectedTracks by selectionViewModel.selectedTracks.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    
    val scope = rememberCoroutineScope()
    val snackbarController = LocalSnackbarController.current

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = contentPadding)
            ) {
                item {
                    album?.let { albumData ->
                        AlbumHeader(albumData)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            album?.let { albumData ->
                                viewModel.startAlbumDownload(albumData.id, albumData.title)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_download_album), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                items(tracks.size) { index ->
                    val track = tracks[index]
                    val isDownloaded = downloadedKeys.contains(
                        downloadManager.generateTrackKey(track.title ?: "", track.artist?.name ?: "")
                    )
                    
                    TrackListItem(
                        track = track,
                        index = index + 1,
                        isDownloaded = isDownloaded,
                        isDownloading = activeDownloads.contains(track.id.toString()),
                        isSelected = selectedTracks.any { it.id == track.id },
                        inSelectionMode = isSelectionMode,
                        onDownloadClick = {
                            viewModel.startTrackDownload(track.id, track.title ?: "Unknown Track")
                        },
                        onStreamClick = {
                            if (isSelectionMode) {
                                selectionViewModel.toggleSelection(
                                    SelectedTrack.Remote(
                                        track = track,
                                        source = album?.title,
                                        backupAlbumArt = album?.coverBig ?: album?.coverMedium
                                    )
                                )
                            } else {
                                viewModel.playAlbum(index)
                            }
                        },
                        onLongClick = {
                            selectionViewModel.enterSelectionMode(
                                context = SelectionContext.REMOTE, 
                                initialTrack = SelectedTrack.Remote(
                                    track = track,
                                    source = album?.title,
                                    backupAlbumArt = album?.coverBig ?: album?.coverMedium
                                )
                            )
                        },
                        onAddToQueue = { 
                            val currentAlbum = album
                            viewModel.playerController.addToQueue(
                                track = track,
                                source = currentAlbum?.title,
                                backupAlbumArt = currentAlbum?.coverBig ?: currentAlbum?.coverMedium
                            ) 
                        },
                        album = album
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
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

@Composable
private fun AlbumHeader(album: Album) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.coverXl ?: album.coverBig,
            contentDescription = null,
            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(24.dp))
        MarqueeText(
            text = album.title ?: "",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = album.artist?.name ?: "Unknown Artist",
            fontSize = 16.sp,
            color = TextGray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${album.releaseDate?.take(4) ?: ""} • ${album.recordType?.uppercase() ?: ""} • ${album.nbTracks ?: 0} tracks",
            fontSize = 12.sp,
            color = TextGray
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListItem(
    track: Track,
    index: Int,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onStreamClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    album: Album?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Primary.copy(alpha = 0.15f)
                else if (index % 2 == 0) SurfaceDark.copy(alpha = 0.3f) 
                else Color.Transparent
            )
            .combinedClickable(onClick = onStreamClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onStreamClick() }, colors = CheckboxDefaults.colors(checkedColor = Primary))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "$index", fontSize = 14.sp, color = TextGray, modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = track.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.fillMaxWidth())
            if (track.artist?.name != track.album?.title) {
                MarqueeText(text = track.artist?.name ?: "Unknown Artist", fontSize = 12.sp, color = TextGray, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(text = formatDuration(track.duration ?: 0), fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = onDownloadClick, enabled = !isDownloading && !isDownloaded) {
            if (isDownloaded) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(20.dp))
            else if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
            else Icon(Icons.Default.Download, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        
        TrackOptionsMenu(
            track = SelectedTrack.Remote(
                track = track,
                source = album?.title,
                backupAlbumArt = album?.coverBig ?: album?.coverMedium
            ),
            onAddToQueue = onAddToQueue
        )
    }
}
