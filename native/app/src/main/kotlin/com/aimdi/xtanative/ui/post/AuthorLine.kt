package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.XIcons

/**
 * "Name ✓ @handle · 3h" on one line, cut as X cuts it: the time always shows,
 * the name comes first, and the handle takes what is left.
 */
@Composable
fun AuthorLine(name: String, handle: String, time: String, verified: Boolean, modifier: Modifier = Modifier) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    Layout(
        modifier = modifier,
        content = {
            Text(name, style = style, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (verified) {
                Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 2.dp).size(17.dp))
            } else {
                Spacer(Modifier)
            }
            Text("@$handle", style = style, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            Text(if (time.isEmpty()) "" else " · $time", style = style, color = grey, maxLines = 1)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedWidth
        val timePiece = measurables[3].measure(loose)
        val badgePiece = measurables[1].measure(loose)
        val nameRoom = (constraints.maxWidth - timePiece.width - badgePiece.width).coerceAtLeast(0)
        val namePiece = measurables[0].measure(if (bounded) loose.copy(maxWidth = nameRoom) else loose)
        val handlePiece = measurables[2].measure(if (bounded) loose.copy(maxWidth = (nameRoom - namePiece.width).coerceAtLeast(0)) else loose)
        val placed = listOf(namePiece, badgePiece, handlePiece, timePiece)
        val height = placed.maxOf { it.height }
        val used = placed.sumOf { it.width }
        layout(if (bounded) constraints.maxWidth else used, height) {
            var x = 0
            placed.forEach { piece ->
                piece.placeRelative(x, (height - piece.height) / 2)
                x += piece.width
            }
        }
    }
}
