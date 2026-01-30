package com.crowstar.deeztrackermobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark

@Composable
fun AlphabeticalFastScroller(
    modifier: Modifier = Modifier,
    selectedLetter: Char? = null,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onLetterSelected: (Char) -> Unit
) {
    val letters = remember { listOf('#') + ('A'..'Z').toList() }
    var currentlySelectedLetter by remember { mutableStateOf<Char?>(null) }
    var lastSelectedLetter by remember { mutableStateOf<Char?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Animate opacity instead of visibility to avoid size changes
    val popupAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "popupAlpha"
    )
    
    // Fixed width container to keep scroller static
    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(bottom = bottomInset)
    ) {
        // Preview popup - positioned outside the scroller, perfectly centered
        if (popupAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-100).dp)
                    .requiredSize(80.dp) // Force exact square size
                    .graphicsLayer { alpha = popupAlpha }
                    .background(Primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (currentlySelectedLetter ?: lastSelectedLetter)?.toString() ?: "",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // The actual fast scroller bar with letters
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isDragging = true
                        },
                        onDragEnd = { 
                            isDragging = false 
                        },
                        onDragCancel = { 
                            isDragging = false 
                        }
                    ) { change, _ ->
                        change.consume()
                        val totalHeight = size.height.toFloat()
                        val yPos = change.position.y.coerceIn(0f, totalHeight)
                        val letterIndex = ((yPos / totalHeight) * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val targetLetter = letters[letterIndex]
                        currentlySelectedLetter = targetLetter
                        lastSelectedLetter = targetLetter
                        onLetterSelected(targetLetter)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEach { letter ->
                    val isSelected = letter == (currentlySelectedLetter ?: selectedLetter)
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 16.dp else 12.dp)
                            .clickable { onLetterSelected(letter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter.toString(),
                            color = if (isSelected) Primary else TextGray,
                            fontSize = if (isSelected) 11.sp else 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
