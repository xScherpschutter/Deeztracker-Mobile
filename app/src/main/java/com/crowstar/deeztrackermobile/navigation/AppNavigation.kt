package com.crowstar.deeztrackermobile.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crowstar.deeztrackermobile.ui.screens.LoginScreen
import com.crowstar.deeztrackermobile.ui.screens.SearchScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "login") {
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
