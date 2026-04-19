package com.crowstar.deeztrackermobile.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.player.MiniPlayer
import com.crowstar.deeztrackermobile.ui.player.MusicPlayerScreen
import com.crowstar.deeztrackermobile.ui.search.SearchScreen
import com.crowstar.deeztrackermobile.ui.library.LocalMusicScreen
import com.crowstar.deeztrackermobile.ui.downloads.DownloadsScreen
import com.crowstar.deeztrackermobile.ui.settings.SettingsScreen
import com.crowstar.deeztrackermobile.ui.album.LocalAlbumDetailScreen
import com.crowstar.deeztrackermobile.ui.artist.LocalArtistDetailScreen
import com.crowstar.deeztrackermobile.ui.album.AlbumScreen
import com.crowstar.deeztrackermobile.ui.artist.ArtistScreen
import com.crowstar.deeztrackermobile.ui.playlist.PlaylistScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionActionBatchBar
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import com.crowstar.deeztrackermobile.ui.utils.SnackbarController
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.IntentSender
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MainViewModel @Inject constructor(
    val playerController: PlayerController,
    val downloadManager: DownloadManager,
    val musicRepository: LocalMusicRepository,
    val playlistRepository: LocalPlaylistRepository
) : ViewModel() {
    
    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender.asStateFlow()

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }

    fun requestDeleteTracks(ids: List<Long>) {
        viewModelScope.launch {
            val sender = musicRepository.requestDeleteTracks(ids)
            if (sender != null) {
                _deleteIntentSender.value = sender
            }
        }
    }
}

@Composable
fun MainScreen(
    importAction: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    selectionViewModel: SelectionViewModel
) {
    val navController = rememberNavController()
    val playerController = viewModel.playerController
    val playerState by playerController.playerState.collectAsState()
    
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    val selectedTracks by selectionViewModel.selectedTracks.collectAsState()
    val selectionContext by selectionViewModel.selectionContext.collectAsState()
    val currentPlaylistId by selectionViewModel.currentPlaylistId.collectAsState()

    val context = LocalContext.current
    val downloadManager = viewModel.downloadManager
    val downloadState by downloadManager.downloadState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarController = remember { SnackbarController(snackbarHostState, scope) }
    
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylistFromSelection by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.playlistRepository.playlists.collectAsState()
    
    var isNavigating by remember { mutableStateOf(false) }
    var currentSelectAllAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var currentViewTrackCount by remember { mutableIntStateOf(0) }
    val isAllSelected = selectedTracks.isNotEmpty() && selectedTracks.size >= currentViewTrackCount && currentViewTrackCount > 0

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selectionViewModel.exitSelectionMode()
            viewModel.musicRepository.notifyLibraryChanged()
        }
        viewModel.resetDeleteIntentSender()
    }

    val deleteIntentSender by viewModel.deleteIntentSender.collectAsState()
    LaunchedEffect(deleteIntentSender) {
        deleteIntentSender?.let { sender ->
            val request = androidx.activity.result.IntentSenderRequest.Builder(sender).build()
            deleteLauncher.launch(request)
        }
    }

    fun shareTracks(tracks: List<SelectedTrack.Local>) {
        val uris = tracks.map { android.net.Uri.parse(it.track.filePath) }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.selection_share_chooser)))
    }

    // Handle download state changes
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                val message = if (state.failedCount > 0) {
                    context.getString(R.string.notification_download_summary, state.successCount, state.failedCount)
                } else {
                    context.getString(R.string.notification_download_completed, state.title)
                }
                snackbarController.showSnackbar(message)
                downloadManager.resetState()
            }
            is DownloadState.Error -> {
                snackbarController.showSnackbar(
                    context.getString(R.string.notification_download_failed, state.message)
                )
                downloadManager.resetState()
            }
            else -> {}
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
     CompositionLocalProvider(LocalSnackbarController provides snackbarController) {
        
        val safePopBackStack: () -> Unit = remember(navController) {
            {
                if (!isNavigating && navController.previousBackStackEntry != null) {
                    isNavigating = true
                    navController.popBackStack()
                }
            }
        }
        
        LaunchedEffect(currentRoute) {
            isNavigating = false
            selectionViewModel.exitSelectionMode()
            currentSelectAllAction = null
            currentViewTrackCount = 0
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            
            val isMiniPlayerVisible = playerState.currentTrack != null
            val isBottomBarVisible = currentRoute in listOf("library", "search", "downloads", "settings")
            
            var floatingUIHeight = 0.dp
            
            if (isSelectionMode) {
                floatingUIHeight = 80.dp // Selection bar height
            } else if (isBottomBarVisible) {
                floatingUIHeight = 64.dp // Standard nav bar height
            } 
            
            if (isMiniPlayerVisible) {
                floatingUIHeight += 70.dp
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                MainNavigation(
                    navController, 
                    importAction = importAction,
                    onLogout = onLogout,
                    playerController = playerController,
                    selectionViewModel = selectionViewModel,
                    safePopBackStack = safePopBackStack,
                    contentPadding = floatingUIHeight,
                    onSelectAllUpdate = { action -> currentSelectAllAction = action },
                    onTrackCountUpdate = { count -> currentViewTrackCount = count }
                )
            }

            // Bottom UI (MiniPlayer + Nav)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (isMiniPlayerVisible && currentRoute != "player") {
                    MiniPlayer(
                        onClick = { navController.navigate("player") }
                    )
                }

                if (isSelectionMode) {
                    val count = selectedTracks.size
                    val tracksCopy = selectedTracks.toList()
                    val contextType = selectionContext
                    val pid = currentPlaylistId
                    
                    SelectionActionBatchBar(
                        selectedCount = count,
                        context = contextType,
                        isAllSelected = isAllSelected,
                        showSelectAll = currentSelectAllAction != null,
                        onClose = { selectionViewModel.exitSelectionMode() },
                        onToggleSelectAll = { 
                            if (isAllSelected) selectionViewModel.exitSelectionMode()
                            else currentSelectAllAction?.invoke() 
                        },
                        onDelete = {
                            val ids = tracksCopy.filterIsInstance<SelectedTrack.Local>().map { it.id }
                            if (ids.isNotEmpty()) {
                                viewModel.requestDeleteTracks(ids)
                            }
                        },
                        onRemove = {
                            val countToRemove = tracksCopy.size
                            if (pid != null && tracksCopy.isNotEmpty()) {
                                scope.launch {
                                    val ids = tracksCopy.map { it.id }
                                    viewModel.playlistRepository.removeTracksFromPlaylist(pid, ids)
                                    snackbarController.showSnackbar(context.getString(R.string.selection_removed_from_playlist, countToRemove))
                                }
                                selectionViewModel.exitSelectionMode()
                            }
                        },
                        onAddToQueue = {
                            val countToAdd = tracksCopy.size
                            tracksCopy.forEach { st ->
                                when (st) {
                                    is SelectedTrack.Local -> playerController.addToQueue(st.track)
                                    is SelectedTrack.Remote -> playerController.addToQueue(
                                        track = st.track,
                                        source = st.source,
                                        backupAlbumArt = st.backupAlbumArt
                                    )
                                }
                            }
                            selectionViewModel.exitSelectionMode()
                            scope.launch {
                                snackbarController.showSnackbar(context.getString(R.string.selection_added_to_queue, countToAdd))
                            }
                        },
                        onAddToPlaylist = {
                            showAddToPlaylist = true
                        },
                        onShare = {
                            val localTracks = tracksCopy.filterIsInstance<SelectedTrack.Local>()
                            if (localTracks.isNotEmpty()) {
                                shareTracks(localTracks)
                            }
                        },
                        onDownload = {
                            val remoteTracks = tracksCopy.filterIsInstance<SelectedTrack.Remote>()
                            val downloadCount = remoteTracks.size
                            remoteTracks.forEach { st ->
                                downloadManager.startTrackDownload(st.track.id, st.track.title ?: "Unknown Track")
                            }
                            selectionViewModel.exitSelectionMode()
                            scope.launch {
                                snackbarController.showSnackbar(context.getString(R.string.selection_started_download, downloadCount))
                            }
                        }
                    )
                } else if (isBottomBarVisible) {
                    BottomNavigationBar(navController)
                }
            }

            // Standard Snackbar Host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = floatingUIHeight + 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        if (showAddToPlaylist) {
            com.crowstar.deeztrackermobile.ui.playlist.AddToPlaylistBottomSheet(
                playlists = localPlaylists,
                onDismiss = { showAddToPlaylist = false },
                onPlaylistClick = { playlist ->
                    scope.launch {
                        val tracksToSave = selectedTracks.map { st ->
                            when (st) {
                                is SelectedTrack.Local -> st.track.toPlaylistTrack()
                                is SelectedTrack.Remote -> st.track.toPlaylistTrack()
                            }
                        }
                        viewModel.playlistRepository.addTracksToPlaylist(playlist.id, tracksToSave)
                        selectionViewModel.exitSelectionMode()
                        snackbarController.showSnackbar(
                            context.getString(R.string.toast_added_to_playlist, playlist.name)
                        )
                    }
                    showAddToPlaylist = false
                },
                onCreateNewPlaylist = { 
                    showAddToPlaylist = false
                    showCreatePlaylistFromSelection = true 
                }
            )
        }

        if (showCreatePlaylistFromSelection) {
            com.crowstar.deeztrackermobile.ui.playlist.CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistFromSelection = false },
                onCreate = { newName ->
                    scope.launch {
                        val newId = viewModel.playlistRepository.createPlaylist(newName)
                        val tracksToSave = selectedTracks.map { st ->
                            when (st) {
                                is SelectedTrack.Local -> st.track.toPlaylistTrack()
                                is SelectedTrack.Remote -> st.track.toPlaylistTrack()
                            }
                        }
                        viewModel.playlistRepository.addTracksToPlaylist(newId, tracksToSave)
                        selectionViewModel.exitSelectionMode()
                        snackbarController.showSnackbar(
                            context.getString(R.string.toast_playlist_created, newName)
                        )
                    }
                    showCreatePlaylistFromSelection = false
                }
            )
        }
    }
}

@Composable
fun MainNavigation(
    navController: NavHostController,
    importAction: () -> Unit,
    onLogout: () -> Unit,
    playerController: PlayerController,
    selectionViewModel: SelectionViewModel,
    safePopBackStack: () -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
    onSelectAllUpdate: (() -> Unit) -> Unit,
    onTrackCountUpdate: (Int) -> Unit
) {
    NavHost(navController = navController, startDestination = "library") {
        composable("library") { 
            val viewModel = hiltViewModel<com.crowstar.deeztrackermobile.ui.library.LocalMusicViewModel>()
            
            LocalMusicScreen(
                onTrackClick = { track, list, context -> 
                    if (selectionViewModel.isSelectionMode.value) {
                        selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                    } else {
                        playerController.playTrack(track, list, context)
                    }
                },
                onAlbumClick = { album -> navController.navigate("local_album/${album.id}") },
                onArtistClick = { artist -> navController.navigate("local_artist/${artist.name}") },
                onImportPlaylist = importAction,
                onAddToQueue = { track -> playerController.addToQueue(track) },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                onSelectAllUpdate = onSelectAllUpdate,
                onTrackCountUpdate = onTrackCountUpdate,
                viewModel = viewModel
            )
        }
        composable("search") { 
            SearchScreen(
                onArtistClick = { id -> navController.navigate("artist/$id") },
                onPlaylistClick = { id -> navController.navigate("playlist/$id") },
                onAlbumClick = { id -> navController.navigate("album/$id") },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding
            )
        }
        composable("downloads") { 
            val viewModel = hiltViewModel<com.crowstar.deeztrackermobile.ui.downloads.DownloadsViewModel>()
            val downloadedTracks by viewModel.tracks.collectAsState()
            
            LaunchedEffect(downloadedTracks) {
                onTrackCountUpdate(downloadedTracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(downloadedTracks.map { SelectedTrack.Local(it) })
                }
            }

            DownloadsScreen(
                onTrackClick = { track, list -> 
                    if (selectionViewModel.isSelectionMode.value) {
                        selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                    } else {
                        playerController.playTrack(track, list, "Downloads")
                    }
                },
                onAddToQueue = { track -> playerController.addToQueue(track) },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = viewModel
            )
        }
        composable("settings") { 
            SettingsScreen(
                onLogout = onLogout,
                contentPadding = contentPadding
            )
        }
        composable("player") {
            MusicPlayerScreen(onCollapse = safePopBackStack)
        }
        composable(
            route = "local_album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            val viewModel = hiltViewModel<com.crowstar.deeztrackermobile.ui.library.LocalMusicViewModel>()
            val unfilteredTracks by viewModel.unfilteredTracks.collectAsState()
            
            val tracks = remember(unfilteredTracks, albumId) {
                unfilteredTracks.filter { it.albumId == albumId }
            }
            
            LaunchedEffect(tracks) {
                onTrackCountUpdate(tracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(tracks.map { SelectedTrack.Local(it) })
                }
            }

            LocalAlbumDetailScreen(
                albumId = albumId,
                onBackClick = safePopBackStack,
                onTrackClick = { track, list -> 
                    if (selectionViewModel.isSelectionMode.value) {
                        selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                    } else {
                        playerController.playTrack(track, list, "Album")
                    }
                },
                onPlayAlbum = { list -> playerController.playTrack(list.first(), list, "Album") },
                onAddToQueue = { track -> playerController.addToQueue(track) },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = viewModel
            )
        }
        composable(
            route = "local_artist/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            val viewModel = hiltViewModel<com.crowstar.deeztrackermobile.ui.library.LocalMusicViewModel>()
            val unfilteredTracks by viewModel.unfilteredTracks.collectAsState()
            
            val tracks = remember(unfilteredTracks, artistName) {
                unfilteredTracks.filter { it.artist == artistName }
            }
            
            LaunchedEffect(tracks) {
                onTrackCountUpdate(tracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(tracks.map { SelectedTrack.Local(it) })
                }
            }

            LocalArtistDetailScreen(
                artistName = artistName,
                onBackClick = safePopBackStack,
                onTrackClick = { track, list -> 
                    if (selectionViewModel.isSelectionMode.value) {
                        selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                    } else {
                        playerController.playTrack(track, list, "Artist")
                    }
                },
                onPlayArtist = { list -> playerController.playTrack(list.first(), list, "Artist") },
                onAddToQueue = { track -> playerController.addToQueue(track) },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = viewModel
            )
        }
        
        // Remote Views
        composable(
            route = "artist/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            val artistViewModel: com.crowstar.deeztrackermobile.ui.artist.ArtistViewModel = hiltViewModel()
            val topTracks by artistViewModel.topTracks.collectAsState()
            val artist by artistViewModel.artist.collectAsState()
            
            LaunchedEffect(topTracks, artist) {
                onTrackCountUpdate(topTracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(topTracks.map { track ->
                        SelectedTrack.Remote(
                            track = track,
                            source = artist?.name,
                            backupAlbumArt = track.album?.coverBig ?: track.album?.coverMedium
                        )
                    })
                }
            }

            ArtistScreen(
                artistId = artistId,
                onBackClick = safePopBackStack,
                onAlbumClick = { albumId ->
                    navController.navigate("album/${albumId}")
                },
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = artistViewModel
            )
        }
        
        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            val albumViewModel: com.crowstar.deeztrackermobile.ui.album.AlbumViewModel = hiltViewModel()
            val tracks by albumViewModel.tracks.collectAsState()
            val album by albumViewModel.album.collectAsState()
            
            LaunchedEffect(tracks, album) {
                onTrackCountUpdate(tracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(tracks.map { track ->
                        SelectedTrack.Remote(
                            track = track,
                            source = album?.title,
                            backupAlbumArt = album?.coverBig ?: album?.coverMedium
                        )
                    })
                }
            }

            AlbumScreen(
                albumId = albumId,
                onBackClick = safePopBackStack,
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = albumViewModel
            )
        }
        
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            val playlistViewModel: com.crowstar.deeztrackermobile.ui.playlist.PlaylistViewModel = hiltViewModel()
            val tracks by playlistViewModel.tracks.collectAsState()
            val playlist by playlistViewModel.playlist.collectAsState()
            
            LaunchedEffect(tracks, playlist) {
                onTrackCountUpdate(tracks.size)
                onSelectAllUpdate {
                    selectionViewModel.selectAll(tracks.map { track ->
                        SelectedTrack.Remote(
                            track = track,
                            source = playlist?.title,
                            backupAlbumArt = playlist?.pictureBig ?: playlist?.pictureMedium
                        )
                    })
                }
            }

            PlaylistScreen(
                playlistId = playlistId,
                onBackClick = safePopBackStack,
                selectionViewModel = selectionViewModel,
                contentPadding = contentPadding,
                viewModel = playlistViewModel
            )
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        NavigationItem("library", stringResource(R.string.bottom_nav_library), Icons.Default.LibraryMusic),
        NavigationItem("search", stringResource(R.string.bottom_nav_search), Icons.Default.Search),
        NavigationItem("downloads", stringResource(R.string.bottom_nav_downloads), Icons.Default.Download),
        NavigationItem("settings", stringResource(R.string.bottom_nav_settings), Icons.Default.Settings)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp),
        color = SurfaceDark.copy(alpha = 0.95f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .weight(1f)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (selected) Primary else TextGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        color = if (selected) Primary else TextGray,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

data class NavigationItem(val route: String, val title: String, val icon: ImageVector)
