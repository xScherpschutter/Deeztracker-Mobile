package com.crowstar.deeztrackermobile.ui.playlist

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import uniffi.rusteer.Track
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistScreen(
    onBackClick: () -> Unit,
    viewModel: ImportPlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val playlistName by viewModel.playlistName.collectAsState()
    val importedTracks by viewModel.importedTracks.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progressMessage by viewModel.progressMessage.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadedCount by viewModel.downloadedCount.collectAsState()
    val totalToDownload by viewModel.totalToDownload.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isDownloading) { }

    // Resources
    val readingMsg = stringResource(R.string.import_playlist_reading)
    val checkingMsg = stringResource(R.string.import_playlist_checking_local)
    val searchingMsg = stringResource(R.string.import_playlist_searching_deezer)
    val downloadingProgressTemplate = stringResource(R.string.import_playlist_downloading_progress)
    val creatingMsg = stringResource(R.string.import_playlist_creating)
    val createdWithDownloadsTemplate = stringResource(R.string.import_playlist_created_with_downloads)
    val createdTemplate = stringResource(R.string.import_playlist_created)
    val startingDownloadTemplate = stringResource(R.string.import_playlist_starting_download)

    // Error messages
    val errorInvalidFile = stringResource(R.string.import_playlist_error_invalid_file)
    val errorEmptyFile = stringResource(R.string.import_playlist_error_empty_file)
    val errorCorrupted = stringResource(R.string.import_playlist_error_corrupted)
    val errorRead = stringResource(R.string.import_playlist_error_read)
    val errorUnknown = stringResource(R.string.import_playlist_error_unknown)

    // Monitor download progress
    val downloadTrigger by viewModel.downloadRefreshTrigger.collectAsState()
    
    LaunchedEffect(downloadTrigger) {
        if (isDownloading && downloadedCount < totalToDownload) {
            viewModel.incrementDownloadedCount()
            viewModel.updateProgressMessage(downloadingProgressTemplate.format(downloadedCount + 1, totalToDownload))
            
            if (downloadedCount + 1 >= totalToDownload) {
                viewModel.updateProgressMessage(creatingMsg)
                viewModel.createPlaylistWithTracks()
                
                snackbarHostState.showSnackbar(
                    message = createdWithDownloadsTemplate.format(playlistName, totalToDownload),
                    duration = SnackbarDuration.Short
                )
                onBackClick()
            }
        }
    }
    

    // File Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.startImport(
                context = context,
                uri = it,
                readingMsg = readingMsg,
                checkingMsg = checkingMsg,
                searchingMsg = searchingMsg,
                onInvalidFile = { scope.launch { snackbarHostState.showSnackbar(errorInvalidFile) } },
                onEmptyFile = { scope.launch { snackbarHostState.showSnackbar(errorEmptyFile) } },
                onCorrupted = { scope.launch { snackbarHostState.showSnackbar(errorCorrupted) } },
                onIOError = { scope.launch { snackbarHostState.showSnackbar(errorRead) } },
                onUnknownError = { scope.launch { snackbarHostState.showSnackbar(errorUnknown) } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_playlist_title), color = Color.White) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        enabled = !isDownloading
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = if (isDownloading) Color.White.copy(alpha = 0.3f) else Color.White
                        )
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
                            onClick = { 
                                launcher.launch(arrayOf(
                                    "audio/x-mpegurl",
                                    "audio/mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "audio/x-scpls",
                                    "text/plain"
                                ))
                            },
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
                            viewModel.executeImport(startingDownloadTemplate) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(createdTemplate.format(playlistName))
                                    onBackClick()
                                }
                            }
                        },
                        enabled = canImport && !isProcessing && !isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        val text = if (missingCount > 0) 
                            stringResource(R.string.import_playlist_action_import_download, missingCount)
                        else 
                            stringResource(R.string.import_playlist_action_create)
                        Text(text, color = Color.Black)
                    }
                }
                
                if (isProcessing || isDownloading) {
                    LinearProgressIndicator(
                        progress = if (totalToDownload > 0) downloadedCount.toFloat() / totalToDownload.toFloat() else 0f,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
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
