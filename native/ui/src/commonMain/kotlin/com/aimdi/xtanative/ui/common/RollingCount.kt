package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** What a [RollingCount] remembers between compositions: the text it showed, and the roll under way. */
private class Roll(var text: String, var value: Long?) {
    var from: String? = null
    var rises = true
    var generation = 0
    var started = 0
}

/**
 * A count that rolls to its new number as X's does (XTA's RollingCount): the
 * old text slides out of its line while the new one slides in, upwards for a
 * rise and downwards for a fall, the line's width moving between theirs. Only
 * a change between two shown numbers rolls; a count that appears or goes,
 * and every change with reduced motion, changes at once. [value] is the
 * number [text] shows, which picks the direction.
 */
@Composable
fun RollingCount(text: String, value: Long?, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val roll = remember { Roll(text, value) }
    if (roll.text != text) {
        roll.from = roll.text.takeIf { !reduced && it.isNotEmpty() && text.isNotEmpty() }
        roll.rises = rollingCountRises(roll.value, value)
        roll.text = text
        roll.value = value
        roll.generation++
    }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(roll.generation) {
        if (roll.from == null) return@LaunchedEffect
        progress.snapTo(0f)
        roll.started = roll.generation
        progress.animateTo(1f, tween(ROLLING_COUNT_MILLIS, easing = EaseOutCubic))
        roll.from = null
    }
    val from = roll.from
    val t = if (roll.started != roll.generation) 0f else progress.value
    if (from == null || t >= 1f) return Text(text, modifier, color = color, style = style, maxLines = 1)
    val rises = roll.rises
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
        val width = (old.width + (new.width - old.width) * t).roundToInt()
        val travel = if (rises) -height else height
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            old.placeWithLayer(0, (travel * t).roundToInt()) { alpha = 1 - t }
            new.placeWithLayer(0, (-travel * (1 - t)).roundToInt()) { alpha = t }
        }
    }
}
