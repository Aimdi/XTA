package com.aimdi.xtanative.ui.saved

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

/** The notice at the window's foot centres under the screens, not under the navigation beside them. */
class NoticePlaceTest {
    @Test
    fun aNoticeCentresAcrossTheScreensBesideTheNavigation() {
        // Screens from 300 to 1600, a notice 500 wide: centred at 950.
        assertEquals(700, NoticePlace.x(areaLeft = 300, areaWidth = 1300, windowWidth = 1600, width = 500))
    }

    @Test
    fun withoutNavigationItCentresAcrossTheWindow() {
        assertEquals(550, NoticePlace.x(areaLeft = 0, areaWidth = 1600, windowWidth = 1600, width = 500))
    }

    @Test
    fun anAreaNotLaidOutYetIsTheWholeWindow() {
        assertEquals(550, NoticePlace.x(areaLeft = 0, areaWidth = 0, windowWidth = 1600, width = 500))
    }

    @Test
    fun aNoticeStaysInsideTheWindow() {
        assertEquals(400, NoticePlace.x(areaLeft = 900, areaWidth = 700, windowWidth = 1000, width = 600))
        assertEquals(0, NoticePlace.x(areaLeft = 0, areaWidth = 300, windowWidth = 300, width = 500))
    }

    @Test
    fun aNoticeCentresUnderTheColumnAScreenClaimedElseUnderThePage() {
        val page = IntRect(300, 0, 1600, 900)
        val column = IntRect(300, 0, 950, 900)
        assertEquals(column, NoticePlace.area(column, page))
        assertEquals(page, NoticePlace.area(null, page))
        assertEquals(page, NoticePlace.area(IntRect(300, 0, 300, 900), page), "not laid out yet")
    }
}
