package com.crowstar.deeztrackermobile.ui.components

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.TextGray

@Composable
fun AlphabeticalFastScroller(
    modifier: Modifier = Modifier,
    selectedLetter: Char? = null,
    onLetterSelected: (Char) -> Unit
) {
    val letters = remember { listOf('#') + ('A'..'Z').toList() }
    var currentlySelectedLetter by remember { mutableStateOf<Char?>(null) }
    
    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val index = (offset.y / size.height * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        currentlySelectedLetter = letter
                        onLetterSelected(letter)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val offset = change.position
                        val index = (offset.y / size.height * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        if (letter != currentlySelectedLetter) {
                            currentlySelectedLetter = letter
                            onLetterSelected(letter)
                        }
                    },
                    onDragEnd = {
                        currentlySelectedLetter = null
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                val isSelected = letter == (currentlySelectedLetter ?: selectedLetter)
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 18.dp else 14.dp)
                        .clickable { onLetterSelected(letter) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        color = if (isSelected) Primary else TextGray,
                        fontSize = if (isSelected) 12.sp else 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
