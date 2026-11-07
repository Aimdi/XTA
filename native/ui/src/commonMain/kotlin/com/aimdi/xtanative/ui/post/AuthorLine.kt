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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.XIcons
import kotlin.math.max
import kotlin.math.min

/**
 * How an author line shares its width (tweet_header.dart's TweetAuthorLine):
 * the time keeps its size up to what the name leaves, and never less than
 * [TIME_SHARE] of the line; the name and its badge come next; the handle
 * takes what is left, and is left out below [HANDLE_MIN] rather than cut to
 * an ellipsis alone. With text scaled to [TWO_LINES_SCALE] or more the handle
 * and time take a second line.
 */
object AuthorLineLayout {
    const val TIME_SHARE = 0.45f
    const val HANDLE_MIN = 40f
    const val TWO_LINES_SCALE = 1.3f

    /** The widths given to each piece; [handle] is 0 where the handle is left out. */
    data class Widths(val name: Float, val badge: Float, val handle: Float, val time: Float, val twoLines: Boolean)

    fun twoLines(fontScale: Float): Boolean = fontScale >= TWO_LINES_SCALE

    /**
     * The widths on a line [line] wide for pieces that would like [name],
     * [badge], [handle] and [time]; all in the same unit. On two lines the
     * name and badge share the first, the handle and time the second, the
     * time again keeping its size.
     */
    fun widths(line: Float, name: Float, badge: Float, handle: Float, time: Float, fontScale: Float = 1f): Widths {
        if (twoLines(fontScale)) {
            val nameRoom = max(0f, line - badge)
            val timeWidth = min(time, line)
            val handleRoom = max(0f, line - timeWidth)
            return Widths(min(name, nameRoom), badge, if (handleRoom < HANDLE_MIN) 0f else min(handle, handleRoom), timeWidth, twoLines = true)
        }
        val timeWidth = min(time, max(line * TIME_SHARE, line - (name + badge)))
        val rest = max(0f, line - timeWidth)
        val nameWidth = min(name, max(0f, rest - badge))
        val handleRoom = max(0f, rest - nameWidth - badge)
        val handleWidth = if (handleRoom < HANDLE_MIN) 0f else min(handle, handleRoom)
        return Widths(nameWidth, badge, handleWidth, timeWidth, twoLines = false)
    }
}

/**
 * "Name ✓ @handle · 3h" on one line, cut as X cuts it ([AuthorLineLayout]):
 * the time always shows, the name comes first, and the handle takes what is
 * left; with large text the handle and time go to a second line.
 */
@Composable
fun AuthorLine(name: String, handle: String, time: String, verified: Boolean, modifier: Modifier = Modifier) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    val fontScale = LocalDensity.current.fontScale
    val handleGap = 4.dp
    Layout(
        modifier = modifier,
        content = {
            Text(name, style = style, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (verified) {
                Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 2.dp).size(17.dp))
            } else {
                Spacer(Modifier)
            }
            Text("@$handle", style = style, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (time.isEmpty()) "" else " · $time", style = style, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedWidth
        val gap = handleGap.roundToPx()
        val badgePiece = measurables[1].measure(loose)
        val twoLines = AuthorLineLayout.twoLines(fontScale)
        val widths = if (bounded) {
            AuthorLineLayout.widths(
                constraints.maxWidth.toFloat() / density,
                measurables[0].maxIntrinsicWidth(Int.MAX_VALUE) / density,
                badgePiece.width / density,
                (measurables[2].maxIntrinsicWidth(Int.MAX_VALUE) + gap) / density,
                measurables[3].maxIntrinsicWidth(Int.MAX_VALUE) / density,
                fontScale,
            )
        } else {
            null
        }
        fun fit(width: Float?) = if (width == null) loose else loose.copy(maxWidth = (width * density).toInt().coerceAtLeast(0))
        val namePiece = measurables[0].measure(fit(widths?.name))
        val handleWidth = widths?.handle
        val showsHandle = handleWidth == null || handleWidth > 0f
        val handlePiece = measurables[2].measure(if (showsHandle) fit(handleWidth?.let { it - gap / density }) else loose.copy(maxWidth = 0))
        val timePiece = measurables[3].measure(fit(widths?.time))
        val handleSpan = if (showsHandle) handlePiece.width + gap else 0
        if (twoLines) {
            val first = max(namePiece.height, badgePiece.height)
            val second = max(handlePiece.height, timePiece.height)
            val used = max(namePiece.width + badgePiece.width, handlePiece.width + timePiece.width)
            layout(if (bounded) constraints.maxWidth else used, first + second) {
                namePiece.placeRelative(0, (first - namePiece.height) / 2)
                badgePiece.placeRelative(namePiece.width, (first - badgePiece.height) / 2)
                if (showsHandle) handlePiece.placeRelative(0, first + (second - handlePiece.height) / 2)
                timePiece.placeRelative(if (showsHandle) handlePiece.width else 0, first + (second - timePiece.height) / 2)
            }
        } else {
            val height = maxOf(namePiece.height, badgePiece.height, handlePiece.height, timePiece.height)
            val used = namePiece.width + badgePiece.width + handleSpan + timePiece.width
            layout(if (bounded) constraints.maxWidth else used, height) {
                var x = 0
                namePiece.placeRelative(x, (height - namePiece.height) / 2)
                x += namePiece.width
                badgePiece.placeRelative(x, (height - badgePiece.height) / 2)
                x += badgePiece.width
                if (showsHandle) {
                    handlePiece.placeRelative(x + gap, (height - handlePiece.height) / 2)
                    x += handleSpan
                }
                timePiece.placeRelative(x, (height - timePiece.height) / 2)
            }
        }
    }
}
