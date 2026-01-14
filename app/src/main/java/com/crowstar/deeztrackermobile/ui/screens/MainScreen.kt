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
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun MainScreen(
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit
) {
    val navController = rememberNavController()
    
    // We use a Box to overlay the floating UI on top of the content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Main Content Area
        // Add padding at the bottom so content isn't hidden by the floating bars
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 140.dp)) {
            MainNavigation(navController, onArtistClick, onPlaylistClick)
        }

        // Floating UI Container (MiniPlayer + BottomBar)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp) // Margin from screen edges
        ) {
            // Persistent Mini Player 
            MiniPlayer()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Floating Bottom Navigation Bar
            FloatingBottomNavigationBar(navController)
        }
    }
}

@Composable
fun MiniPlayer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.95f)) 
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .clickable { /* Expand Player */ }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Album Art Placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Starboy", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Playing on Device", color = Primary, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White) }
                
                // Play Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { /* Toggle Play */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                
                IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White) }
            }
        }
    }
}

@Composable
fun FloatingBottomNavigationBar(navController: NavHostController) {
    val items = listOf("Search", "Library", "Downloads", "Settings")
    val icons = listOf(
        Icons.Default.Search,
        Icons.Default.LibraryMusic,
        Icons.Default.Download,
        Icons.Default.Settings
    )
    val routes = listOf("search", "library", "downloads", "settings")
    
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
                val isSelected = currentRoute == routes[index]
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
    onPlaylistClick: (Long) -> Unit
) {
    NavHost(navController, startDestination = "search") {
        composable("search") {
            SearchScreen(
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick
            )
        }
        composable("library") {
            LocalMusicScreen(onBackClick = { /* No back action in main tab */ })
        }
        composable("downloads") {
            DownloadsScreen()
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}
