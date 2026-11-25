package com.aimdi.xtanative.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * A small button's hit area grown to at least [min] each way without moving
 * anything: [outer], then the clickable, then [inner]. [inner] lays the
 * button out larger by what is missing, around its glyph; [outer] reports
 * the glyph's own size to the row and places the larger box over it, so
 * the row keeps its look while a tap or click just beside the glyph (where
 * it opened the post under it before) lands on the button. What is drawn
 * after [inner] (its clip, ripple and glyph) keeps the glyph's size.
 */
internal class HitArea(private val min: Dp = MIN_HIT) {
    private var spareX = 0
    private var spareY = 0

    /** First in the chain, before the clickable. */
    val outer: Modifier = Modifier.layout { measurable, constraints ->
        val most = min.roundToPx()
        val placeable = measurable.measure(constraints.offset(most, most))
        layout(placeable.width - 2 * spareX, placeable.height - 2 * spareY) { placeable.place(-spareX, -spareY) }
    }

    /** After the clickable, before what is drawn. */
    val inner: Modifier = Modifier.layout { measurable, constraints ->
        val most = min.roundToPx()
        val placeable = measurable.measure(constraints.offset(-most, -most))
        spareX = HitAreas.spare(placeable.width, most)
        spareY = HitAreas.spare(placeable.height, most)
        layout(placeable.width + 2 * spareX, placeable.height + 2 * spareY) { placeable.place(spareX, spareY) }
    }

    companion object {
        /** The touch target Material asks for, and a comfortable one for a mouse. */
        val MIN_HIT = 48.dp
    }
}

/** The arithmetic of [HitArea]. */
internal object HitAreas {
    /** What a box [size] pixels long grows by on each side to be [min] long; nothing when it already is. */
    fun spare(size: Int, min: Int): Int = ((min - size) / 2).coerceAtLeast(0)
}
