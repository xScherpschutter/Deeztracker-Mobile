package com.crowstar.deeztrackermobile.ui.common.selection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark

@Composable
fun SelectionActionBatchBar(
    selectedCount: Int,
    context: SelectionContext,
    isAllSelected: Boolean = false,
    showSelectAll: Boolean = true,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemove: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp),
        color = SurfaceDark.copy(alpha = 0.95f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Selection Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close selection", tint = Color.White)
                }
                
                if (showSelectAll) {
                    // Master Checkbox Toggle
                    IconButton(onClick = onToggleSelectAll) {
                        Icon(
                            imageVector = if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Toggle select all",
                            tint = if (isAllSelected) Primary else Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "$selectedCount selected",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (context) {
                    SelectionContext.LOCAL -> {
                        ActionButton(icon = Icons.Default.Delete, tint = Color.Red, onClick = onDelete)
                        ActionButton(icon = Icons.Default.QueueMusic, onClick = onAddToQueue)
                        ActionButton(icon = Icons.Default.PlaylistAdd, onClick = onAddToPlaylist)
                        ActionButton(icon = Icons.Default.Share, onClick = onShare)
                    }
                    SelectionContext.LOCAL_PLAYLIST -> {
                        ActionButton(icon = Icons.Default.RemoveCircleOutline, tint = Color.Red, onClick = onRemove)
                        ActionButton(icon = Icons.Default.QueueMusic, onClick = onAddToQueue)
                        ActionButton(icon = Icons.Default.PlaylistAdd, onClick = onAddToPlaylist)
                        ActionButton(icon = Icons.Default.Share, onClick = onShare)
                    }
                    SelectionContext.REMOTE -> {
                        ActionButton(icon = Icons.Default.Download, tint = Primary, onClick = onDownload)
                        ActionButton(icon = Icons.Default.QueueMusic, onClick = onAddToQueue)
                        ActionButton(icon = Icons.Default.PlaylistAdd, onClick = onAddToPlaylist)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
