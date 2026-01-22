package com.crowstar.deeztrackermobile.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crowstar.deeztrackermobile.ui.screens.LocalMusicViewModel
import com.crowstar.deeztrackermobile.ui.screens.LocalMusicViewModelFactory
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rusteer.Track
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Services
    val rustService = remember { RustDeezerService(context) }
    val viewModel: LocalMusicViewModel = viewModel(
        factory = LocalMusicViewModelFactory(context)
    )
    val localRepo = remember { LocalMusicRepository(context.contentResolver) }
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // State
    var playlistName by remember { mutableStateOf("") }
    var importedTracks by remember { mutableStateOf<List<ImportedTrackState>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    
    // Resources for logic that needs string access
    val readingMsg = stringResource(R.string.import_playlist_reading)
    val checkingMsg = stringResource(R.string.import_playlist_checking_local)
    val searchingMsg = stringResource(R.string.import_playlist_searching_deezer)
    val queuedMsg = stringResource(R.string.import_playlist_download_queued)

    // File Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                progressMessage = readingMsg
                try {
                    val (name, tracks) = parsePlaylist(context, it)
                    playlistName = name
                    
                    // Check local files
                    progressMessage = checkingMsg
                    val localTracks = localRepo.getAllTracks()
                    
                    // Map to state objects
                    val stateList = tracks.map { query ->
                        // Simple check: does any local track title contain the query?
                        // Or try to parse Artist - Title from query
                        val match = findLocalMatch(query, localTracks)
                        if (match != null) {
                            ImportedTrackState(query, ImportStatus.FoundLocally(match))
                        } else {
                            ImportedTrackState(query, ImportStatus.Missing)
                        }
                    }
                    importedTracks = stateList
                    
                    // Auto-search for missing
                    progressMessage = searchingMsg
                    stateList.forEachIndexed { index, item ->
                        if (item.status is ImportStatus.Missing) {
                             val searchResult = rustService.searchTracks(item.rawQuery).firstOrNull()
                             if (searchResult != null) {
                                 importedTracks = importedTracks.toMutableList().apply {
                                     this[index] = item.copy(status = ImportStatus.FoundOnDeezer(searchResult))
                                 }
                             } else {
                                 importedTracks = importedTracks.toMutableList().apply {
                                     this[index] = item.copy(status = ImportStatus.NotFound)
                                 }
                             }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("ImportPlaylist", "Error importing", e)
                } finally {
                    isProcessing = false
                    progressMessage = ""
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_playlist_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (importedTracks.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.import_playlist_empty_title), color = TextGray)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { launcher.launch(arrayOf("*/*")) }, // Mime types can be tricky, allowing all for now
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(stringResource(R.string.import_playlist_select_file), color = Color.Black)
                        }
                    }
                }
            } else {
                // List State
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.import_playlist_format, playlistName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    val missingCount = importedTracks.count { it.status is ImportStatus.FoundOnDeezer }
                    val totalValid = importedTracks.count { it.status is ImportStatus.FoundOnDeezer || it.status is ImportStatus.FoundLocally }
                    val canImport = totalValid > 0
                    
                    Button(
                        onClick = {
                            scope.launch {
                                // Create Playlist in App
                                val playlistId = viewModel.createPlaylistSync(playlistName)
                                
                                // Add found local tracks to playlist immediately
                                importedTracks.forEach { item ->
                                    if (item.status is ImportStatus.FoundLocally) {
                                        viewModel.addTrackToPlaylistId(playlistId, item.status.localTrack)
                                    }
                                }

                                // Trigger downloads for missing tracks AND link them to playlist
                                val tracksToDownload = importedTracks.mapNotNull { 
                                    (it.status as? ImportStatus.FoundOnDeezer)?.track
                                }
                                tracksToDownload.forEach { track ->
                                    downloadManager.startTrackDownload(
                                        trackId = track.id.toLong(), 
                                        title = track.title, 
                                        targetPlaylistId = playlistId // Auto-add to playlist on completion
                                    )
                                }
                                
                                // Show snackbar confirmation
                                val downloadCount = tracksToDownload.size
                                if (downloadCount > 0) {
                                    snackbarHostState.showSnackbar(
                                        message = String.format(queuedMsg, downloadCount), // Use format directly here or context.getString
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                
                                onBackClick()
                            }
                        },
                        enabled = canImport && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        val text = if (missingCount > 0) 
                            stringResource(R.string.import_playlist_action_import_download, missingCount)
                        else 
                            stringResource(R.string.import_playlist_action_create)
                        Text(text, color = Color.Black)
                    }
                }
                
                if (isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Text(progressMessage, color = TextGray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(importedTracks) { track ->
                        ImportItemRow(track)
                    }
                }
            }
        }
    }
}

@Composable
fun ImportItemRow(item: ImportedTrackState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.rawQuery,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtext = when (val s = item.status) {
                is ImportStatus.FoundLocally -> stringResource(R.string.import_playlist_found_locally, s.localTrack.title)
                is ImportStatus.FoundOnDeezer -> stringResource(R.string.import_playlist_found_deezer, s.track.title, s.track.artist)
                is ImportStatus.NotFound -> stringResource(R.string.import_playlist_not_found)
                is ImportStatus.Missing -> stringResource(R.string.import_playlist_searching)
            }
            Text(
                text = subtext,
                color = TextGray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        when (item.status) {
            is ImportStatus.FoundLocally -> Icon(Icons.Default.Check, null, tint = Color.Green)
            is ImportStatus.FoundOnDeezer -> Icon(Icons.Default.Download, null, tint = Primary)
            is ImportStatus.NotFound -> Icon(Icons.Default.Error, null, tint = Color.Red)
            is ImportStatus.Missing -> CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextGray)
        }
    }
}

data class ImportedTrackState(
    val rawQuery: String,
    val status: ImportStatus
)

sealed class ImportStatus {
    object Missing : ImportStatus()
    data class FoundLocally(val localTrack: LocalTrack) : ImportStatus()
    data class FoundOnDeezer(val track: Track) : ImportStatus()
    object NotFound : ImportStatus()
}

// Logic Helper
suspend fun parsePlaylist(context: android.content.Context, uri: Uri): Pair<String, List<String>> = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver
    val lines = mutableListOf<String>()
    
    // Attempt to guess name from filename
    // Cursor query for display name not shown here for brevity, defaulting to "Imported"
    var name = context.getString(R.string.import_playlist_default_name)
    
    // Query display name
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                val filename = cursor.getString(nameIndex)
                name = filename.substringBeforeLast(".") // Remove extension
            }
        }
    }
    
    // Check extension
    // Simple parsing logic...
    
    // Re-implementation for robustness
    val queries = mutableListOf<String>()
    contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line = reader.readLine()
            var lastWasExtInf = false
            
            while (line != null) {
                line = line.trim()
                if (line.startsWith("#EXTINF:")) {
                    val info = line.substringAfter(",", "").trim()
                    if (info.isNotEmpty()) {
                        queries.add(info)
                        lastWasExtInf = true
                    }
                } else if (!line.startsWith("#") && line.isNotEmpty()) {
                    if (!lastWasExtInf) {
                        // Try to get filename from path
                        // /storage/emulated/0/Music/Artist - Title.mp3
                        val filename = line.substringAfterLast("/").substringAfterLast("\\")
                        val nameWithoutExt = filename.substringBeforeLast(".")
                        if (nameWithoutExt.isNotEmpty()) {
                            queries.add(nameWithoutExt)
                        }
                    }
                    lastWasExtInf = false
                }
                line = reader.readLine()
            }
        }
    }
    
    Pair(name, queries.distinct()) // remove duplicates
}

/**
 * Fuzzy match a query string against local tracks.
 * Query format expected: "Artist - Title" or just "Title"
 */
fun findLocalMatch(query: String, tracks: List<LocalTrack>): LocalTrack? {
    // Normalization
    val normalizedQuery = query.lowercase().replace(Regex("[^a-z0-9]"), "")
    
    return tracks.find { track ->
        val t = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val a = track.artist.lowercase().replace(Regex("[^a-z0-9]"), "")
        val full = "$a$t"
        val fullReverse = "$t$a"
        
        // Check exact match of title
        if (t == normalizedQuery) return@find true
        
        // Check "ArtistTitle" match
        if (full.contains(normalizedQuery) || normalizedQuery.contains(full)) return@find true
        
        false
    }
}
