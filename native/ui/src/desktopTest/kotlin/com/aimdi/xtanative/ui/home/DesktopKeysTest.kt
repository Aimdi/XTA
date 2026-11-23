package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's desktop shortcuts (lib/ui/desktop_navigation.dart, desktop_pager.dart) and the avatar's monogram (fallback_avatar.dart). */
class DesktopKeysTest {
    private fun action(
        key: DeskKey,
        alt: Boolean = false,
        ctrl: Boolean = false,
        focusInside: Boolean = false,
        canGoBack: Boolean = true,
        rtl: Boolean = false,
        open: Boolean = false,
        shift: Boolean = false,
    ) = DesktopKeys.action(key, alt, ctrl, focusInside, canGoBack, rtl, open, shift)

    @Test
    fun theReadingKeysMoveTheListAsInABrowser() {
        assertEquals(DeskAction.LINE_DOWN, action(DeskKey.DOWN))
        assertEquals(DeskAction.LINE_UP, action(DeskKey.UP))
        assertEquals(DeskAction.SCREEN_DOWN, action(DeskKey.PAGE_DOWN))
        assertEquals(DeskAction.SCREEN_UP, action(DeskKey.PAGE_UP))
        assertEquals(DeskAction.SCREEN_DOWN, action(DeskKey.SPACE))
        assertEquals(DeskAction.SCREEN_UP, action(DeskKey.SPACE, shift = true))
        assertEquals(DeskAction.TO_TOP, action(DeskKey.HOME))
        assertEquals(DeskAction.TO_END, action(DeskKey.END))
    }

    @Test
    fun theReadingKeysLeaveFieldsAndWhatIsOpenAlone() {
        DeskKey.entries.filter { it in setOf(DeskKey.UP, DeskKey.DOWN, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN, DeskKey.SPACE, DeskKey.HOME, DeskKey.END) }.forEach { key ->
            assertEquals(DeskAction.NONE, action(key, focusInside = true), "$key while typing")
            assertEquals(DeskAction.NONE, action(key, open = true), "$key with a sheet open")
            if (key != DeskKey.PAGE_UP && key != DeskKey.PAGE_DOWN) assertEquals(DeskAction.NONE, action(key, ctrl = true), "Ctrl+$key")
            assertEquals(DeskAction.NONE, action(key, alt = true), "Alt+$key")
        }
    }

    @Test
    fun escLeavesTheFieldFirstThenClosesThenGoesBack() {
        assertEquals(DeskAction.LEAVE_FIELD, action(DeskKey.ESCAPE, focusInside = true, open = true))
        assertEquals(DeskAction.CLOSE, action(DeskKey.ESCAPE, open = true, canGoBack = false))
        assertEquals(DeskAction.BACK, action(DeskKey.ESCAPE))
        assertEquals(DeskAction.NONE, action(DeskKey.ESCAPE, canGoBack = false))
    }

    @Test
    fun theMouseBackButtonClosesOrGoesBack() {
        assertEquals(DeskAction.CLOSE, DesktopKeys.back(canGoBack = true, somethingOpen = true))
        assertEquals(DeskAction.BACK, DesktopKeys.back(canGoBack = true, somethingOpen = false))
        assertEquals(DeskAction.NONE, DesktopKeys.back(canGoBack = false, somethingOpen = false))
    }

    @Test
    fun onTheFirstScreenTheMouseBackButtonClosesTheAppAndTheKeysDoNothing() {
        assertEquals(DeskAction.LEAVE_APP, DesktopKeys.mouseBack(canGoBack = false, somethingOpen = false))
        assertEquals(DeskAction.CLOSE, DesktopKeys.mouseBack(canGoBack = false, somethingOpen = true))
        assertEquals(DeskAction.BACK, DesktopKeys.mouseBack(canGoBack = true, somethingOpen = false))
        assertEquals(DeskAction.NONE, action(DeskKey.ESCAPE, canGoBack = false))
    }

    @Test
    fun altLeftAndTheBackKeyGoBackFromAnywhere() {
        assertEquals(DeskAction.BACK, action(DeskKey.LEFT, alt = true, focusInside = true))
        assertEquals(DeskAction.BACK, action(DeskKey.BACK, focusInside = true))
        assertEquals(DeskAction.NONE, action(DeskKey.BACK, canGoBack = false))
    }

    @Test
    fun f5AndCtrlRRefresh() {
        assertEquals(DeskAction.REFRESH, action(DeskKey.F5))
        assertEquals(DeskAction.REFRESH, action(DeskKey.R, ctrl = true, focusInside = true))
        assertEquals(DeskAction.NONE, action(DeskKey.R))
    }

    @Test
    fun arrowsTurnPagesOnlyWhenNothingTakesThem() {
        assertEquals(DeskAction.PREVIOUS_PAGE, action(DeskKey.LEFT))
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.RIGHT))
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.LEFT, rtl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, focusInside = true))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, ctrl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.OTHER))
    }

    @Test
    fun ctrlTabAndCtrlPageKeysTurnPagesAsABrowserTurnsTabs() {
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.TAB, ctrl = true))
        assertEquals(DeskAction.PREVIOUS_PAGE, action(DeskKey.TAB, ctrl = true, shift = true))
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.PAGE_DOWN, ctrl = true))
        assertEquals(DeskAction.PREVIOUS_PAGE, action(DeskKey.PAGE_UP, ctrl = true))
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.TAB, ctrl = true, focusInside = true), "from a field too")
        assertEquals(DeskAction.NEXT_PAGE, action(DeskKey.TAB, ctrl = true, rtl = true), "tabs keep their order whichever way text reads")
        assertEquals(DeskAction.NONE, action(DeskKey.TAB, ctrl = true, open = true), "not under a sheet")
        assertEquals(DeskAction.NONE, action(DeskKey.TAB, focusInside = true), "plain Tab inside the page moves the focus on")
        assertEquals(DeskAction.NONE, action(DeskKey.TAB, ctrl = true, alt = true))
    }

    @Test
    fun tabFromTheWindowsFrameGoesIntoThePage() {
        assertEquals(DeskAction.ENTER_PAGE, action(DeskKey.TAB))
        assertEquals(DeskAction.ENTER_PAGE, action(DeskKey.TAB, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.TAB, focusInside = true), "inside, Tab is the focus system's")
        assertEquals(DeskAction.NONE, action(DeskKey.TAB, alt = true))
    }

    @Test
    fun ctrlQClosesTheAppFromAnywhere() {
        assertEquals(DeskAction.LEAVE_APP, action(DeskKey.Q, ctrl = true))
        assertEquals(DeskAction.LEAVE_APP, action(DeskKey.Q, ctrl = true, focusInside = true, open = true), "from a field or a sheet too")
        assertEquals(DeskAction.NONE, action(DeskKey.Q), "a plain Q is typed or ignored")
        assertEquals(DeskAction.NONE, action(DeskKey.Q, ctrl = true, alt = true))
    }

    @Test
    fun pagesDoNotWrap() {
        assertEquals(1, DesktopKeys.turned(0, 3, 1))
        assertNull(DesktopKeys.turned(0, 3, -1))
        assertNull(DesktopKeys.turned(2, 3, 1))
    }

    @Test
    fun topBarPinsKeepTheirOrder() {
        assertEquals(listOf("a", "b"), TopBarPins.toggled(listOf("a"), "b"))
        assertEquals(listOf("b"), TopBarPins.toggled(listOf("a", "b"), "a"))
        assertEquals(listOf("c", "a"), TopBarPins.shown(listOf("a", "b", "c"), listOf("c", "gone", "a", "c")) { it })
    }

    @Test
    fun theMonogramIsXtas() {
        assertEquals("Q", Monogram.initial("quax_tests", "1"))
        assertEquals("É", Monogram.initial("_émile", "1"))
        assertEquals("7", Monogram.initial("___", "7x"))
        assertEquals("#", Monogram.initial("", "_"))
        // XTA's hash of "abc": 97, then 98 + 97 * 31, then 99 + 3105 * 31.
        assertEquals(96354 % 16, Monogram.index("abc", 16))
        assertEquals(0, Monogram.index("", 16))
        assertEquals(Monogram.palette[Monogram.index("mock", Monogram.palette.size)], Monogram.colour("mock"))
        // A long seed overflows 64 bits as Dart's ints do and still lands in the palette.
        assertEquals(Monogram.index("x".repeat(500), 16), Monogram.index("x".repeat(500), 16).coerceIn(0, 15))
    }

    @Test
    fun aSheetOverAnotherHandsTheKeysBackOnlyWhenTheLastGoes() {
        var refocused = 0
        val actions = DesktopActions(refocus = { refocused++ })
        actions.sheetOpened()
        actions.sheetOpened()
        actions.sheetClosed()
        assertEquals(0, refocused, "the sheet under it keeps its field's focus")
        actions.sheetClosed()
        assertEquals(1, refocused)
        actions.sheetClosed()
        assertEquals(2, refocused, "never below none")
    }

    @Test
    fun aSheetSwappedForAnotherIsStillOpenWhenTheFrameWouldTakeTheKeys() {
        val actions = DesktopActions()
        actions.sheetOpened()
        actions.sheetClosed()
        assertFalse(actions.sheetOpen())
        actions.sheetOpened()
        assertTrue(actions.sheetOpen(), "the delayed refocus sees the new sheet and leaves the keys with it")
    }
}
