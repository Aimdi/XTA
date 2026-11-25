package com.aimdi.xtanative.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A hardware keyboard's reading keys off the desktop ([HardwareKeys], [onHardwareKey]). */
class HardwareKeysTest {
    @Test
    fun theReadingKeysMoveTheList() {
        assertEquals(DeskAction.LINE_UP, HardwareKeys.action(DeskKey.UP))
        assertEquals(DeskAction.LINE_DOWN, HardwareKeys.action(DeskKey.DOWN))
        assertEquals(DeskAction.SCREEN_UP, HardwareKeys.action(DeskKey.PAGE_UP))
        assertEquals(DeskAction.SCREEN_DOWN, HardwareKeys.action(DeskKey.PAGE_DOWN))
        assertEquals(DeskAction.SCREEN_DOWN, HardwareKeys.action(DeskKey.SPACE))
        assertEquals(DeskAction.SCREEN_UP, HardwareKeys.action(DeskKey.SPACE, shift = true))
        assertEquals(DeskAction.TO_TOP, HardwareKeys.action(DeskKey.HOME))
        assertEquals(DeskAction.TO_END, HardwareKeys.action(DeskKey.END))
        assertEquals(DeskAction.NEXT_ITEM, HardwareKeys.action(DeskKey.J))
        assertEquals(DeskAction.PREVIOUS_ITEM, HardwareKeys.action(DeskKey.K))
        assertEquals(DeskAction.LINE_DOWN, HardwareKeys.action(DeskKey.DOWN, repeated = true), "a key held goes on moving")
    }

    @Test
    fun refreshAndSearchAreAskedOnce() {
        assertEquals(DeskAction.REFRESH, HardwareKeys.action(DeskKey.F5))
        assertEquals(DeskAction.REFRESH, HardwareKeys.action(DeskKey.R, ctrl = true))
        assertEquals(DeskAction.FIND, HardwareKeys.action(DeskKey.SLASH))
        assertEquals(DeskAction.FIND_IN_PAGE, HardwareKeys.action(DeskKey.F, ctrl = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.F5, repeated = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.SLASH, repeated = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.R), "r alone is a letter")
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.F))
    }

    @Test
    fun nothingWhileAFieldIsTypedIn() {
        DeskKey.entries.forEach { key ->
            assertEquals(DeskAction.NONE, HardwareKeys.action(key, typing = true), "$key")
            assertEquals(DeskAction.NONE, HardwareKeys.action(key, typing = true, ctrl = true), "Ctrl+$key")
        }
    }

    @Test
    fun androidsOwnKeysAreLeftToIt() {
        // Back (Esc), the focus walk (Tab, Enter) and the pager's swipe stay the system's; so does every desktop key not taken on.
        val left = listOf(DeskKey.ESCAPE, DeskKey.BACK, DeskKey.TAB, DeskKey.ENTER, DeskKey.LEFT, DeskKey.RIGHT, DeskKey.MENU, DeskKey.DELETE, DeskKey.Q, DeskKey.W, DeskKey.O, DeskKey.L, DeskKey.B, DeskKey.QUESTION, DeskKey.F1, DeskKey.OTHER)
        left.forEach { key ->
            listOf(false, true).forEach { ctrl -> assertEquals(DeskAction.NONE, HardwareKeys.action(key, ctrl = ctrl), "$key ctrl=$ctrl") }
        }
    }

    @Test
    fun modifiersBelongToTheSystem() {
        DeskKey.entries.forEach { key ->
            assertEquals(DeskAction.NONE, HardwareKeys.action(key, alt = true), "Alt+$key")
            assertEquals(DeskAction.NONE, HardwareKeys.action(key, meta = true), "Meta+$key")
        }
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.K, ctrl = true), "the desktop's Ctrl+K is not taken on")
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.PAGE_DOWN, ctrl = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.DOWN, ctrl = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.J, shift = true))
    }

    @Test
    fun whatIsOpenOverThePageKeepsItStill() {
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.DOWN, somethingOpen = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.J, somethingOpen = true))
        assertEquals(DeskAction.NONE, HardwareKeys.action(DeskKey.SLASH, somethingOpen = true))
        assertEquals(DeskAction.LINE_DOWN, HardwareKeys.action(DeskKey.DOWN, somethingOpen = true, openIsRead = true), "an opened post is read with them")
        assertEquals(DeskAction.REFRESH, HardwareKeys.action(DeskKey.F5, somethingOpen = true))
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun DesktopActions.key(action: DeskAction, openSearch: () -> Unit = {}) = onHardwareKey(action, scope, 64f, openSearch)

    @Test
    fun aKeyNothingAnswersIsLeftToAndroid() {
        val actions = DesktopActions()
        assertFalse(actions.key(DeskAction.NONE))
        assertFalse(actions.key(DeskAction.REFRESH), "no screen refreshes")
        assertFalse(actions.key(DeskAction.LINE_DOWN), "no list on screen")
        assertFalse(actions.key(DeskAction.NEXT_ITEM))
    }

    @Test
    fun theScreenShownRefreshes() {
        val actions = DesktopActions()
        var refreshed = 0
        actions.take(this, refresh = { refreshed++ }, turn = {}, isOpen = { false }, close = {})
        assertTrue(actions.key(DeskAction.REFRESH))
        assertEquals(1, refreshed)
    }

    @Test
    fun thePagesOwnSearchComesBeforeTheApps() {
        val actions = DesktopActions()
        var opened = 0
        var found = 0
        assertTrue(actions.key(DeskAction.FIND) { opened++ })
        assertTrue(actions.key(DeskAction.FIND_IN_PAGE) { opened++ })
        assertEquals(2, opened)
        actions.addFinder(this) { found++ }
        assertTrue(actions.key(DeskAction.FIND) { opened++ })
        assertTrue(actions.key(DeskAction.FIND_IN_PAGE) { opened++ })
        assertEquals(2 to 2, opened to found)
    }

    @Test
    fun ctrlFOpensThePagesFindBar() {
        val actions = DesktopActions()
        var bar = 0
        actions.addPageFinder(this, PageFinder(open = { bar++ }, step = { false }))
        assertTrue(actions.key(DeskAction.FIND_IN_PAGE) { error("the search stays shut") })
        assertEquals(1, bar)
    }
}
