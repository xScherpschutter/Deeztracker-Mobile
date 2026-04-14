package com.crowstar.deeztrackermobile.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.crowstar.deeztrackermobile.R

/**
 * A reusable component for displaying track artwork with a standardized fallback.
 * Optimized for high performance in lists.
 */
@Composable
fun TrackArtwork(
    model: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showPlaceholder: Boolean = true
) {
    val context = LocalContext.current
    val placeholder = if (showPlaceholder) painterResource(R.drawable.ic_app_icon) else null
    
    // Optimized: Request is remembered to avoid heavy reconstruction on recomposition
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .diskCacheKey(model?.toString()) // Explicit cache key
            .memoryCacheKey(model?.toString())
            .build()
    }
    
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder
    )
}

@Composable
fun DefaultTrackArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_app_icon),
        contentDescription = null,
        modifier = modifier.background(Color.Transparent),
        contentScale = ContentScale.Fit
    )
}
