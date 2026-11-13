package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_gif
import org.jetbrains.compose.resources.stringResource

/**
 * The small label in a video tile's bottom-start corner, as X's feed draws
 * it: the time ([timeMs]: what is left while it plays, else its length),
 * after a small ▶ when [play] says a tap is what starts it. Nothing when
 * there is neither. The tile itself tells a screen reader all of it.
 */
@Composable
internal fun TileCorner(timeMs: Long?, play: Boolean, modifier: Modifier = Modifier) {
    if (timeMs == null && !play) return
    CornerLabel(modifier) {
        if (play) Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        timeMs?.let { CornerText(VideoRules.timeText(it)) }
    }
}

/** "GIF" in a GIF tile's bottom-start corner, where a video's time would be. */
@Composable
internal fun GifCorner(modifier: Modifier = Modifier) {
    CornerLabel(modifier) { CornerText(stringResource(Res.string.reader_gif)) }
}

@Composable
private fun CornerLabel(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun CornerText(text: String) {
    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
}
