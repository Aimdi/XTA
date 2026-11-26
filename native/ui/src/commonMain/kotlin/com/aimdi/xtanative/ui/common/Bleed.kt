package com.aimdi.xtanative.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** Laid out [by] wider on either side than the space given, reaching into the margin round it (a dialog's, a page's padding). */
internal fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val wider = if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra) else constraints
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}
