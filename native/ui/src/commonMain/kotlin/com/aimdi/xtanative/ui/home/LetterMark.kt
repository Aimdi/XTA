package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/** One letter on a disc; the letter keeps its size whatever the text size, or it would leave the disc (as in XTA). */
@Composable
internal fun LetterMark(letter: String, colour: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(colour), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() }
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
    }
}
