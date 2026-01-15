package com.crowstar.deeztrackermobile.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info 
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.crowstar.deeztrackermobile.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onArtistClick: (Long) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_tracks),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_playlists)
    )

    val repository = remember { DeezerRepository() }
    val scope = rememberCoroutineScope()
    


    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadState by downloadManager.downloadState.collectAsState()

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
                // If it's not a new search and there is no next URL, stop.
                if (!isNewSearch && currentNext == null) return@launch

                when (selectedTabIndex) {
                    0 -> {
                        val response = repository.searchTracks(query, currentNext)
                        tracks = if (isNewSearch) response.data else tracks + response.data
                        nextUrl = response.next
                    }
                    1 -> {
                        val response = repository.searchArtists(query, currentNext)
                        artists = if (isNewSearch) response.data else artists + response.data
                        nextUrl = response.next
                    }
                    2 -> {
                        val response = repository.searchAlbums(query, currentNext)
                        albums = if (isNewSearch) response.data else albums + response.data
                        nextUrl = response.next
                    }
                    3 -> {
                        val response = repository.searchPlaylists(query, currentNext)
                        playlists = if (isNewSearch) response.data else playlists + response.data
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

    // Trigger search when tab changes
    LaunchedEffect(selectedTabIndex) {
        if (query.isNotEmpty() && hasSearched) {
            performSearch(isNewSearch = true)
        }
    }

    // Infinite Scroll Logic
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
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(BackgroundDark, Color.Transparent)
                            )
                        )
                        .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
                ) {
                    Column {
                        // Title Removed


                        // Search Bar
                        OutlinedTextField(
                            value = query,
                            onValueChange = { 
                                query = it
                                if (it.isEmpty()) hasSearched = false
                                hasSearched = false
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search_hint), color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        query = "" 
                                        hasSearched = false
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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { 
                                hasSearched = true
                                performSearch(true) 
                            })
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tabs (Chips)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(tabs.size) { index ->
                                val isSelected = selectedTabIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (isSelected) Primary.copy(alpha = 0.1f) else SurfaceDark.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { selectedTabIndex = index }
                                        .padding(horizontal = 24.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = tabs[index],
                                        color = if (isSelected) Primary else TextGray,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Content List
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else if (query.isEmpty() || !hasSearched) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp), // Visual center adjustment
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.search_hint),
                                color = Color.Gray.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    val isListEmpty = when (selectedTabIndex) {
                        0 -> tracks.isEmpty()
                        1 -> artists.isEmpty()
                        2 -> albums.isEmpty()
                        3 -> playlists.isEmpty()
                        else -> true
                    }
                    
                    if (isListEmpty) {
                        NoResultsView(query)
                    } else {
                        val isDownloading = downloadState is DownloadState.Downloading
                    
                        LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (selectedTabIndex) {
                            0 -> {

                                items(tracks) { track ->
                                    TrackItem(
                                        track = track,
                                        isDownloading = isDownloading,
                                        onDownloadClick = {
                                            downloadManager.startTrackDownload(track.id, track.title)
                                        }
                                    )
                                }
                            }
                            1 -> {

                                items(artists) { artist ->
                                    ArtistItem(artist, onClick = { onArtistClick(artist.id) })
                                }
                            }
                            2 -> {

                                items(albums) { album ->
                                    AlbumItem(album, onClick = { onAlbumClick(album.id) })
                                }
                            }
                            3 -> {

                                items(playlists) { playlist ->
                                    PlaylistItem(playlist, onClick = { onPlaylistClick(playlist.id) })
                                }
                            }
                        }
                        if (isAppending) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun TrackItem(
    track: Track,
    isDownloading: Boolean = false,
    onDownloadClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { /* Handle click */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.album?.coverMedium,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.unknown_track),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist?.name ?: stringResource(R.string.unknown_artist),
                    color = TextGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (track.album != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.label_song),
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        IconButton(
            onClick = onDownloadClick,
            enabled = !isDownloading,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = if (isDownloading) TextGray.copy(alpha = 0.5f) else Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ArtistItem(artist: Artist, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.pictureMedium,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name ?: stringResource(R.string.unknown_artist),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${artist.nbFan ?: 0} ${stringResource(R.string.label_fans)}",
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AlbumItem(album: com.crowstar.deeztrackermobile.features.deezer.Album, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.coverMedium,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title ?: stringResource(R.string.unknown_album),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = album.artist?.name ?: stringResource(R.string.unknown_artist),
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.pictureMedium,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title ?: stringResource(R.string.unknown_playlist),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${playlist.nbTracks ?: 0} ${stringResource(R.string.label_tracks)}",
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NoResultsView(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info, // Ensure Icons.Default.Info is imported
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_results),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.search_no_results_desc, query),
                color = TextGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
