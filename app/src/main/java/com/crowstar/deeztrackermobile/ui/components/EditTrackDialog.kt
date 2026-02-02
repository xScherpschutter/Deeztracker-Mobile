package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.localmusic.TrackMetadata
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun EditTrackDialog(
    initialMetadata: TrackMetadata,
    onDismiss: () -> Unit,
    onSave: (TrackMetadata) -> Unit
) {
    var title by remember { mutableStateOf(initialMetadata.title) }
    var artist by remember { mutableStateOf(initialMetadata.artist) }
    var album by remember { mutableStateOf(initialMetadata.album) }
    var year by remember { mutableStateOf(initialMetadata.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.edit_track_title), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditTextField(
                    label = stringResource(R.string.edit_label_title),
                    value = title,
                    onValueChange = { title = it }
                )
                EditTextField(
                    label = stringResource(R.string.edit_label_artist),
                    value = artist,
                    onValueChange = { artist = it }
                )
                EditTextField(
                    label = stringResource(R.string.edit_label_album),
                    value = album,
                    onValueChange = { album = it }
                )
                EditTextField(
                    label = stringResource(R.string.edit_label_year),
                    value = year,
                    onValueChange = { year = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(TrackMetadata(title, artist, album, year))
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

@Composable
private fun EditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextGray) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Primary
        )
    )
}
