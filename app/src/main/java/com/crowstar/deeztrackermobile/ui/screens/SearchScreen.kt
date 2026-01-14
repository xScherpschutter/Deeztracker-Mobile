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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onArtistClick: (Long) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_tracks),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_playlists)
    )

    val repository = remember { DeezerRepository() }
    val scope = rememberCoroutineScope()

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
        if (query.isNotEmpty()) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    .padding(top = 48.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
            ) {
                Column {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, Color.Transparent, RoundedCornerShape(16.dp)) // Placeholder for glow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextGray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(Primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { performSearch(true) }),
                                decorationBox = { innerTextField ->
                                    if (query.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_hint),
                                            color = TextGray,
                                            fontSize = 18.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextGray
                                    )
                                }
                            }
                        }
                    }

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
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            if (tracks.isEmpty() && query.isNotEmpty()) {
                                item { Text(stringResource(R.string.no_results), color = TextGray) }
                            }
                            items(tracks) { track ->
                                TrackItem(track)
                            }
                        }
                        1 -> {
                            if (artists.isEmpty() && query.isNotEmpty()) {
                                item { Text(stringResource(R.string.no_results), color = TextGray) }
                            }
                            items(artists) { artist ->
                                ArtistItem(artist, onClick = { onArtistClick(artist.id) })
                            }
                        }
                        2 -> {
                            if (albums.isEmpty() && query.isNotEmpty()) {
                                item { Text(stringResource(R.string.no_results), color = TextGray) }
                            }
                            items(albums) { album ->
                                AlbumItem(album)
                            }
                        }
                        3 -> {
                            if (playlists.isEmpty() && query.isNotEmpty()) {
                                item { Text(stringResource(R.string.no_results), color = TextGray) }
                            }
                            items(playlists) { playlist ->
                                PlaylistItem(playlist)
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

        // Bottom Navigation / Mini Player (Mockup)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column {
                // Mini Player (Mock)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.9f)) // Glass-ish
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Starboy", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Playing on Device", color = Primary, fontSize = 12.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                        }
                    }
                }

                // Bottom Nav
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                        .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavButton(Icons.Default.Search, stringResource(R.string.nav_search), true)
                        NavButton(Icons.Default.LibraryMusic, stringResource(R.string.nav_library), false)
                        NavButton(Icons.Default.Download, stringResource(R.string.nav_downloads), false)
                        NavButton(Icons.Default.Settings, stringResource(R.string.nav_settings), false)
                    }
                }
            }
        }
    }
}

@Composable
fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Primary else TextGray
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Primary else TextGray
        )
    }
}

@Composable
fun TrackItem(track: Track) {
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
            onClick = { /* Download */ },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = TextGray,
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
fun AlbumItem(album: com.crowstar.deeztrackermobile.features.deezer.Album) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { /* Handle click */ }
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
fun PlaylistItem(playlist: Playlist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { /* Handle click */ }
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
