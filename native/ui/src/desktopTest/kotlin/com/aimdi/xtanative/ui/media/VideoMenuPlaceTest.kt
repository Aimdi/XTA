package com.aimdi.xtanative.ui.media

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the video's ⋮ menu stands on the desktop: above its button, its end at the button's end. */
class VideoMenuPlaceTest {
    private val place = VideoMenuPlace(margin = 8, gap = 4)
    private val window = IntSize(1600, 900)
    private val menu = IntSize(220, 200)

    @Test
    fun itStandsAboveTheButtonEndToEnd() {
        val button = IntRect(1500, 800, 1548, 848)
        assertEquals(IntOffset(1328, 596), place.calculatePosition(button, window, LayoutDirection.Ltr, menu))
    }

    @Test
    fun rightToLeftItsStartIsAtTheButtonsStart() {
        val button = IntRect(100, 800, 148, 848)
        assertEquals(IntOffset(100, 596), place.calculatePosition(button, window, LayoutDirection.Rtl, menu))
    }

    @Test
    fun withNoRoomAboveItGoesBelowAndStaysInTheWindow() {
        assertEquals(IntOffset(1328, 152), place.calculatePosition(IntRect(1500, 100, 1548, 148), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(8, 52), place.calculatePosition(IntRect(0, 0, 48, 48), window, LayoutDirection.Ltr, menu))
    }
}
