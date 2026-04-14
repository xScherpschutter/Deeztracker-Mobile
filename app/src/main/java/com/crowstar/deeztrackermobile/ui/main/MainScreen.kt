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
import com.crowstar.deeztrackermobile.ui.utils.LocalSnackbarController
import com.crowstar.deeztrackermobile.ui.utils.SnackbarController
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@HiltViewModel
class MainViewModel @Inject constructor(
    val playerController: PlayerController,
    val downloadManager: DownloadManager
) : ViewModel()

@Composable
fun MainScreen(
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    importAction: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val playerController = viewModel.playerController
    val playerState by playerController.playerState.collectAsState()
    
    val context = LocalContext.current
    val downloadManager = viewModel.downloadManager
    val downloadState by downloadManager.downloadState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarController = remember { SnackbarController(snackbarHostState, scope) }
    
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
        // Navigation guard
        var isNavigating by remember { mutableStateOf(false) }
        
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
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            
            val isMiniPlayerVisible = playerState.currentTrack != null
            val isBottomBarVisible = currentRoute in listOf("library", "search", "downloads", "settings")
            
            var floatingUIHeight = 0.dp
            
            if (isBottomBarVisible) {
                floatingUIHeight += 84.dp 
            } 
            
            if (isMiniPlayerVisible) {
                floatingUIHeight += 70.dp
            }
            
            val containerBottomPadding = if (currentRoute in listOf("library", "search", "downloads", "settings", "player")) 0.dp else floatingUIHeight + 16.dp 

            Box(modifier = Modifier.fillMaxSize().padding(bottom = containerBottomPadding)) {
                MainNavigation(
                    navController, 
                    onArtistClick, 
                    onPlaylistClick,
                    onAlbumClick,
                    importAction = importAction,
                    onLogout = onLogout,
                    playerController = playerController,
                    safePopBackStack = safePopBackStack,
                    contentPadding = floatingUIHeight
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

                if (isBottomBarVisible) {
                    BottomNavigationBar(navController)
                }
            }

            // Standard Snackbar Host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isMiniPlayerVisible) 80.dp else 16.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun MainNavigation(
    navController: NavHostController,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    importAction: () -> Unit,
    onLogout: () -> Unit,
    playerController: PlayerController,
    safePopBackStack: () -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp
) {
    NavHost(navController = navController, startDestination = "library") {
        composable("library") { 
            LocalMusicScreen(
                onTrackClick = { track, list, context -> playerController.playTrack(track, list, context) },
                onAlbumClick = { album -> navController.navigate("local_album/${album.id}") },
                onArtistClick = { artist -> navController.navigate("local_artist/${artist.name}") },
                onImportPlaylist = importAction,
                contentPadding = contentPadding
            )
        }
        composable("search") { 
            SearchScreen(
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick,
                contentPadding = contentPadding
            )
        }
        composable("downloads") { 
            DownloadsScreen(
                onTrackClick = { track, list -> playerController.playTrack(track, list, "Downloads") },
                contentPadding = contentPadding
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
            LocalAlbumDetailScreen(
                albumId = albumId,
                onBackClick = safePopBackStack,
                onTrackClick = { track, list -> playerController.playTrack(track, list, "Album") },
                onPlayAlbum = { list -> playerController.playTrack(list.first(), list, "Album") }
            )
        }
        composable(
            route = "local_artist/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            LocalArtistDetailScreen(
                artistName = artistName,
                onBackClick = safePopBackStack,
                onTrackClick = { track, list -> playerController.playTrack(track, list, "Artist") },
                onPlayArtist = { list -> playerController.playTrack(list.first(), list, "Artist") }
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
