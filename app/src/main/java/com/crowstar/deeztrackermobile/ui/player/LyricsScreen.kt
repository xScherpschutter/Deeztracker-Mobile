package com.crowstar.deeztrackermobile.ui.player

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.lyrics.LrcLine
import com.crowstar.deeztrackermobile.features.player.LyricMode
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun LyricsScreen(
    lyrics: List<LrcLine>,
    currentIndex: Int,
    isLoading: Boolean,
    onLineClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val lyricMode = remember {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("lyric_mode", LyricMode.CLASSIC.name)
        try { LyricMode.valueOf(saved ?: LyricMode.CLASSIC.name) } catch (e: Exception) { LyricMode.CLASSIC }
    }
    
    val isSynchronized = lyrics.isNotEmpty() && lyrics[0].timeMs != Long.MAX_VALUE
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (isSynchronized && currentIndex >= 0 && currentIndex < lyrics.size) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -300
            )
        }
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.crowstar.deeztrackermobile.R.string.lyrics_loading),
                        color = TextGray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        lyrics.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.crowstar.deeztrackermobile.R.string.lyrics_not_available),
                    color = TextGray,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        lyricMode == LyricMode.SINGLE_LINE && isSynchronized -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (currentIndex >= 0 && currentIndex < lyrics.size) {
                    Text(
                        text = lyrics[currentIndex].text,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineHeight = 38.sp
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 100.dp, horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == currentIndex
                    val isPast = index < currentIndex
                    
                    val blurValue by animateFloatAsState(
                        targetValue = when {
                            !isSynchronized || isActive || lyricMode == LyricMode.NORMAL -> 0f
                            else -> 8f
                        },
                        animationSpec = tween(durationMillis = 800),
                        label = "blur"
                    )
                    
                    val alphaValue by animateFloatAsState(
                        targetValue = when {
                            !isSynchronized || isActive -> 1f
                            lyricMode == LyricMode.NORMAL -> 0.7f
                            lyricMode == LyricMode.FADE && isPast -> 0f // "Dust" effect: completely gone
                            else -> 0.4f
                        },
                        animationSpec = tween(durationMillis = 800),
                        label = "alpha"
                    )

                    val scaleValue by animateFloatAsState(
                        targetValue = when {
                            isActive -> 1.05f
                            lyricMode == LyricMode.FADE && isPast -> 0.9f // Shrink slightly as it fades
                            else -> 1f
                        },
                        animationSpec = tween(durationMillis = 800),
                        label = "scale"
                    )

                    // Vertical offset for "floating away" effect in Fade mode
                    val yOffset by animateFloatAsState(
                        targetValue = if (lyricMode == LyricMode.FADE && isPast) -20f else 0f,
                        animationSpec = tween(durationMillis = 1000),
                        label = "yOffset"
                    )

                    Text(
                        text = line.text,
                        color = if (isActive) Color.White else TextGray,
                        fontSize = 22.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = yOffset
                                scaleX = scaleValue
                                scaleY = scaleValue
                                alpha = alphaValue
                            }
                            .let { if (blurValue > 0f) it.blur(blurValue.dp) else it }
                            .clickable(enabled = isSynchronized) { 
                                if (isSynchronized) onLineClick(line.timeMs) 
                            },
                        lineHeight = 32.sp
                    )
                }
            }
        }
    }
}
