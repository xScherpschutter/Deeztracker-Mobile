package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_playlist_title), color = Color.White) },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text(stringResource(R.string.new_playlist_name)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextGray,
                    cursorColor = Primary,
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistName.isNotBlank() && playlistName != currentName) {
                        onEdit(playlistName)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextGray)
            }
        },
        containerColor = BackgroundDark
    )
}
