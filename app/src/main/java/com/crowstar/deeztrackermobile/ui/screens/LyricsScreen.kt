package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.lyrics.LrcLine
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun LyricsScreen(
    lyrics: List<LrcLine>,
    currentIndex: Int,
    onLineClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            // Scroll to center the active line roughly
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -300 // Offset to vertically center somewhat, adjustments might be needed
            )
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.crowstar.deeztrackermobile.R.string.lyrics_not_available),
                color = TextGray,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 50.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == currentIndex
                val alpha = if (isActive) 1f else 0.5f
                val scale = if (isActive) 1.1f else 1f
                val color = if (isActive) Color.White else TextGray
                val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                Text(
                    text = line.text,
                    color = color,
                    fontSize = 20.sp, // Reduced font size for better fit
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineClick(line.timeMs) },
                    lineHeight = 30.sp
                )
            }
        }
    }
}
