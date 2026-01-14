package com.crowstar.deeztrackermobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.ui.screens.LoginScreen
import com.crowstar.deeztrackermobile.ui.screens.SearchScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Check if user is already logged in
    val rustService = remember { RustDeezerService(context) }
    val startDestination = if (rustService.isLoggedIn()) "search" else "login"
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("search") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("search") {
            SearchScreen()
        }
    }
}
