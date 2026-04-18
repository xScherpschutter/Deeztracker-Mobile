package com.crowstar.deeztrackermobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import android.content.Intent 
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.deezer.Artist
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Playlist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.TrackArtwork
import kotlinx.coroutines.launch
import com.crowstar.deeztrackermobile.ui.utils.formatDuration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DeezerRepository,
    val downloadManager: DownloadManager,
    val playerController: PlayerController,
    val playlistRepository: LocalPlaylistRepository
) : ViewModel() {
    val apiRepository = repository
    
    val downloadedKeys = downloadManager.downloadedKeys
    val playlists = playlistRepository.playlists
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onArtistClick: (Long) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_tracks),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_playlists)
    )

    val repository = viewModel.apiRepository
    val scope = rememberCoroutineScope()
    
    val downloadManager = viewModel.downloadManager
    val downloadState by downloadManager.downloadState.collectAsState()
    val downloadedKeys by viewModel.downloadedKeys.collectAsState()
    val activeDownloads by downloadManager.activeDownloads.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var trackToAddToPlaylist by remember { mutableStateOf<com.crowstar.deeztrackermobile.features.deezer.Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.playlists.collectAsState()

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<com.crowstar.deeztrackermobile.features.deezer.Album>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    var nextUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isAppending by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun performSearch(isNewSearch: Boolean = true) {
        if (query.isBlank()) return
        scope.launch {
            if (isNewSearch) {
                isLoading = true
                tracks = emptyList()
                artists = emptyList()
                albums = emptyList()
                playlists = emptyList()
                nextUrl = null
            } else {
                isAppending = true
            }

            try {
                val currentNext = if (isNewSearch) null else nextUrl
                when (selectedTabIndex) {
                    0 -> {
                        val response = repository.searchTracks(query, currentNext)
                        tracks = if (isNewSearch) response.data else (tracks + response.data).distinctBy { it.id }
                        nextUrl = response.next
                    }
                    1 -> {
                        val response = repository.searchArtists(query, currentNext)
                        artists = if (isNewSearch) response.data else (artists + response.data).distinctBy { it.id }
                        nextUrl = response.next
                    }
                    2 -> {
                        val response = repository.searchAlbums(query, currentNext)
                        albums = if (isNewSearch) response.data else (albums + response.data).distinctBy { it.id }
                        nextUrl = response.next
                    }
                    3 -> {
                        val response = repository.searchPlaylists(query, currentNext)
                        playlists = if (isNewSearch) response.data else (playlists + response.data).distinctBy { it.id }
                        nextUrl = response.next
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                isAppending = false
            }
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (query.isNotEmpty() && hasSearched) {
            performSearch(isNewSearch = true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex > (totalItemsNumber - 5)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .collect {
                if (it && !isLoading && !isAppending && nextUrl != null) {
                    performSearch(isNewSearch = false)
                }
            }
    }

    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.notification_download_completed, state.title),
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            is DownloadState.Error -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.notification_download_failed, state.message),
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.search_title), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Input & Tabs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { 
                                query = it
                                if (it.isEmpty()) {
                                    hasSearched = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search_hint), color = TextGray) },
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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { 
                                hasSearched = true
                                performSearch(true) 
                            })
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(tabs.size) { index ->
                                val isSelected = selectedTabIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isSelected) Primary.copy(alpha = 0.1f) else SurfaceDark.copy(alpha = 0.5f))
                                        .border(1.dp, if (isSelected) Primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
                                        .clickable { selectedTabIndex = index }
                                        .padding(horizontal = 24.dp, vertical = 10.dp)
                                ) {
                                    Text(text = tabs[index], color = if (isSelected) Primary else TextGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else if (query.isEmpty() || !hasSearched) {
                    EmptySearchView()
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + contentPadding, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (selectedTabIndex) {
                            0 -> {
                                items(tracks, key = { it.id }) { track ->
                                    // FAST CHECK: O(1) in-memory check
                                    val isDownloaded = downloadedKeys.contains(
                                        downloadManager.generateTrackKey(
                                            track.title ?: "",
                                            track.artist?.name ?: ""
                                        )
                                    )
                                    
                                    TrackItem(
                                        track = track,
                                        isDownloaded = isDownloaded,
                                        isDownloading = activeDownloads.contains(track.id.toString()),
                                        onDownloadClick = { downloadManager.startTrackDownload(track.id, track.title ?: "Unknown Track") },
                                        onStreamClick = {
                                            viewModel.playerController.playDeezerTrackWithRadio(track)
                                        },
                                        onAddToPlaylist = { trackToAddToPlaylist = track },
                                        onAddToQueue = { viewModel.playerController.addToQueue(track) }
                                    )
                                }
                            }
                            1 -> items(artists, key = { it.id }) { artist -> 
                                ArtistItem(artist, onClick = { 
                                    onArtistClick(artist.id) 
                                }) 
                            }
                            2 -> items(albums, key = { it.id }) { album -> 
                                AlbumItem(album, onClick = { 
                                    onAlbumClick(album.id) 
                                }) 
                            }
                            3 -> items(playlists, key = { it.id }) { playlist -> 
                                PlaylistItem(playlist, onClick = { 
                                    onPlaylistClick(playlist.id) 
                                }) 
                            }
                        }
                        if (isAppending) {
                            item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp)) } }
                        }
                    }
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
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.toast_playlist_created, newPlaylistName),
                        duration = SnackbarDuration.Short
                    )
                }
                showCreatePlaylistDialog = false
            }
        )
    }
}

@Composable
fun TrackItem(
    track: Track,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onStreamClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onAddToQueue: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onStreamClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackArtwork(
                model = track.album?.coverMedium,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Stream",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = track.title ?: "", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(text = track.artist?.name ?: "", color = TextGray, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                if (track.duration != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = formatDuration(track.duration), color = TextGray, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onDownloadClick,
            enabled = !isDownloading && !isDownloaded,
            modifier = Modifier.size(40.dp)
        ) {
            if (isDownloaded) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.desc_downloaded), tint = Color.Green, modifier = Modifier.size(20.dp))
            } else if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = Primary, modifier = Modifier.size(20.dp))
            }
        }

        TrackOptionsMenu(onAddToPlaylist = onAddToPlaylist, onAddToQueue = onAddToQueue)
    }
}

@Composable
fun EmptySearchView() {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.search_hint), color = Color.Gray.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ArtistItem(artist: Artist, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(model = artist.pictureMedium, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = artist.name ?: "", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text = "${artist.nbFan ?: 0} ${stringResource(R.string.label_fans)}", color = TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
fun AlbumItem(album: com.crowstar.deeztrackermobile.features.deezer.Album, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(model = album.coverMedium, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = album.title ?: "", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            MarqueeText(text = album.artist?.name ?: "", color = TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(model = playlist.pictureMedium, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(text = playlist.title ?: "", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text = "${playlist.nbTracks ?: 0} ${stringResource(R.string.label_tracks)}", color = TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
fun NoResultsView(query: String) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.no_results), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.search_no_results_desc, query), color = TextGray, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
