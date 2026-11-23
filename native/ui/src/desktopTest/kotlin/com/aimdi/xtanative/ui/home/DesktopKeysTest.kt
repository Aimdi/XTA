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

    @Test
    fun ctrlFAndSlashGoToTheSearch() {
        assertEquals(DeskAction.FIND, action(DeskKey.F, ctrl = true))
        assertEquals(DeskAction.FIND, action(DeskKey.F, ctrl = true, focusInside = true), "Ctrl+F from another field")
        assertEquals(DeskAction.FIND, action(DeskKey.F, ctrl = true, canGoBack = false), "on the first screen too")
        assertEquals(DeskAction.FIND, action(DeskKey.SLASH))
        assertEquals(DeskAction.FIND, action(DeskKey.SLASH, shift = true), "Shift+7 on a German keyboard")
    }

    @Test
    fun slashIsTypedInAFieldAndNeitherKeyReachesUnderWhatIsOpen() {
        assertEquals(DeskAction.NONE, action(DeskKey.SLASH, focusInside = true))
        assertEquals(DeskAction.NONE, action(DeskKey.SLASH, ctrl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.SLASH, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.F))
        assertEquals(DeskAction.NONE, action(DeskKey.F, ctrl = true, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.F, ctrl = true, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.F, ctrl = true, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.SLASH, open = true))
    }

    @Test
    fun theSearchShownLastTakesTheKeysAndOneGoneHandsThemToTheOneUnder() {
        val actions = DesktopActions()
        val found = mutableListOf<String>()
        assertFalse(actions.find(), "no field: the app's search opens instead")
        val page = Any()
        val layer = Any()
        actions.addFinder(page) { found += "page" }
        actions.addFinder(layer) { found += "layer" }
        assertTrue(actions.find())
        actions.removeFinder(layer)
        actions.find()
        actions.addFinder(page) { found += "page again" }
        actions.find()
        assertEquals(listOf("layer", "page", "page again"), found)
        actions.removeFinder(page)
        assertFalse(actions.find(), "a screen resumed twice is listed once")
    }

    @Test
    fun ctrlPlusMinusAndZeroSizeTheTextAsABrowsersZoom() {
        assertEquals(DeskAction.TEXT_BIGGER, action(DeskKey.PLUS, ctrl = true))
        assertEquals(DeskAction.TEXT_BIGGER, action(DeskKey.PLUS, ctrl = true, shift = true), "Ctrl+Shift+= on a US keyboard")
        assertEquals(DeskAction.TEXT_SMALLER, action(DeskKey.MINUS, ctrl = true, focusInside = true), "from a field too")
        assertEquals(DeskAction.TEXT_RESET, action(DeskKey.ZERO, ctrl = true, canGoBack = false))
        listOf(DeskKey.PLUS, DeskKey.MINUS, DeskKey.ZERO).forEach { key ->
            assertEquals(DeskAction.NONE, action(key), "$key alone is typed")
            assertEquals(DeskAction.NONE, action(key, ctrl = true, alt = true), "Ctrl+Alt+$key")
        }
    }

    @Test
    fun theTextSizeMovesOnTheSlidersStepsWithinItsRange() {
        val range = 1.0..1.5
        assertEquals(1.05, DesktopKeys.textScaled(1.0, DeskAction.TEXT_BIGGER, range))
        assertEquals(1.15, DesktopKeys.textScaled(1.1, DeskAction.TEXT_BIGGER, range))
        assertEquals(1.5, DesktopKeys.textScaled(1.5, DeskAction.TEXT_BIGGER, range), "never above the slider's end")
        assertEquals(1.0, DesktopKeys.textScaled(1.0, DeskAction.TEXT_SMALLER, range), "never below the system's size")
        assertEquals(1.25, DesktopKeys.textScaled(1.3, DeskAction.TEXT_SMALLER, range))
        assertEquals(1.1, DesktopKeys.textScaled(1.07, DeskAction.TEXT_BIGGER, range), "a size between steps goes onto one")
        assertEquals(1.0, DesktopKeys.textScaled(1.35, DeskAction.TEXT_RESET, range))
        assertEquals(1.35, DesktopKeys.textScaled(1.35, DeskAction.REFRESH, range))
    }

    @Test
    fun theSearchOpensOverTheAppsScreensOnly() {
        assertTrue(com.aimdi.xtanative.ui.SearchKeys.opensSearchOver("home"))
        listOf("intro", "import", "search?q={q}&tab={tab}&focus={focus}", "plugin/rss", "plugin/rss/search?q={q}", "plugin-media/{key}/{index}", null).forEach { route ->
            assertFalse(com.aimdi.xtanative.ui.SearchKeys.opensSearchOver(route), "over $route")
        }
    }

    @Test
    fun f1AndAQuestionMarkListTheKeys() {
        assertEquals(DeskAction.SHOW_KEYS, action(DeskKey.F1))
        assertEquals(DeskAction.SHOW_KEYS, action(DeskKey.F1, focusInside = true), "F1 types nothing, so a field keeps no claim to it")
        assertEquals(DeskAction.SHOW_KEYS, action(DeskKey.QUESTION, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.QUESTION, focusInside = true), "a field types its ?")
        assertEquals(DeskAction.NONE, action(DeskKey.F1, open = true), "not over a sheet")
        assertEquals(DeskAction.NONE, action(DeskKey.F1, ctrl = true))
    }

    @Test
    fun everyShortcutListedHasKeys() {
        assertTrue(DesktopShortcuts.all.isNotEmpty())
        DesktopShortcuts.all.forEach { shortcut -> assertTrue(shortcut.keys.isNotEmpty() && shortcut.keys.all { it.isNotEmpty() }, "${shortcut.does}") }
        assertEquals(DesktopShortcuts.all.size, DesktopShortcuts.all.map { it.does }.distinct().size)
    }
}
