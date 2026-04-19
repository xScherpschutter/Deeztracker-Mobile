package com.crowstar.deeztrackermobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.ui.login.LoginScreen
import com.crowstar.deeztrackermobile.ui.main.MainScreen
import com.crowstar.deeztrackermobile.ui.playlist.ImportPlaylistScreen
import com.crowstar.deeztrackermobile.ui.common.selection.SelectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val rustService: RustDeezerService,
    private val playerController: PlayerController
) : ViewModel() {
    fun isLoggedIn() = rustService.isLoggedIn()
    fun logout() {
        playerController.stop()
    }
}

@Composable
fun AppNavigation(
    viewModel: NavigationViewModel = hiltViewModel(),
    selectionViewModel: SelectionViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination = if (viewModel.isLoggedIn()) "main" else "login"
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
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
                importAction = {
                    navController.navigate("import_playlist")
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                selectionViewModel = selectionViewModel
            )
        }

        composable("import_playlist") {
            ImportPlaylistScreen(
                onBackClick = safePopBackStack
            )
        }
    }
}
