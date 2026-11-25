package com.aimdi.xtanative.ui.home

import androidx.compose.ui.focus.FocusRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F6 between the page and the navigation beside it (the wide frame's column or rail), and where the frame shows which. */
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

    @Test
    fun tabFromTheFrameGoesToThePageNotIntoTheNavigation() {
        assertEquals(DeskAction.ENTER_PAGE, DesktopKeys.action(DeskKey.TAB, alt = false, ctrl = false, focusInside = false, canGoBack = true, somethingOpen = false))
        val actions = DesktopActions()
        assertNull(actions.enterPage(), "no navigation beside the screens: Tab enters the window as before")
        var paged = 0
        var entered = 0
        actions.setRegion(FrameRegion(enter = { entered++; true }, holds = { false }, page = { paged++; true }))
        assertEquals(true, actions.enterPage())
        assertEquals(1 to 0, paged to entered, "the page, not the navigation")
        actions.setRegion(FrameRegion(enter = { true }, holds = { false }, page = { false }))
        assertEquals(false, actions.enterPage(), "a page with nothing to focus says so")
        actions.setRegion(FrameRegion(enter = { true }, holds = { false }))
        assertEquals(false, actions.enterPage())
    }

    @Test
    fun thePagesStartTakesTheFocusOnlyOnTheWayToThePage() {
        val focus = FrameFocus()
        var stepped = 0
        focus.move = { stepped++; true }
        assertFalse(focus.entering, "Tab's own walk passes it by")
        assertFalse(focus.toPage(), "nothing composed: nothing took the keys")
        assertEquals(0, stepped, "no step on from a start that took no focus")
        assertFalse(focus.entering, "and it takes none afterwards")
        assertFalse(focus.enter())
    }

    @Test
    fun whileTheRailsMenuHoldsTheKeysTheWindowLeavesThemToIt() {
        for (action in listOf(DeskAction.OTHER_REGION, DeskAction.ENTER_PAGE, DeskAction.CLOSE, DeskAction.LEAVE_FIELD, DeskAction.NEXT_COLUMN)) {
            assertFalse(DesktopKeys.windowActs(action, overlayHolds = true), "$action is the menu's")
            assertTrue(DesktopKeys.windowActs(action, overlayHolds = false))
        }
        assertTrue(DesktopKeys.windowActs(DeskAction.LEAVE_APP, overlayHolds = true), "Ctrl+Q still quits")
        val actions = DesktopActions()
        val menu = FocusRequester()
        actions.holdKeys(menu)
        assertTrue(actions.overlayHoldsKeys())
        actions.releaseKeys(menu)
        assertFalse(actions.overlayHoldsKeys(), "closed, it gives them back")
    }

    @Test
    fun aDesktopWindowHasTheColumnTheRailOrNothingBesideItsScreens() {
        assertEquals(FrameKind.NONE, HomeWide.frame(599f, rail = true))
        assertEquals(FrameKind.RAIL, HomeWide.frame(600f, rail = true))
        assertEquals(FrameKind.RAIL, HomeWide.frame(820f, rail = true))
        assertEquals(FrameKind.RAIL, HomeWide.frame(999f, rail = true))
        assertEquals(FrameKind.SIDEBAR, HomeWide.frame(1000f, rail = true))
        assertEquals(FrameKind.NONE, HomeWide.frame(820f, rail = false), "a tablet keeps its bottom bar")
        assertEquals(FrameKind.SIDEBAR, HomeWide.frame(1200f, rail = false))
    }

    @Test
    fun theScreensStandBesideWhatIsThere() {
        assertEquals(HomeWide.FRAME_INSET, HomeWide.inset(FrameKind.SIDEBAR))
        assertEquals(HomeWide.RAIL_WIDTH + 1, HomeWide.inset(FrameKind.RAIL))
        assertEquals(0, HomeWide.inset(FrameKind.NONE))
    }
}
