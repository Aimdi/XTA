package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.max
import kotlin.math.roundToInt

/** How long a count takes to roll to its new number. */
const val ROLLING_COUNT_MILLIS = 300

/** Whether a count moving from [from] to [to] rolls upwards: a rise, or a change whose numbers are not known (XTA's rollingCountRises). */
fun rollingCountRises(from: Long?, to: Long?): Boolean = from == null || to == null || to >= from

/**
 * The text a count rolls out of on its way to [to]: the one it showed
 * ([before]), when both are numbers shown and motion is not [reduced]; null
 * changes it at once (a count that appears or goes, or the same text).
 */
fun rollingCountFrom(before: String, to: String, reduced: Boolean): String? =
    before.takeIf { !reduced && it != to && it.isNotEmpty() && to.isNotEmpty() }

/** What a [RollingCount] showed when its last composition was applied: written only then, read by the next. */
private class Shown(var text: String, var value: Long?)

/**
 * A count that rolls to its new number as X's does (XTA's RollingCount): the
 * old text slides out of its line while the new one slides in, upwards for a
 * rise and downwards for a fall, the line's width moving between theirs. Only
 * a change between two shown numbers rolls; a count that appears or goes,
 * and every change with reduced motion, changes at once. [value] is the
 * number [text] shows, which picks the direction. The roll's progress is
 * read where the line is measured and placed, so its frames lay the line out
 * again without composing it again.
 */
@Composable
fun RollingCount(text: String, value: Long?, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val shown = remember { Shown(text, value) }
    // Worked out once per new text from what was last shown; what is shown is only written once the composition is applied.
    val from = remember(text) { rollingCountFrom(shown.text, text, reduced) }
    val rises = remember(text) { rollingCountRises(shown.value, value) }
    SideEffect {
        shown.text = text
        shown.value = value
    }
    val progress = remember(text) { Animatable(if (from == null) 1f else 0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(ROLLING_COUNT_MILLIS, easing = EaseOutCubic)) }
    val done by remember(progress) { derivedStateOf { progress.value >= 1f } }
    if (from == null || done) return Text(text, modifier, color = color, style = style, maxLines = 1)
    Layout(
        content = {
            Text(from, color = color, style = style, maxLines = 1)
            Text(text, color = color, style = style, maxLines = 1)
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val (old, new) = measurables.map { it.measure(free) }
        val height = max(old.height, new.height)
        val t = progress.value
        val width = (old.width + (new.width - old.width) * t).roundToInt()
        val travel = if (rises) -height else height
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            old.placeWithLayer(0, (travel * t).roundToInt()) { alpha = 1 - t }
            new.placeWithLayer(0, (-travel * (1 - t)).roundToInt()) { alpha = t }
        }
    }
}
