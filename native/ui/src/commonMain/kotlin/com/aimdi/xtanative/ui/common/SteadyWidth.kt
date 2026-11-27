package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * [content] laid out as wide as [widest] (drawn unseen and unread beneath
 * it), so a chip keeps its width when choosing it writes it bolder and the
 * row around it does not jump.
 */
@Composable
fun SteadyWidth(widest: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { widest() }
        content()
    }
}

/** A chip's [label], bold when [selected] and as wide as bold either way ([SteadyWidth]). */
@Composable
fun SteadyChipLabel(label: String, selected: Boolean, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified) {
    SteadyWidth({ Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1) }) {
        Text(label, color = color, fontSize = fontSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
