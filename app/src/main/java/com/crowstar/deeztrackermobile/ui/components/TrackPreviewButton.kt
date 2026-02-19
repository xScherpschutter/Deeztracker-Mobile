package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import com.crowstar.deeztrackermobile.ui.theme.Primary

private const val PREVIEW_DURATION_MS = 30_000L

/**
 * Option A — circular button with play/pause icon and a clockwise arc
 * that tracks the real ExoPlayer position (via [PreviewPlayer.positionMs]).
 *
 * Hides itself if [previewUrl] is null or blank.
 */
@Composable
fun TrackPreviewButton(
    previewUrl: String?,
    modifier: Modifier = Modifier
) {
    if (previewUrl.isNullOrBlank()) return

    val playingUrl by PreviewPlayer.playingUrl.collectAsState()
    val positionMs by PreviewPlayer.positionMs.collectAsState()

    val isThisPlaying = playingUrl == previewUrl

    // Real progress: only use position if this track is the one playing
    val progress = if (isThisPlaying) {
        (positionMs.toFloat() / PREVIEW_DURATION_MS).coerceIn(0f, 1f)
    } else {
        0f
    }

    val arcColor = Primary
    val trackColor = Color.White.copy(alpha = 0.12f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // Background track circle
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Progress arc — follows real playback position
                if (progress > 0f) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
    ) {
        IconButton(
            onClick = { PreviewPlayer.toggle(previewUrl) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Stop preview" else "Play preview",
                tint = if (isThisPlaying) Primary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
