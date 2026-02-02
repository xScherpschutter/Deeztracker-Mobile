package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun TrackDetailsDialog(
    track: LocalTrack,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.details_title), color = Color.White) },
        text = {
            Column {
                DetailRow(stringResource(R.string.details_path_label), track.filePath)
                DetailRow(stringResource(R.string.details_size_label), track.getFormattedSize())
                DetailRow(stringResource(R.string.details_format_label), track.mimeType)
                DetailRow(stringResource(R.string.details_bitrate_label), "Unknown")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = Primary)
            }
        },
        containerColor = BackgroundDark,
        titleContentColor = Color.White,
        textContentColor = TextGray
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 14.sp)
    }
}
