package com.crowstar.deeztrackermobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun PlaylistMosaic(covers: List<String?>, modifier: Modifier = Modifier) {
    val nonNullCovers = covers.filterNotNull()
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        when {
            nonNullCovers.isEmpty() -> {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextGray
                )
            }
            nonNullCovers.size == 1 -> {
                TrackArtwork(
                    model = nonNullCovers[0],
                    modifier = Modifier.fillMaxSize()
                )
            }
            nonNullCovers.size in 2..3 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    TrackArtwork(
                        model = nonNullCovers[0],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    TrackArtwork(
                        model = nonNullCovers[1],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        TrackArtwork(
                            model = nonNullCovers[0],
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        TrackArtwork(
                            model = nonNullCovers[1],
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        TrackArtwork(
                            model = nonNullCovers[2],
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        TrackArtwork(
                            model = nonNullCovers[3],
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}
