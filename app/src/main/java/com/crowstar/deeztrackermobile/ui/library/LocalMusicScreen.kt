package com.crowstar.deeztrackermobile.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
import com.crowstar.deeztrackermobile.features.localmusic.LocalArtist
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.crowstar.deeztrackermobile.features.localmusic.toLocalTrack
import com.crowstar.deeztrackermobile.ui.playlist.LocalPlaylistDetailScreen
import com.crowstar.deeztrackermobile.ui.playlist.LocalPlaylistsScreen
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import com.crowstar.deeztrackermobile.ui.common.AlphabeticalFastScroller
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.PlaylistActionHoster
import com.crowstar.deeztrackermobile.features.localmusic.TrackMetadata
import com.crowstar.deeztrackermobile.ui.library.EditTrackDialog
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalMusicScreen(
    onTrackClick: (LocalTrack, List<LocalTrack>, String?) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
    onImportPlaylist: () -> Unit,
    onAddToQueue: ((LocalTrack) -> Unit)? = null,
    selectionViewModel: SelectionViewModel,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onSelectAllUpdate: (() -> Unit) -> Unit = {},
    onTrackCountUpdate: (Int) -> Unit = {},
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    val tracks by viewModel.tracks.collectAsState()
    val unfilteredTracks by viewModel.unfilteredTracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val totalStorage by viewModel.totalStorage.collectAsState()
    
    val context = LocalContext.current
    val snackbarController = LocalSnackbarController.current
    val localMusicTitle = stringResource(R.string.local_music_title)
    
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    LaunchedEffect(isLoading, searchQuery) {
        if (!isLoading) {
             viewModel.searchTracks(searchQuery)
        }
    }
    
    val tracksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val albumsGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val artistsGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val playlistsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    
    fun shareTrack(track: LocalTrack) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(track.filePath))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (shareIntent.resolveActivity(context.packageManager) != null) {
            val chooserTitle = context.getString(R.string.intent_share_track)
            context.startActivity(android.content.Intent.createChooser(shareIntent, chooserTitle))
        }
    }

    val deleteIntentSender by viewModel.deleteIntentSender.collectAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteSuccess()
        }
        viewModel.resetDeleteIntentSender()
    }

    LaunchedEffect(deleteIntentSender) {
        deleteIntentSender?.let { sender ->
            val request = IntentSenderRequest.Builder(sender).build()
            deleteLauncher.launch(request)
        }
    }

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPlaylistId) {
        selectionViewModel.exitSelectionMode()
    }

    var trackToEdit by remember { mutableStateOf<LocalTrack?>(null) }
    var metadataToEdit by remember { mutableStateOf<TrackMetadata?>(null) }
    val metadataEditor = remember { com.crowstar.deeztrackermobile.features.localmusic.MetadataEditor(context) }
    val scope = rememberCoroutineScope()

    if (trackToEdit != null && metadataToEdit == null) {
        LaunchedEffect(trackToEdit) {
            withContext(Dispatchers.IO) {
                metadataToEdit = metadataEditor.readMetadata(trackToEdit!!.filePath)
            }
        }
    }

    if (trackToEdit != null && metadataToEdit != null) {
        EditTrackDialog(
            initialMetadata = metadataToEdit!!,
            onDismiss = { 
                trackToEdit = null
                metadataToEdit = null
            },
            onSave = { newMetadata ->
                val trackPath = trackToEdit!!.filePath
                trackToEdit = null
                metadataToEdit = null
                
                scope.launch(Dispatchers.IO) {
                    val success = metadataEditor.writeMetadata(
                        trackPath, 
                        newMetadata,
                        onScanComplete = { viewModel.loadMusic() }
                    )
                    if (success) {
                        snackbarController.showSnackbar(context.getString(R.string.edit_success))
                    } else {
                        snackbarController.showSnackbar(context.getString(R.string.edit_error_saving))
                    }
                }
            }
        )
    }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                viewModel.loadMusic()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            viewModel.loadMusic()
        }
    }

    val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }

    // DYNAMIC SELECT ALL LOGIC
    val downloadTrigger by viewModel.downloadManager.downloadRefreshTrigger.collectAsState()
    val playlistTracksWithState = remember(selectedPlaylist, unfilteredTracks, downloadTrigger) {
        selectedPlaylist?.let { viewModel.getPlaylistTracksUiState(it, unfilteredTracks) } ?: emptyList()
    }

    LaunchedEffect(selectedPlaylist, tracks, playlistTracksWithState, selectedView) {
        if (selectedPlaylist != null) {
            onTrackCountUpdate(playlistTracksWithState.size)
            onSelectAllUpdate {
                selectionViewModel.selectAll(playlistTracksWithState.map { SelectedTrack.Local(it.track, it.originalId) })
            }
        } else if (selectedView == 0) { // Songs Tab
            onTrackCountUpdate(tracks.size)
            onSelectAllUpdate {
                selectionViewModel.selectAll(tracks.map { SelectedTrack.Local(it) })
            }
        } else {
            onTrackCountUpdate(0)
            onSelectAllUpdate { }
        }
    }

    if (selectedPlaylist != null) {
        BackHandler(enabled = selectedPlaylistId != null) {
            selectedPlaylistId = null
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundDark
        ) {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                LocalPlaylistDetailScreen(
                    playlist = selectedPlaylist,
                    playlistTracks = playlistTracksWithState,
                    onBackClick = { selectedPlaylistId = null },
                    onTrackClick = { track ->
                         val rawTracks = playlistTracksWithState.map { it.track }
                         onTrackClick(track, rawTracks, selectedPlaylist.name)
                    },
                    onPlayPlaylist = {
                         val rawTracks = playlistTracksWithState.map { it.track }
                         if (rawTracks.isNotEmpty()) {
                             onTrackClick(rawTracks.first(), rawTracks, selectedPlaylist.name)
                         }
                    },
                    onShufflePlaylist = {
                        val rawTracks = playlistTracksWithState.map { it.track }
                        if (rawTracks.isNotEmpty()) {
                            val shuffled = rawTracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled, selectedPlaylist.name)
                        }
                    },
                    onRemoveTrack = { uiState -> viewModel.removeTrackFromPlaylist(selectedPlaylist, uiState) },
                    onShareTrack = { track -> shareTrack(track) },
                    onAddToQueue = { track -> onAddToQueue?.invoke(track) },
                    onEditTrack = { track -> trackToEdit = track },
                    selectionViewModel = selectionViewModel,
                    contentPadding = contentPadding
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.local_music_title), 
                        color = Color.White, 
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (selectedView == 3) {
                         IconButton(onClick = onImportPlaylist) {
                             Icon(Icons.Default.Input, contentDescription = "Import Playlist", tint = Color.White)
                         }
                    }
                    if (selectedView == 0 && tracks.isNotEmpty()) {
                        IconButton(onClick = {
                            val shuffled = tracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled, localMusicTitle)
                        }) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.player_shuffle), tint = Color.White)
                        }
                    }
                    IconButton(onClick = { viewModel.loadMusic() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = Color.White)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.searchTracks(it) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.local_search_hint), color = TextGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchTracks("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = TextGray)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Primary
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(
                    selectedTabIndex = selectedView,
                    containerColor = BackgroundDark,
                    contentColor = Primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedView]),
                            color = Primary
                        )
                    },
                    divider = { }
                ) {
                    val tabs = listOf(
                        stringResource(R.string.view_songs),
                        stringResource(R.string.view_albums),
                        stringResource(R.string.view_artists),
                        stringResource(R.string.view_playlists)
                    )
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedView == index,
                            onClick = { viewModel.setSelectedView(index) },
                            text = { 
                                Text(
                                    text = title, 
                                    color = if (selectedView == index) Primary else TextGray,
                                    fontWeight = FontWeight.Bold
                                ) 
                            }
                        )
                    }
                }
            }

            if (!hasPermission) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.permission_required), color = TextGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }) {
                            Text(stringResource(R.string.permission_grant))
                        }
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedView) {
                        0 -> {
                            val selectedTracksFlow by selectionViewModel.selectedTracks.collectAsState()
                            val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
                            
                            LocalTracksList(
                                tracks = tracks,
                                state = tracksListState,
                                selectedTracks = selectedTracksFlow.map { it.id }.toSet(),
                                isSelectionMode = isSelectionMode,
                                onTrackClick = { track, list -> 
                                    if (isSelectionMode) {
                                        selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                                    } else {
                                        onTrackClick(track, list, localMusicTitle)
                                    }
                                },
                                onTrackLongClick = { track ->
                                    selectionViewModel.enterSelectionMode(SelectionContext.LOCAL, SelectedTrack.Local(track))
                                },
                                onShare = { track -> shareTrack(track) },
                                onDelete = { track -> viewModel.requestDeleteTrack(track) },
                                onEdit = { track -> trackToEdit = track },
                                onAddToQueue = { track -> onAddToQueue?.invoke(track) },
                                totalStorage = totalStorage,
                                contentPadding = contentPadding
                            )
                        }
                        1 -> LocalAlbumsGrid(albums, albumsGridState, onAlbumClick, contentPadding)
                        2 -> LocalArtistsGrid(artists, artistsGridState, onArtistClick, contentPadding)
                        3 -> LocalPlaylistsScreen(
                            playlists = playlists,
                            state = playlistsListState,
                            onPlaylistClick = { playlist -> 
                                selectedPlaylistId = playlist.id
                            },
                            onDeletePlaylist = { playlist ->
                                viewModel.deletePlaylist(playlist)
                            },
                            onEditPlaylist = { playlist, newName ->
                                viewModel.editPlaylist(playlist, newName)
                            },
                            onCreatePlaylist = {
                                showCreatePlaylistDialog = true
                            },
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }

        if (showCreatePlaylistDialog) {
            com.crowstar.deeztrackermobile.ui.playlist.CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { newPlaylistName ->
                    viewModel.createPlaylist(newPlaylistName)
                    snackbarController.showSnackbar(
                        context.getString(R.string.toast_playlist_created, newPlaylistName)
                    )
                    showCreatePlaylistDialog = false
                }
            )
        }
    }
}


@Composable
fun LocalTracksList(
    tracks: List<LocalTrack>,
    state: LazyListState = rememberLazyListState(),
    selectedTracks: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onTrackLongClick: (LocalTrack) -> Unit = {},
    onShare: (LocalTrack) -> Unit,
    onDelete: (LocalTrack) -> Unit,
    onEdit: (LocalTrack) -> Unit,
    onAddToQueue: ((LocalTrack) -> Unit)? = null,
    totalStorage: Long,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    
    val (letterIndexMap, currentLetter) = remember(tracks) {
        val grouped = mutableMapOf<Char, Int>()
        tracks.forEachIndexed { index, track ->
            val firstChar = track.title.firstOrNull()?.uppercaseChar()?.let {
                if (it.isLetter()) it else '#'
            } ?: '#'
            if (!grouped.containsKey(firstChar)) {
                grouped[firstChar] = index
            }
        }
        grouped to mutableStateOf<Char?>('A')
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_tracks_format, tracks.size),
                    color = TextGray,
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                val totalSize = tracks.sumOf { it.size }
                val progress = if (totalStorage > 0) totalSize.toFloat() / totalStorage.toFloat() else 0f
                
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceDark)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .background(Primary)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                val sizeGb = totalSize / (1024.0 * 1024.0 * 1024.0)
                Text(
                    text = stringResource(R.string.stats_storage_format, sizeGb),
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = state,
                contentPadding = PaddingValues(bottom = 16.dp + contentPadding, end = 36.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    LocalTrackItem(
                        track = track,
                        isSelected = selectedTracks.contains(track.id),
                        inSelectionMode = isSelectionMode,
                        onShare = { onShare(track) },
                        onDelete = { onDelete(track) },
                        onEdit = { onEdit(track) },
                        onAddToQueue = { onAddToQueue?.invoke(track) },
                        onClick = { onTrackClick(track, tracks) },
                        onLongClick = { onTrackLongClick(track) }
                    )
                }
            }
        }
        
        LaunchedEffect(state.firstVisibleItemIndex) {
            val firstVisibleTrack = tracks.getOrNull(state.firstVisibleItemIndex)
            if (firstVisibleTrack != null) {
                val letter = firstVisibleTrack.title.firstOrNull()?.uppercaseChar()?.let {
                    if (it.isLetter()) it else '#'
                } ?: '#'
                currentLetter.value = letter
            }
        }
        
        AlphabeticalFastScroller(
            modifier = Modifier.align(Alignment.CenterEnd),
            bottomInset = contentPadding,
            selectedLetter = currentLetter.value,
            onLetterSelected = { letter ->
                scope.launch {
                    val index = letterIndexMap[letter]
                    if (index != null) {
                        state.scrollToItem(index)
                        currentLetter.value = letter
                    } else {
                        val availableLetters = letterIndexMap.keys.sorted()
                        val nextLetter = availableLetters.firstOrNull { it >= letter }
                        if (nextLetter != null) {
                            letterIndexMap[nextLetter]?.let {
                                state.scrollToItem(it)
                                currentLetter.value = nextLetter
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun LocalAlbumsGrid(
    albums: List<LocalAlbum>, 
    state: LazyGridState, 
    onAlbumClick: (LocalAlbum) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumGridItem(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
fun AlbumGridItem(album: LocalAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                model = album.albumArtUri,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = album.title ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        MarqueeText(
            text = album.artist ?: "",
            color = TextGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LocalArtistsGrid(
    artists: List<LocalArtist>, 
    state: LazyGridState, 
    onArtistClick: (LocalArtist) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            ArtistGridItem(artist = artist, onClick = { artist.let { onArtistClick(it) } })
        }
    }
}

@Composable
fun ArtistGridItem(artist: LocalArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SurfaceDark),
                contentAlignment = Alignment.Center
        ) {
            com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                model = artist.artistArtUri,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = artist.name ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.artist_songs_count_format, artist.numberOfTracks),
            color = TextGray,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
