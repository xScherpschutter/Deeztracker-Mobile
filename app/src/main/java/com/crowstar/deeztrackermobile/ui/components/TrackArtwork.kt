package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.crowstar.deeztrackermobile.R
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray

/**
 * A reusable component for displaying track artwork with a standardized fallback.
 */
@Composable
fun TrackArtwork(
    model: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showPlaceholder: Boolean = true
) {
    if (model != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            error = if (showPlaceholder) {
                rememberTrackArtworkPlaceholder()
            } else null,
            placeholder = if (showPlaceholder) {
                rememberTrackArtworkPlaceholder()
            } else null
        )
    } else if (showPlaceholder) {
        DefaultTrackArtwork(modifier)
    }
}

@Composable
fun DefaultTrackArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_app_icon),
        contentDescription = null,
        modifier = modifier.background(Color.Transparent), // No background container
        contentScale = ContentScale.Fit
    )
}

// Coil Painter placeholder helper
@Composable
fun rememberTrackArtworkPlaceholder(): androidx.compose.ui.graphics.painter.Painter {
    return painterResource(R.drawable.ic_app_icon)
}

@Composable
fun TrackArtwork(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    coil.compose.SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            DefaultTrackArtwork(Modifier.fillMaxSize())
        },
        error = {
            DefaultTrackArtwork(Modifier.fillMaxSize())
        }
    )
}
