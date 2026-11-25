package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F6 between the page and the navigation beside it (the wide frame's column). */
class FrameRegionTest {
    private fun f6(shift: Boolean = false, focusInside: Boolean = false, open: Boolean = false, ctrl: Boolean = false, alt: Boolean = false) =
        DesktopKeys.action(DeskKey.F6, alt, ctrl, focusInside, canGoBack = true, somethingOpen = open, shift = shift)

    @Test
    fun f6AndShiftF6MoveBetweenTheNavigationAndThePage() {
        assertEquals(DeskAction.OTHER_REGION, f6())
        assertEquals(DeskAction.OTHER_REGION, f6(shift = true))
        assertEquals(DeskAction.OTHER_REGION, f6(focusInside = true), "from a field too, as in a browser")
        assertEquals(DeskAction.NONE, f6(open = true), "not past what is open over the page")
        assertEquals(DeskAction.NONE, f6(ctrl = true))
        assertEquals(DeskAction.NONE, f6(alt = true))
    }

    @Test
    fun theKeysGoIntoTheNavigationAndBackToThePage() {
        var holds = false
        var entered = 0
        var toPage = 0
        val actions = DesktopActions()
        assertFalse(actions.otherRegion { toPage++ }, "a window with no navigation beside its screens leaves the key")
        val region = FrameRegion(enter = { entered++; holds = true; true }, holds = { holds })
        actions.setRegion(region)
        assertTrue(actions.otherRegion { toPage++ })
        assertEquals(1 to 0, entered to toPage)
        assertTrue(actions.otherRegion { toPage++; holds = false })
        assertEquals(1 to 1, entered to toPage)
        actions.dropRegion(FrameRegion(enter = { true }, holds = { false }))
        assertTrue(actions.otherRegion { toPage++ }, "only its own owner takes it away")
        actions.dropRegion(region)
        assertFalse(actions.otherRegion { toPage++ })
    }
}
