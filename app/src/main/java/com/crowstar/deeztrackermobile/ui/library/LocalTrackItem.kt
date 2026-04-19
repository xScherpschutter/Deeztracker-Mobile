package com.crowstar.deeztrackermobile.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.common.MarqueeText
import com.crowstar.deeztrackermobile.ui.common.TrackOptionsMenu
import com.crowstar.deeztrackermobile.ui.common.selection.SelectedTrack
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalTrackItem(
    track: LocalTrack,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    onShare: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null, // No longer strictly needed for UI but kept for compatibility if needed
    onAddToQueue: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    deleteLabel: String = stringResource(R.string.action_delete),
    showAllOptions: Boolean = true,
    trackNumber: Int? = null
) {
    var showDetails by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (trackNumber != null) {
            Text(
                text = "$trackNumber",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            // Album Art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                com.crowstar.deeztrackermobile.ui.common.TrackArtwork(
                    model = track.albumArtUri,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = track.title ?: "",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(
                    text = track.artist ?: "",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Format Badge
                FormatBadge(track.mimeType)
                
                if (track.size > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = track.getFormattedSize().replace(" ", ""), // Compact size
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Menu (Centralized)
        TrackOptionsMenu(
            track = SelectedTrack.Local(track),
            onAddToQueue = onAddToQueue,
            onShare = if (showAllOptions) onShare else null,
            onEdit = if (showAllOptions) onEdit else null,
            onDelete = onDelete,
            onShowDetails = if (showAllOptions) { { showDetails = true } } else null,
            deleteLabel = deleteLabel
        )
    }

    if (showDetails) {
        com.crowstar.deeztrackermobile.ui.common.TrackDetailsDialog(
            track = track,
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
fun FormatBadge(mimeType: String) {
    val (text, color) = when {
        mimeType.contains("flac") -> "FLAC" to Color(0xFF00A2E8) // Blue
        mimeType.contains("wav") -> "WAV" to Color(0xFFFFC107) // Amber
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3" to TextGray
        mimeType.contains("mp4") || mimeType.contains("aac") -> "AAC" to TextGray
        else -> "AUDIO" to TextGray
    }
    
    val backgroundColor = if (text == "FLAC" || text == "WAV") color.copy(alpha = 0.2f) else SurfaceDark
    val textColor = if (text == "FLAC" || text == "WAV") color else TextGray

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
