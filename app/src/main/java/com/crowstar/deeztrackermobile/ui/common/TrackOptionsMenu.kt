package com.crowstar.deeztrackermobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun TrackOptionsMenu(
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = TextGray
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(SurfaceDark)
        ) {
            DropdownMenuItem(
                text = { 
                    Text(
                        text = stringResource(R.string.action_add_to_playlist),
                        color = Color.White 
                    ) 
                },
                onClick = {
                    showMenu = false
                    onAddToPlaylist()
                }
            )
            
            // Aquí podemos añadir más opciones en el futuro:
            // DropdownMenuItem(text = { Text("Ir al Artista") }, onClick = { ... })
        }
    }
}
