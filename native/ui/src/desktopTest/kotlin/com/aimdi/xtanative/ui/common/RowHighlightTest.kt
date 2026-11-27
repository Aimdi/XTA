package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class RowHighlightTest {
    @Test
    fun aWideRowIsLitAsARoundedBlockInsetFromItsEdges() {
        // A settings row: 600 × 56 dp.
        assertEquals(RowHighlight.Block(8f, 4f, 584f, 48f, 8f), RowHighlight.block(600f, 56f))
    }

    @Test
    fun aNarrowRowIsInsetAboveAndBelowButNotAtItsSides() {
        assertEquals(RowHighlight.Block(0f, 4f, 90f, 40f, 8f), RowHighlight.block(90f, 48f))
    }

    @Test
    fun aTabIsInsetAtItsSidesTooThoughNarrowerThanARow() {
        // A search page's tab: 130 × 48 dp, five to a column.
        assertEquals(RowHighlight.Block(8f, 4f, 114f, 40f, 8f), RowHighlight.block(130f, 48f, tab = true))
        assertEquals(RowHighlight.Block(0f, 4f, 40f, 40f, 8f), RowHighlight.block(40f, 48f, tab = true))
    }

    @Test
    fun aChipOrAnAvatarIsLitInItsOwnBoundsWithCornersThatFit() {
        assertEquals(RowHighlight.Block(0f, 0f, 70f, 32f, 8f), RowHighlight.block(70f, 32f))
        assertEquals(RowHighlight.Block(0f, 0f, 12f, 12f, 6f), RowHighlight.block(12f, 12f))
    }

    @Test
    fun nothingIsLeftOfARowNarrowerThanItsInsets() {
        assertEquals(0f, RowHighlight.block(0f, 0f).width)
    }

    @Test
    fun aPressWashesMoreThanThePointerAndAFocusAloneWashesNothing() {
        assertEquals(RowHighlight.PRESS, RowHighlight.alpha(hovered = true, pressed = true))
        assertEquals(RowHighlight.HOVER, RowHighlight.alpha(hovered = true, pressed = false))
        assertEquals(0f, RowHighlight.alpha(hovered = false, pressed = false))
    }

    @Test
    fun aFramedBlockIsLitOnItsOwnEdgeWithItsCorners() {
        assertEquals(RowHighlight.Block(0f, 0f, 600f, 120f, 16f), RowHighlight.framed(600f, 120f, 16f))
        assertEquals(RowHighlight.Block(0f, 0f, 20f, 10f, 5f), RowHighlight.framed(20f, 10f, 16f))
    }
}
