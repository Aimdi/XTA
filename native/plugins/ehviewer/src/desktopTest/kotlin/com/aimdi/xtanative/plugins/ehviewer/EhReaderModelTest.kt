package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.store.settings.EhReadingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EhReaderModelTest {
    private val settled = mutableListOf<Int>()
    private fun model(total: Int = 20, page: Int = 1, mode: EhReadingMode = EhReadingMode.LEFT_TO_RIGHT) =
        EhReaderModel(total, page, mode) { settled += it }

    @Test
    fun theReaderOpensWithinTheGalleryAndSettlesOnlyOnANewPage() {
        val reader = model(total = 10, page = 40)
        assertEquals(10, reader.state.value.page)

        reader.goTo(10)
        assertEquals(emptyList(), settled, "the same page settles nothing")
        reader.goTo(3)
        reader.goTo(-2)
        assertEquals(listOf(3, 1), settled)
    }

    @Test
    fun tapZonesTurnSmoothlyAndTheSliderJumps() {
        val reader = model()
        reader.next()
        assertEquals(2, reader.state.value.page)
        assertTrue(reader.state.value.smooth)

        reader.scrub(9)
        assertEquals(9, reader.state.value.shownPage)
        assertEquals(listOf(2), settled, "dragging the slider settles nothing")
        reader.goTo(9)
        assertNull(reader.state.value.scrubPage)
        assertFalse(reader.state.value.smooth)
        assertEquals(listOf(2, 9), settled)
    }

    @Test
    fun aPageTheViewMovedToIsNotJumpedToAgain() {
        val reader = model()
        val jump = reader.state.value.jump
        reader.observe(4)
        assertEquals(4, reader.state.value.page)
        assertEquals(jump, reader.state.value.jump)
        assertEquals(listOf(4), settled)
    }

    @Test
    fun anotherModeStaysOnThePageAndTheBarsToggle() {
        val reader = model(page = 5)
        val jump = reader.state.value.jump
        reader.setMode(EhReadingMode.VERTICAL)
        assertEquals(EhReadingMode.VERTICAL, reader.state.value.mode)
        assertEquals(5, reader.state.value.page)
        assertEquals(jump + 1, reader.state.value.jump)

        reader.tap(EhTap.CHROME)
        assertFalse(reader.state.value.chromeVisible)
    }

    @Test
    fun theOuterThirdsTurnPagesMirroredRightToLeft() {
        assertEquals(EhTap.PREVIOUS, EhReaderRules.tap(10f, 300f, EhReadingMode.LEFT_TO_RIGHT))
        assertEquals(EhTap.NEXT, EhReaderRules.tap(290f, 300f, EhReadingMode.LEFT_TO_RIGHT))
        assertEquals(EhTap.CHROME, EhReaderRules.tap(150f, 300f, EhReadingMode.LEFT_TO_RIGHT))
        assertEquals(EhTap.NEXT, EhReaderRules.tap(10f, 300f, EhReadingMode.RIGHT_TO_LEFT))
        assertEquals(EhTap.PREVIOUS, EhReaderRules.tap(290f, 300f, EhReadingMode.RIGHT_TO_LEFT))
        assertEquals(EhTap.NEXT, EhReaderRules.tap(290f, 300f, EhReadingMode.VERTICAL))
    }

    @Test
    fun theColumnIsOnItsTopPageOrTheLastOnceItShowsWhole() {
        assertEquals(3, EhReaderRules.visiblePage(3, lastShownWhole = false, total = 9))
        assertEquals(9, EhReaderRules.visiblePage(8, lastShownWhole = true, total = 9))
    }
}
