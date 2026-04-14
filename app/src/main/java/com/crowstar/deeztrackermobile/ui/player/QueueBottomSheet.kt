package com.crowstar.deeztrackermobile.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.common.TrackArtwork
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<LocalTrack>,
    currentTrack: LocalTrack?,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_queue),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${queue.size} ${stringResource(R.string.label_tracks)}",
                    fontSize = 14.sp,
                    color = TextGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.playlist_empty), color = TextGray)
                }
            } else {
                val listState = rememberLazyListState()
                var draggedItemId by remember { mutableStateOf<Long?>(null) }
                
                val fingerY = remember { mutableFloatStateOf(0f) }
                val touchOffsetWithinItem = remember { mutableFloatStateOf(0f) }

                // Swap Logic
                fun performSwap(id: Long, isScrollingDown: Boolean?): Boolean {
                    val info = listState.layoutInfo
                    val draggedItem = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return false
                    val draggedCenter = fingerY.floatValue - touchOffsetWithinItem.floatValue + draggedItem.size / 2f

                    val targetItem = if (isScrollingDown != false) {
                        info.visibleItemsInfo.findLast { 
                            it.key != id && it.index > draggedItem.index && draggedCenter > it.offset + it.size / 2f 
                        }
                    } else {
                        info.visibleItemsInfo.find { 
                            it.key != id && it.index < draggedItem.index && draggedCenter < it.offset + it.size / 2f 
                        }
                    }

                    if (targetItem != null) {
                        onMoveTrack(draggedItem.index, targetItem.index)
                        return true
                    }
                    return false
                }

                // Auto-scroll
                LaunchedEffect(draggedItemId) {
                    if (draggedItemId == null) return@LaunchedEffect
                    var scrollStartTime = 0L
                    while (isActive && draggedItemId != null) {
                        val id = draggedItemId ?: break
                        val layoutInfo = listState.layoutInfo
                        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                        val fy = fingerY.floatValue
                        
                        val topLimit = 150f
                        val bottomLimit = viewportHeight - 150f

                        if (fy < topLimit || fy > bottomLimit) {
                            if (scrollStartTime == 0L) scrollStartTime = System.currentTimeMillis()
                            val elapsed = System.currentTimeMillis() - scrollStartTime
                            val accel = (1f + elapsed / 1000f).coerceAtMost(5f)
                            
                            val isDown = fy > bottomLimit
                            val amount = if (isDown) 10f * accel else -10f * accel
                            
                            listState.scrollBy(amount)
                            performSwap(id, isDown)
                            delay(10)
                        } else {
                            scrollStartTime = 0L
                            delay(25)
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                        ?.also {
                                            draggedItemId = it.key as? Long
                                            fingerY.floatValue = offset.y
                                            touchOffsetWithinItem.floatValue = offset.y - it.offset
                                        }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    fingerY.floatValue += dragAmount.y
                                    draggedItemId?.let { performSwap(it, dragAmount.y > 0) }
                                },
                                onDragEnd = { draggedItemId = null },
                                onDragCancel = { draggedItemId = null }
                            )
                        },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                        val isPlaying = track.id == currentTrack?.id
                        val isDragging = track.id == draggedItemId
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elev")

                        QueueItem(
                            track = track,
                            isPlaying = isPlaying,
                            isDragging = isDragging,
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }
                                        if (info != null) {
                                            translationY = fingerY.floatValue - touchOffsetWithinItem.floatValue - info.offset
                                        }
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        alpha = 0.95f
                                    }
                                }
                                .shadow(elevation, RoundedCornerShape(12.dp)),
                            onClick = { onTrackClick(index) },
                            onRemove = { onRemoveTrack(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItem(
    track: LocalTrack,
    isPlaying: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isDragging -> Color(0xFF2A2A2A)
                    isPlaying -> Primary.copy(alpha = 0.15f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            model = track.albumArtUri,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) Primary else Color.White,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove", tint = TextGray.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
            Icon(
                Icons.Default.DragHandle, "Drag",
                tint = if (isDragging) Primary else TextGray.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
