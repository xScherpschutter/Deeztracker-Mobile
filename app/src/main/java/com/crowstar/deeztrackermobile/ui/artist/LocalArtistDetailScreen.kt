package com.crowstar.deeztrackermobile.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.library.LocalMusicViewModel
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.text.style.TextAlign
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionContext
import com.crowstar.deeztrackermobile.ui.library.LocalTrackItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalArtistDetailScreen(
    artistName: String,
    onBackClick: () -> Unit,
    onTrackClick: (LocalTrack, List<LocalTrack>) -> Unit,
    onPlayArtist: (List<LocalTrack>) -> Unit,
    onAddToQueue: ((LocalTrack) -> Unit)? = null,
    selectionViewModel: SelectionViewModel,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    val unfilteredTracks by viewModel.unfilteredTracks.collectAsState()
    
    val tracks = remember(unfilteredTracks, artistName) {
        unfilteredTracks.filter { it.artist == artistName }
    }

    val selectedTracks by selectionViewModel.selectedTracks.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteSuccess()
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

    LaunchedEffect(tracks) {
        if (tracks.isEmpty() && !viewModel.isLoading.value) {
            onBackClick()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp + contentPadding)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = TextGray
                        )
                        
                        val firstTrack = tracks.firstOrNull()
                        if (firstTrack != null) {
                            com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                                model = firstTrack.albumArtUri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = artistName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Text(
                        text = stringResource(R.string.stats_playlist_tracks_format, tracks.size),
                        color = TextGray,
                        fontSize = 14.sp
                    )
                     Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onPlayArtist(tracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                         Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text(stringResource(R.string.action_play_artist), color = Color.White)
                    }
                     Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Tracks
            items(tracks, key = { it.id }) { track ->
                LocalTrackItem(
                    track = track,
                    isSelected = selectedTracks.any { it.id == track.id },
                    inSelectionMode = isSelectionMode,
                    onClick = { 
                        if (isSelectionMode) {
                            selectionViewModel.toggleSelection(SelectedTrack.Local(track))
                        } else {
                            onTrackClick(track, tracks) 
                        }
                    },
                    onLongClick = {
                        selectionViewModel.enterSelectionMode(SelectionContext.LOCAL, SelectedTrack.Local(track))
                    },
                    onDelete = { viewModel.requestDeleteTrack(track) },
                    onAddToQueue = { onAddToQueue?.invoke(track) }
                )
            }
        }
    }
}
