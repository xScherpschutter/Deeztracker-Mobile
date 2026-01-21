package com.crowstar.deeztrackermobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.ui.screens.AlbumScreen
import com.crowstar.deeztrackermobile.ui.screens.ArtistScreen
import com.crowstar.deeztrackermobile.ui.screens.LoginScreen
import com.crowstar.deeztrackermobile.ui.screens.MainScreen
import com.crowstar.deeztrackermobile.ui.screens.PlaylistScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Check if user is already logged in
    val rustService = remember { RustDeezerService(context) }
    val startDestination = if (rustService.isLoggedIn()) "main" else "login"
    
    // Navigation guard to prevent double-clicks causing invalid state
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
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
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        
        composable("main") {
            MainScreen(
                onArtistClick = { artistId ->
                    navController.navigate("artist/$artistId")
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate("playlist/$playlistId")
                },
                onAlbumClick = { albumId ->
                    navController.navigate("album/$albumId")
                },
                importAction = {
                    navController.navigate("import_playlist")
                },
                onLogout = {
                    com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context).stop()
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = "artist/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            ArtistScreen(
                artistId = artistId,
                onBackClick = safePopBackStack,
                onAlbumClick = { albumId ->
                    navController.navigate("album/${albumId}")
                }
            )
        }
        
        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumScreen(
                albumId = albumId,
                onBackClick = safePopBackStack
            )
        }
        
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistScreen(
                playlistId = playlistId,
                onBackClick = safePopBackStack
            )
        }

        composable("import_playlist") {
            com.crowstar.deeztrackermobile.ui.screens.ImportPlaylistScreen(
                onBackClick = safePopBackStack
            )
        }
    }
}
