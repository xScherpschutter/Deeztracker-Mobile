package com.crowstar.deeztrackermobile.ui.screens

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
import com.crowstar.deeztrackermobile.ui.screens.MiniPlayer
import com.crowstar.deeztrackermobile.ui.screens.MusicPlayerScreen
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.download.DownloadState
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    importAction: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val playerController = remember { PlayerController.getInstance(context) }
    val playerState by playerController.playerState.collectAsState()
    
    // Download Manager for centralized snackbars
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadState by downloadManager.downloadState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle download state changes
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Completed -> {
                val message = if (state.failedCount > 0) {
                    "Downloaded ${state.successCount} tracks, ${state.failedCount} failed"
                } else {
                    "Downloaded: ${state.title}"
                }
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                downloadManager.resetState()
            }
            is DownloadState.Error -> {
                snackbarHostState.showSnackbar(
                    "Download failed: ${state.message}",
                    duration = SnackbarDuration.Short
                )
                downloadManager.resetState()
            }
            is DownloadState.Downloading -> {
            }
            else -> {}
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Navigation guard to prevent double-clicks causing invalid state
    var isNavigating by remember { mutableStateOf(false) }
    
    // Safe popBackStack that prevents rapid successive calls
    val safePopBackStack: () -> Unit = remember(navController) {
        {
            if (!isNavigating && navController.previousBackStackEntry != null) {
                isNavigating = true
                navController.popBackStack()
            }
        }
    }
    
    // Reset navigation guard when route changes
    LaunchedEffect(currentRoute) {
        isNavigating = false
    }

    // We use a Box to overlay the floating UI on top of the content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        
        // Calculate dynamic bottom padding for Snackbar and Content
        val isMiniPlayerVisible = playerState.currentTrack != null
        val isBottomBarVisible = currentRoute in listOf("library", "search", "downloads", "settings")
        
        // Calculate the height of the floating UI elements
        var floatingUIHeight = 0.dp
        
        if (isBottomBarVisible) {
            // Navbar (64) + Spacer (12) + Padding (8) = 84dp used space
            floatingUIHeight += 84.dp 
        } 
        
        if (isMiniPlayerVisible) {
            // MiniPlayer height
            floatingUIHeight += 70.dp
        }
        
        // For 'library', 'search', 'downloads' we want content to go behind the bottom bars, so 0.dp container padding.
        // For others, if we want them to stop *above* the bars, we use floatingUIHeight.
        val containerBottomPadding = if (currentRoute in listOf("library", "search", "downloads", "settings", "player")) 0.dp else floatingUIHeight + 16.dp // Add a bit of buffer if not transparent

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
                bottomContentPadding = if (currentRoute in listOf("library", "search", "downloads", "settings")) floatingUIHeight + 8.dp else 0.dp // Pass padding to list screens
            )
        }
        
        // Snackbar Padding
        val finalSnackbarPadding = if (currentRoute == "player") {
            16.dp 
        } else {
             if (floatingUIHeight == 0.dp) 16.dp else floatingUIHeight + 8.dp
        }

        // Centralized Snackbar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = finalSnackbarPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }

        // Floating UI Container (MiniPlayer + BottomBar)
        if (currentRoute != "player") {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp) // Margin from screen edges
            ) {
                // Persistent Mini Player 
                MiniPlayer(
                    onClick = { navController.navigate("player") },
                    playerController = playerController
                )
                
                // Show Bottom Navigation only on main tab destinations
                val bottomNavRoutes = listOf("library", "search", "downloads", "settings")
                if (currentRoute in bottomNavRoutes) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Floating Bottom Navigation Bar
                    FloatingBottomNavigationBar(navController)
                }
            }
        }
    }
}



@Composable
fun FloatingBottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        stringResource(R.string.bottom_nav_library),
        stringResource(R.string.bottom_nav_search),
        stringResource(R.string.bottom_nav_downloads),
        stringResource(R.string.bottom_nav_settings)
    )
    val icons = listOf(
        Icons.Default.LibraryMusic,
        Icons.Default.Search,
        Icons.Default.Download,
        Icons.Default.Settings
    )
    val routes = listOf("library", "search", "downloads", "settings")
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.90f))
            .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = currentRoute == routes[index] // Simple check, might need better matching for sub-routes if we hide navbar
                NavButton(
                    icon = icons[index],
                    label = item,
                    isSelected = isSelected,
                    onClick = {
                        if (currentRoute != routes[index]) {
                            navController.navigate(routes[index]) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NavButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Disable ripple for cleaner look
            ) { onClick() }
            .padding(8.dp)
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
fun MainNavigation(
    navController: NavHostController,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    importAction: () -> Unit,
    onLogout: () -> Unit,
    playerController: PlayerController,
    safePopBackStack: () -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val downloadsTitle = stringResource(R.string.downloads_title)
    val localMusicTitle = stringResource(R.string.local_music_title)
    NavHost(navController, startDestination = "library") {
        composable("search") { 
            SearchScreen(
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick,
                contentPadding = bottomContentPadding
            )
        }
        
        composable("library") { 
            LocalMusicScreen(
                onBackClick = { /* No back action */ },
                onTrackClick = { track, playlist, source -> 
                    playerController.playTrack(track, playlist, source = source)
                },
                onAlbumClick = { album -> navController.navigate("localAlbum/${album.id}") },
                onArtistClick = { artist -> navController.navigate("localArtist/${artist.name}") },
                onImportPlaylist = importAction,
                contentPadding = bottomContentPadding
            ) 
        }
        
        composable("downloads") { 
            DownloadsScreen(
                onTrackClick = { track, playlist ->
                    playerController.playTrack(track, playlist, source = downloadsTitle)
                },
                contentPadding = bottomContentPadding
            ) 
        }
        composable("settings") { 
            SettingsScreen(
                onLogout = onLogout,
                contentPadding = bottomContentPadding
            ) 
        }
        
        // Internal Dialog/Detail Routes
        composable(
            route = "localAlbum/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            LocalAlbumDetailScreen(
                albumId = albumId,
                onBackClick = safePopBackStack,
                onTrackClick = { track, playlist ->
                    playerController.playTrack(track, playlist, source = track.album ?: "")
                },
                onPlayAlbum = { tracks ->
                     if (tracks.isNotEmpty()) {
                         playerController.playTrack(tracks.first(), tracks, source = tracks.first().album ?: "")
                     }
                }
            )
        }

        composable(
            route = "localArtist/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
           LocalArtistDetailScreen(
                artistName = artistName,
                onBackClick = safePopBackStack,
                onTrackClick = { track, playlist ->
                    playerController.playTrack(track, playlist, source = track.artist)
                },
                onPlayArtist = { tracks ->
                    if (tracks.isNotEmpty()) {
                        playerController.playTrack(tracks.first(), tracks, source = artistName)
                    }
                }
            )
        }
        
        // Full Player Screen
        composable(
            "player",
            enterTransition = { androidx.compose.animation.slideInVertically(initialOffsetY = { it }) },
            exitTransition = { androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) }
        ) { 
            MusicPlayerScreen(
                onCollapse = safePopBackStack,
                playerController = playerController
            ) 
        }
    }
}
