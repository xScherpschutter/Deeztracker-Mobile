package com.crowstar.deeztrackermobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    onBackClick: () -> Unit,
    viewModel: LocalMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LocalMusicViewModelFactory(LocalContext.current)
    )
) {
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    
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
                        .padding(horizontal = 8.dp), // Reduce padding effectively
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
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
                        Text(
                            text = "Search local files...",
                            color = TextGray,
                            fontSize = 14.sp
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        LocalTrackItem(track)
                    }
                }
            }
        }
    }
}

@Composable
fun LocalTrackItem(track: LocalTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Play track */ }
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
        IconButton(
            onClick = { /* Menu */ },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = TextGray
            )
        }
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
