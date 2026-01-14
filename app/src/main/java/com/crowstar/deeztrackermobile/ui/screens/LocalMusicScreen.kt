package com.crowstar.deeztrackermobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
    viewModel: LocalMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalMusicViewModelFactory(LocalContext.current)
    )
) {
    val tracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    
    val context = LocalContext.current
    
    // Search Query State
    var searchQuery by remember { mutableStateOf("") }
    
    // Context Menu Actions
    fun shareTrack(track: LocalTrack) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(track.filePath))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Track"))
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
        BackHandler {
            selectedPlaylistId = null
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundDark
        ) {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                LocalPlaylistDetailScreen(
                    playlist = selectedPlaylist,
                    allTracks = tracks,
                    onBackClick = { selectedPlaylistId = null },
                    onTrackClick = { track ->
                        // Pass only playlist tracks if we want the player to play only from playlist
                        // For now, keeping original behavior of passing all tracks, 
                        // but ideally we should filter 'tracks' to match the playlist content if that's the desired player behavior.
                        // Assuming specific requirement is to fix UI, preserving 'tracks' context.
                         onTrackClick(track, tracks)
                    },
                    onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(selectedPlaylist, track) }
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
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        /* Back button removed */
                        Text(
                            text = "Local Music",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.loadMusic() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceDark
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Scan",
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan",
                            color = Primary,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDark
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = TextGray
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                viewModel.searchTracks(it) 
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search local files...",
                                        color = TextGray,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                
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
                    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedView == index,
                            onClick = { viewModel.setSelectedView(index) },
                            text = { 
                                Text(
                                    text = title, 
                                    color = if (selectedView == index) Primary else TextGray,
                                    fontWeight = if (selectedView == index) FontWeight.Bold else FontWeight.Medium
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
             var newPlaylistName by remember { mutableStateOf("") }
             AlertDialog(
                 onDismissRequest = { showCreatePlaylistDialog = false },
                 title = { Text("New Playlist", color = Color.White) },
                 text = {
                     OutlinedTextField(
                         value = newPlaylistName,
                         onValueChange = { newPlaylistName = it },
                         label = { Text("Playlist Name") },
                         singleLine = true,
                         colors = TextFieldDefaults.outlinedTextFieldColors(
                             focusedTextColor = Color.White,
                             unfocusedTextColor = Color.White,
                             focusedBorderColor = Primary,
                             unfocusedBorderColor = TextGray,
                             cursorColor = Primary
                         )
                     )
                 },
                 confirmButton = {
                     Button(
                         onClick = {
                             if (newPlaylistName.isNotBlank()) {
                                 viewModel.createPlaylist(newPlaylistName)
                                 // Optionally wait and add the track immediately?
                                 // For now just create and user can select it since the sheet is still open (or re-opens)
                                 // Improving UX: close dialog, keep sheet open, updated list will show new playlist
                                 showCreatePlaylistDialog = false
                             }
                         },
                         colors = ButtonDefaults.buttonColors(containerColor = Primary)
                     ) {
                         Text("Create")
                     }
                 },
                 dismissButton = {
                     TextButton(onClick = { showCreatePlaylistDialog = false }) {
                         Text("Cancel", color = TextGray)
                     }
                 },
                 containerColor = BackgroundDark
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
                    Text("Permission required to access local music", color = TextGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }) {
                        Text("Grant Permission")
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
            Box(modifier = Modifier.padding(padding)) {
                when (selectedView) {
                    0 -> LocalTracksList(
                        tracks = tracks, 
                        onTrackClick = onTrackClick,
                        onShare = { track -> shareTrack(track) },
                        onDelete = { track -> viewModel.requestDeleteTrack(track) },
                        onAddToPlaylist = { track -> trackForPlaylist = track }
                    )
                    1 -> LocalAlbumsGrid(albums, onAlbumClick)
                    2 -> LocalArtistsGrid(artists, onArtistClick)
                    3 -> LocalPlaylistsScreen(
                        playlists = playlists,
                        onPlaylistClick = { playlist -> 
                            selectedPlaylistId = playlist.id
                        },
                        onDeletePlaylist = { playlist ->
                            viewModel.deletePlaylist(playlist)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LocalTracksList(
    tracks: List<LocalTrack>,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onShare: (LocalTrack) -> Unit,
    onDelete: (LocalTrack) -> Unit,
    onAddToPlaylist: (LocalTrack) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Stats Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${tracks.size} Tracks",
                color = TextGray,
                fontSize = 12.sp
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Simple visual bar represenation
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceDark)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f) // Static 40% for demo
                            .fillMaxHeight()
                            .background(Primary)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val totalSize = tracks.sumOf { it.size }
                val sizeGb = totalSize / (1024.0 * 1024.0 * 1024.0)
                Text(
                    text = "%.1f GB Used".format(sizeGb),
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(tracks) { track ->
                LocalTrackItem(
                    track = track,
                    onShare = { onShare(track) },
                    onDelete = { onDelete(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onClick = { onTrackClick(track, tracks) }
                )
            }
        }
    }
}

@Composable
fun LocalAlbumsGrid(albums: List<LocalAlbum>, onAlbumClick: (LocalAlbum) -> Unit) {
    // Placeholder for Albums Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
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
        Text(
            text = album.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = album.artist,
            color = TextGray,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LocalArtistsGrid(artists: List<LocalArtist>, onArtistClick: (LocalArtist) -> Unit) {
    // Placeholder for Artists Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
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
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${artist.numberOfTracks} songs",
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
    onAddToPlaylist: () -> Unit,
    onClick: () -> Unit,
    deleteLabel: String = "Delete"
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
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    color = TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
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
                    contentDescription = "Options",
                    tint = TextGray
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Share", color = Color.White) },
                    onClick = { 
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Details", color = Color.White) },
                    onClick = { 
                        showMenu = false
                        showDetails = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = Color.Red) },
                    onClick = { 
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(text = "Track Details", color = Color.White) },
            text = {
                Column {
                    DetailRow("Path", track.filePath)
                    DetailRow("Size", track.getFormattedSize())
                    DetailRow("Format", track.mimeType)
                    DetailRow("Bitrate", "Unknown") // Need extra metadata extraction for this
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close", color = Primary)
                }
            },
            containerColor = BackgroundDark,
            titleContentColor = Color.White,
            textContentColor = TextGray
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 14.sp)
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
