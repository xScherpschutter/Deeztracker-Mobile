package com.crowstar.deeztrackermobile.ui.screens

import android.app.Activity
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.formatTime
import com.crowstar.deeztrackermobile.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.crowstar.deeztrackermobile.ui.components.AlphabeticalFastScroller
import com.crowstar.deeztrackermobile.ui.components.MarqueeText
import com.crowstar.deeztrackermobile.features.localmusic.MetadataEditor
import com.crowstar.deeztrackermobile.features.localmusic.TrackMetadata
import com.crowstar.deeztrackermobile.ui.components.EditTrackDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: DownloadsViewModel = viewModel(
        factory = DownloadsViewModelFactory(LocalContext.current)
    )
) {
    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deleteIntentSender by viewModel.deleteIntentSender.collectAsState()
    val context = LocalContext.current
    
    // Search Query State
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Re-apply filter when loading completes or query changes (handles screen restoration)
    LaunchedEffect(isLoading, searchQuery) {
        if (!isLoading) {
            viewModel.filter(searchQuery)
        }
    }
    
    // List Scroll State
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    
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

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteSuccess()
        }
        viewModel.resetDeleteIntentSender()
    }
    
    // Effect to launch delete intent if needed
    LaunchedEffect(deleteIntentSender) {
        deleteIntentSender?.let { intentSender ->
            val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
            deleteLauncher.launch(request)
        }
    }
    
    // Playlist & Details State
    var trackForPlaylist by remember { mutableStateOf<LocalTrack?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackDetails by remember { mutableStateOf<LocalTrack?>(null) }
    
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
                        onScanComplete = { viewModel.loadDownloadedMusic() }
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

    if (trackDetails != null) {
        com.crowstar.deeztrackermobile.ui.components.TrackDetailsDialog(
            track = trackDetails!!,
            onDismiss = { trackDetails = null }
        )
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.downloads_title), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDownloadedMusic() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.filter(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 8.dp),
                placeholder = { Text(stringResource(R.string.downloads_search_hint), color = TextGray) },
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if(searchQuery.isNotEmpty()) stringResource(R.string.no_results) else stringResource(R.string.downloads_empty), 
                            color = TextGray
                        )
                    }
                }
            } else {
                
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
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp + contentPadding, end = 36.dp) // Space for fast scroller + Dynamic Bottom Padding
                    ) {
                        items(tracks) { track ->
                            DownloadedTrackItem(
                                track = track,
                                onClick = { onTrackClick(track, tracks) },
                                onDelete = { viewModel.deleteTrack(track) },
                                onShare = { shareTrack(track) },
                                onEdit = { trackToEdit = track },
                                onDetails = { trackDetails = track },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                        }
                    }
                    
                    // Sync fast scroller with manual scroll position
                    LaunchedEffect(listState.firstVisibleItemIndex) {
                        val firstVisibleTrack = tracks.getOrNull(listState.firstVisibleItemIndex)
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
                                    listState.scrollToItem(index)
                                    currentLetter.value = letter
                                } else {
                                    // Find next available letter
                                    val availableLetters = letterIndexMap.keys.sorted()
                                    val nextLetter = availableLetters.firstOrNull { it >= letter }
                                    if (nextLetter != null) {
                                        letterIndexMap[nextLetter]?.let {
                                            listState.scrollToItem(it)
                                            currentLetter.value = nextLetter
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedTrackItem(
    track: LocalTrack,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art
        com.crowstar.deeztrackermobile.ui.components.TrackArtwork(
            model = track.albumArtUri,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
            MarqueeText(
                text = "${track.artist} • ${formatTime(track.duration)}",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.player_options), tint = TextGray)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
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
                         onDetails()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share), color = Color.White) },
                    onClick = { 
                        showMenu = false
                        onShare()
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
                    text = { Text(stringResource(R.string.action_delete), color = Color.Red) },
                    onClick = { 
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}


