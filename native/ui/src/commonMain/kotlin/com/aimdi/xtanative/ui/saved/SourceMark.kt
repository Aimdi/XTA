package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.store.saved.SavedSource

/** The network's mark, so the picker reads at a glance: X's own, a globe for all, a letter for the others. */
@Composable
fun SourceMark(source: SavedSource, size: Int) {
    when (source) {
        SavedSource.ALL -> Icon(SavedIcons.Public, contentDescription = null, modifier = Modifier.size(size.dp))
        SavedSource.X -> Icon(SavedIcons.XMark, contentDescription = null, modifier = Modifier.size(size.dp))
        SavedSource.OTHER -> Icon(SavedIcons.Extension, contentDescription = null, modifier = Modifier.size(size.dp))
        else -> Box(
            Modifier.size(size.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(sourceInitial(source), fontSize = (size * 0.6f).sp, fontWeight = FontWeight.Bold, lineHeight = (size * 0.6f).sp)
        }
    }
}

private fun sourceInitial(source: SavedSource): String = when (source) {
    SavedSource.REDDIT -> "r"
    SavedSource.MASTODON -> "M"
    SavedSource.BLUESKY -> "B"
    SavedSource.THREADS -> "@"
    else -> ""
}
