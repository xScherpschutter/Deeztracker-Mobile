package com.crowstar.deeztrackermobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
                onLocalAlbumClick = { albumId ->
                    navController.navigate("localAlbum/$albumId")
                },
                onLocalArtistClick = { artistName ->
                    navController.navigate("localArtist/$artistName")
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
                onBackClick = { navController.popBackStack() },
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
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistScreen(
                playlistId = playlistId,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Local Music Detail Routes
         composable(
            route = "localAlbum/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            com.crowstar.deeztrackermobile.ui.screens.LocalAlbumDetailScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() },
                onTrackClick = { track, playlist ->
                    // Get PlayerController instance
                    val playerController = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context)
                    playerController.playTrack(track, playlist, source = "Album: ${track.album}")
                },
                onPlayAlbum = { tracks ->
                     if (tracks.isNotEmpty()) {
                         val playerController = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context)
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
            com.crowstar.deeztrackermobile.ui.screens.LocalArtistDetailScreen(
                artistName = artistName,
                onBackClick = { navController.popBackStack() },
                onTrackClick = { track, playlist ->
                    val playerController = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context)
                    playerController.playTrack(track, playlist, source = "Artist: ${track.artist}")
                },
                onPlayArtist = { tracks ->
                    if (tracks.isNotEmpty()) {
                        val playerController = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context)
                        playerController.playTrack(tracks.first(), tracks, source = "Artist: $artistName")
                    }
                }
            )
        }
    }
}
