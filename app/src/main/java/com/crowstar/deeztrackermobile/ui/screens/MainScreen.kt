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
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun MainScreen(
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val playerController = remember { PlayerController.getInstance(context) }
    // Ensure we observe state if needed at this level, or just pass controller
    val playerState by playerController.playerState.collectAsState()
    
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
        // Main Content Area
        // Add padding at the bottom so content isn't hidden by the floating bars ONLY if not in player
        val contentPadding = if (currentRoute == "player") 0.dp else 140.dp
        Box(modifier = Modifier.fillMaxSize().padding(bottom = contentPadding)) {
            MainNavigation(
                navController, 
                onArtistClick, 
                onPlaylistClick,
                onAlbumClick,
                playerController = playerController,
                safePopBackStack = safePopBackStack
            )
        }

        // Floating UI Container (MiniPlayer + BottomBar)
        if (currentRoute != "player") {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 24.dp) // Margin from screen edges
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
    val items = listOf("Library", "Search", "Downloads", "Settings")
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
            .background(Color(0xFF1E1E1E).copy(alpha = 0.98f))
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
    playerController: PlayerController,
    safePopBackStack: () -> Unit
) {
    NavHost(navController, startDestination = "library") {
        composable("search") { 
            SearchScreen(
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick
            )
        }
        
        composable("library") { 
            LocalMusicScreen(
                onBackClick = { /* No back action */ },
                onTrackClick = { track, playlist -> 
                    playerController.playTrack(track, playlist)
                },
                onAlbumClick = { album -> navController.navigate("localAlbum/${album.id}") },
                onArtistClick = { artist -> navController.navigate("localArtist/${artist.name}") }
            ) 
        }
        
        composable("downloads") { DownloadsScreen() }
        composable("settings") { SettingsScreen() }
        
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
                    playerController.playTrack(track, playlist, source = "Album: ${track.album}")
                },
                onPlayAlbum = { tracks ->
                     if (tracks.isNotEmpty()) {
                         playerController.playTrack(tracks.first(), tracks, source = "Album: ${tracks.first().album}")
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
                    playerController.playTrack(track, playlist, source = "Artist: ${track.artist}")
                },
                onPlayArtist = { tracks ->
                    if (tracks.isNotEmpty()) {
                        playerController.playTrack(tracks.first(), tracks, source = "Artist: $artistName")
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
