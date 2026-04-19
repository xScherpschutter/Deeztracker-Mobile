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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
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
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.text.style.TextAlign
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.PlaylistActionHoster
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import com.crowstar.deeztrackermobile.ui.utils.SnackbarController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistScreen(
    artistId: Long,
    onBackClick: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    selectionViewModel: SelectionViewModel,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
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

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
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
                    artist?.let { artistData ->
                        ArtistHeader(artistData)
                    }
                }

                if (albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Albums",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
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
                        val isDownloaded = downloadedKeys.contains(
                            downloadManager.generateTrackKey(track.title ?: "", track.artist?.name ?: "")
                        )
                        
                        ArtistTrackItem(
                            track = track,
                            index = index,
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
                                            source = artist?.name,
                                            backupAlbumArt = track.album?.coverBig ?: track.album?.coverMedium
                                        )
                                    )
                                } else {
                                    viewModel.playArtistTopTracks(index)
                                }
                            },
                            onLongClick = {
                                selectionViewModel.enterSelectionMode(
                                    context = SelectionContext.REMOTE, 
                                    initialTrack = SelectedTrack.Remote(
                                        track = track,
                                        source = artist?.name,
                                        backupAlbumArt = track.album?.coverBig ?: track.album?.coverMedium
                                    )
                                )
                            },
                            onAddToQueue = { 
                                viewModel.playerController.addToQueue(
                                    track = track,
                                    source = artist?.name,
                                    backupAlbumArt = track.album?.coverBig ?: track.album?.coverMedium
                                ) 
                            },
                            artist = artist
                        )
                    }
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
private fun ArtistHeader(artist: Artist) {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        AsyncImage(
            model = artist.pictureXl ?: artist.pictureBig,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.7f), BackgroundDark)
                )
            )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(text = "Featured Artist", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            MarqueeText(text = artist.name ?: "", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "${artist.nbFan} Fans", fontSize = 14.sp, color = TextGray)
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onAlbumClick: (Long) -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable { onAlbumClick(album.id) }) {
        AsyncImage(
            model = album.coverMedium,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(text = album.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.fillMaxWidth())
        Text(text = album.releaseDate?.take(4) ?: "", fontSize = 12.sp, color = TextGray)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTrackItem(
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
    artist: Artist?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(onClick = onStreamClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onStreamClick() }, colors = CheckboxDefaults.colors(checkedColor = Primary))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "${index + 1}", fontSize = 14.sp, color = TextGray, modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        AsyncImage(
            model = track.album?.coverSmall,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = track.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth())
            MarqueeText(text = track.artist?.name ?: "Unknown Artist", fontSize = 12.sp, color = TextGray, modifier = Modifier.fillMaxWidth())
        }
        Text(text = formatDuration(track.duration ?: 0), fontSize = 12.sp, color = TextGray)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onDownloadClick, enabled = !isDownloading && !isDownloaded) {
            if (isDownloaded) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(20.dp))
            else if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
            else Icon(Icons.Default.Download, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        
        TrackOptionsMenu(
            track = SelectedTrack.Remote(
                track = track,
                source = artist?.name,
                backupAlbumArt = track.album?.coverBig ?: track.album?.coverMedium
            ),
            onAddToQueue = onAddToQueue
        )
    }
}
