package com.crowstar.deeztrackermobile.ui.common.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side: Controls & Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.selection_close), tint = Color.White)
                }
                
                if (showSelectAll) {
                    IconButton(onClick = onToggleSelectAll) {
                        Icon(
                            imageVector = if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = stringResource(R.string.selection_toggle_all),
                            tint = if (isAllSelected) Primary else Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // The Badge: Compact numeric display
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$selectedCount",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            // Right Side: Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
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
            modifier = Modifier.size(24.dp)
        )
    }
}
