package com.crowstar.deeztrackermobile.ui.screens

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
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.crowstar.deeztrackermobile.ui.components.AlphabeticalFastScroller
import com.crowstar.deeztrackermobile.ui.components.MarqueeText
import com.crowstar.deeztrackermobile.features.localmusic.MetadataEditor
import com.crowstar.deeztrackermobile.features.localmusic.TrackMetadata
import com.crowstar.deeztrackermobile.ui.components.EditTrackDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    onTrackClick: (LocalTrack, List<LocalTrack>, String?) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
    onImportPlaylist: () -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: LocalMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalMusicViewModel.LocalMusicViewModelFactory(LocalContext.current)
    )
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
    val localMusicTitle = stringResource(R.string.local_music_title)
    
    // Search Query State - Persist across navigation
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Re-apply filter when loading completes or query changes (handles screen restoration)
    LaunchedEffect(isLoading, searchQuery) {
        if (!isLoading) {
             viewModel.searchTracks(searchQuery)
        }
    }
    
    // Scroll State - Persist across navigation
    val tracksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val albumsGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val artistsGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val playlistsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    
    // Context Menu Actions
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

    var trackForPlaylist by remember { mutableStateOf<LocalTrack?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // Edit Metadata State
    var trackToEdit by remember { mutableStateOf<LocalTrack?>(null) }
    var metadataToEdit by remember { mutableStateOf<TrackMetadata?>(null) }
    val metadataEditor = remember { MetadataEditor(context) }
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
                // Close dialog immediately
                trackToEdit = null
                metadataToEdit = null
                
                // Save and refresh in background
                scope.launch(Dispatchers.IO) {
                    val success = metadataEditor.writeMetadata(
                        trackPath, 
                        newMetadata,
                        onScanComplete = { viewModel.loadMusic() }
                    )
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, R.string.edit_success, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.edit_error_saving, Toast.LENGTH_SHORT).show()
                        }
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

    val selectedPlaylist = remember(selectedPlaylistId, playlists) {
        playlists.find { it.id == selectedPlaylistId }
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
                    allTracks = unfilteredTracks,
                    onBackClick = { selectedPlaylistId = null },
                    onTrackClick = { track ->
                         // Filter tracks to match playlist content context
                         val playlistTracks = selectedPlaylist.trackIds.mapNotNull { id -> unfilteredTracks.find { it.id == id } }
                         onTrackClick(track, playlistTracks, selectedPlaylist.name)
                    },
                    onPlayPlaylist = {
                         val playlistTracks = selectedPlaylist.trackIds.mapNotNull { id -> unfilteredTracks.find { it.id == id } }
                         if (playlistTracks.isNotEmpty()) {
                             onTrackClick(playlistTracks.first(), playlistTracks, selectedPlaylist.name)
                         }
                    },
                    onShufflePlaylist = {
                        val playlistTracks = selectedPlaylist.trackIds.mapNotNull { id -> unfilteredTracks.find { it.id == id } }
                        if (playlistTracks.isNotEmpty()) {
                            val shuffled = playlistTracks.shuffled()
                            onTrackClick(shuffled.first(), shuffled, selectedPlaylist.name)
                        }
                    },
                    onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(selectedPlaylist, track) },
                    contentPadding = contentPadding
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
            ) {
                // Header Row
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_icon),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.local_music_title), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (selectedView == 3) { // Playlists Tab
                             IconButton(onClick = onImportPlaylist) {
                                 Icon(Icons.Default.Input, contentDescription = "Import Playlist", tint = Color.White)
                             }
                        }
                        IconButton(onClick = { viewModel.loadMusic() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
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

                // Tabs
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
        },
        containerColor = BackgroundDark,
        bottomBar = {
             // Optional: Player Bar placeholder could go here if needed
        }
    ) { padding ->


        if (trackForPlaylist != null) {
            com.crowstar.deeztrackermobile.ui.components.AddToPlaylistBottomSheet(
                playlists = playlists,
                onDismiss = { trackForPlaylist = null },
                onPlaylistClick = { playlist ->
                    trackForPlaylist?.let { track ->
                       viewModel.addTrackToPlaylist(playlist, track)
                    }
                    trackForPlaylist = null
                },
                onCreateNewPlaylist = { showCreatePlaylistDialog = true }
            )
        }

    if (showCreatePlaylistDialog) {
        com.crowstar.deeztrackermobile.ui.components.CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { newPlaylistName ->
                viewModel.createPlaylist(newPlaylistName)
                showCreatePlaylistDialog = false
            }
        )
    }

        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            // View Switching Logic
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedView) {
                    0 -> LocalTracksList(
                        tracks = tracks,
                        state = tracksListState,
                        onTrackClick = { track, list -> onTrackClick(track, list, localMusicTitle) },
                        onShare = { track -> shareTrack(track) },
                        onDelete = { track -> viewModel.requestDeleteTrack(track) },
                        onEdit = { track -> trackToEdit = track },
                        onAddToPlaylist = { track -> trackForPlaylist = track },
                        totalStorage = totalStorage,
                        contentPadding = contentPadding
                    )
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
                        onCreatePlaylist = {
                            showCreatePlaylistDialog = true
                        },
                        contentPadding = contentPadding
                    )
                }
            }
        }

    }


}


@Composable
fun LocalTracksList(
    tracks: List<LocalTrack>,
    state: LazyListState = rememberLazyListState(),
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onShare: (LocalTrack) -> Unit,
    onDelete: (LocalTrack) -> Unit,
    onEdit: (LocalTrack) -> Unit,
    onAddToPlaylist: (LocalTrack) -> Unit,
    totalStorage: Long,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    
    // Group tracks by first letter and create index map
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
            // Stats Bar
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
                
                // Simple visual bar representation
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
                            .fillMaxWidth(progress.coerceIn(0.01f, 1f)) // Ensure at least a tiny sliver is visible if > 0
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
                contentPadding = PaddingValues(bottom = 16.dp + contentPadding, end = 36.dp) // Space for fast scroller + Dynamic Bottom Padding
            ) {
                items(tracks) { track ->
                    LocalTrackItem(
                        track = track,
                        onShare = { onShare(track) },
                        onDelete = { onDelete(track) },
                        onEdit = { onEdit(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onClick = { onTrackClick(track, tracks) }
                    )
                }
            }
        }
        
        // Sync fast scroller with manual scroll position
        LaunchedEffect(state.firstVisibleItemIndex) {
            val firstVisibleTrack = tracks.getOrNull(state.firstVisibleItemIndex)
            if (firstVisibleTrack != null) {
                val letter = firstVisibleTrack.title.firstOrNull()?.uppercaseChar()?.let {
                    if (it.isLetter()) it else '#'
                } ?: '#'
                currentLetter.value = letter
            }
        }
        
        // Fast Scroller Overlay
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
                        // Find next available letter
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
    // Placeholder for Albums Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums) { album ->
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
            if (album.albumArtUri != null) {
                AsyncImage(
                    model = album.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextGray.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = album.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        MarqueeText(
            text = album.artist,
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
    // Placeholder for Artists Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(artists) { artist ->
            ArtistGridItem(artist = artist, onClick = { onArtistClick(artist) })
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
            // Placeholder for artist image, or use a default icon
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = TextGray.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MarqueeText(
            text = artist.name,
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

@Composable
fun LocalTrackItem(
    track: LocalTrack,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onClick: () -> Unit,
    deleteLabel: String = stringResource(R.string.action_delete)
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                android.util.Log.d("DeezTracker", "LocalTrackItem clicked: ${track.title}")
                onClick() 
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            if (track.albumArtUri != null) {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextGray.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = track.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = track.artist,
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Format Badge
                FormatBadge(track.mimeType)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = track.getFormattedSize().replace(" ", ""), // Compact size
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.player_options),
                    tint = TextGray
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share), color = Color.White) },
                    onClick = { 
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_add_to_playlist), color = Color.White) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_details), color = Color.White) },
                    onClick = { 
                        showMenu = false
                        showDetails = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit), color = Color.White) },
                    onClick = { 
                        showMenu = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = Color.Red) }, // deleteLabel is passed as prop, assuming caller handles localization or default is localized? Wait, caller is line 438: deleteLabel default is "Delete".
                    onClick = { 
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }

    if (showDetails) {
        com.crowstar.deeztrackermobile.ui.components.TrackDetailsDialog(
            track = track,
            onDismiss = { showDetails = false }
        )
    }
}



@Composable
fun FormatBadge(mimeType: String) {
    val (text, color) = when {
        mimeType.contains("flac") -> "FLAC" to Color(0xFF00A2E8) // Blue
        mimeType.contains("wav") -> "WAV" to Color(0xFFFFC107) // Amber
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3" to TextGray
        mimeType.contains("mp4") || mimeType.contains("aac") -> "AAC" to TextGray
        else -> "AUDIO" to TextGray
    }
    
    val backgroundColor = if (text == "FLAC" || text == "WAV") color.copy(alpha = 0.2f) else SurfaceDark
    val textColor = if (text == "FLAC" || text == "WAV") color else TextGray

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
