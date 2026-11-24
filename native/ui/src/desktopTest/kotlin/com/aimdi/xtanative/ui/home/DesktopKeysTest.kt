package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
        selecting: Boolean = false,
        openIsRead: Boolean = false,
    ) = DesktopKeys.action(key, alt, ctrl, focusInside, canGoBack, rtl, open, shift, selecting, openIsRead)

    @Test
    fun tabStepsForwardAndShiftTabBackButCtrlOrAltTabIsNoStep() {
        assertEquals(TabStep.FORWARD, DesktopKeys.tabStep(DeskKey.TAB, shift = false, ctrl = false, alt = false))
        assertEquals(TabStep.BACKWARD, DesktopKeys.tabStep(DeskKey.TAB, shift = true, ctrl = false, alt = false))
        assertNull(DesktopKeys.tabStep(DeskKey.TAB, shift = false, ctrl = true, alt = false), "Ctrl+Tab turns the tabs")
        assertNull(DesktopKeys.tabStep(DeskKey.TAB, shift = true, ctrl = true, alt = false), "Ctrl+Shift+Tab turns them back")
        assertNull(DesktopKeys.tabStep(DeskKey.TAB, shift = false, ctrl = false, alt = true), "Alt+Tab is the desktop's")
        assertNull(DesktopKeys.tabStep(DeskKey.DOWN, shift = false, ctrl = false, alt = false))
        assertNull(DesktopKeys.tabStep(DeskKey.OTHER, shift = true, ctrl = false, alt = false))
    }

    @Test
    fun aButtonClickedKeepsNoKeysButAFieldOrWhatTabReachedDoes() {
        assertFalse(DesktopKeys.keysInside(focusInside = false, typing = false, clicked = false), "the frame itself has them")
        assertFalse(DesktopKeys.keysInside(focusInside = true, typing = false, clicked = true), "a tab, chip or refresh button clicked")
        assertTrue(DesktopKeys.keysInside(focusInside = true, typing = true, clicked = true), "a field clicked into")
        assertTrue(DesktopKeys.keysInside(focusInside = true, typing = false, clicked = false), "a button reached with Tab: Space presses it")
        assertTrue(DesktopKeys.keysInside(focusInside = true, typing = true, clicked = false), "a field reached with Tab")
        // After the click, the keys read on at once.
        val inside = DesktopKeys.keysInside(focusInside = true, typing = false, clicked = true)
        assertEquals(DeskAction.NEXT_ITEM, action(DeskKey.J, focusInside = inside))
        assertEquals(DeskAction.SCREEN_DOWN, action(DeskKey.PAGE_DOWN, focusInside = inside))
        assertEquals(DeskAction.SCREEN_DOWN, action(DeskKey.SPACE, focusInside = inside))
        assertEquals(DeskAction.PREVIOUS_COLUMN, action(DeskKey.LEFT, ctrl = true, focusInside = inside))
        assertEquals(DeskAction.BACK, action(DeskKey.ESCAPE, focusInside = inside), "nothing to leave first")
    }

    @Test
    fun altArrowsMoveTheRowUnderThePointer() {
        assertEquals(DeskAction.MOVE_ROW_UP, action(DeskKey.UP, alt = true))
        assertEquals(DeskAction.MOVE_ROW_DOWN, action(DeskKey.DOWN, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.UP, alt = true, focusInside = true), "what has the focus answers first; a field keeps them")
        assertEquals(DeskAction.NONE, action(DeskKey.DOWN, alt = true, open = true), "not under what is open over the page")
        assertEquals(DeskAction.NONE, action(DeskKey.UP, alt = true, ctrl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.DOWN, alt = true, shift = true))
    }

    @Test
    fun ctrlASelectsAllAsInAFileManagerButNotInAField() {
        assertEquals(DeskAction.SELECT_ALL, action(DeskKey.A, ctrl = true))
        assertEquals(DeskAction.SELECT_ALL, action(DeskKey.A, ctrl = true, selecting = true))
        assertEquals(DeskAction.NONE, action(DeskKey.A, ctrl = true, focusInside = true), "a field selects its own text")
        assertEquals(DeskAction.NONE, action(DeskKey.A, ctrl = true, open = true), "not under what is open over the page")
        assertEquals(DeskAction.NONE, action(DeskKey.A, ctrl = true, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.A, ctrl = true, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.A))
    }

    @Test
    fun deleteRemovesTheSelectionOnlyWhileSelecting() {
        assertEquals(DeskAction.REMOVE_SELECTED, action(DeskKey.DELETE, selecting = true))
        assertEquals(DeskAction.NONE, action(DeskKey.DELETE))
        assertEquals(DeskAction.NONE, action(DeskKey.DELETE, selecting = true, focusInside = true), "a field deletes a character")
        assertEquals(DeskAction.NONE, action(DeskKey.DELETE, selecting = true, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.DELETE, selecting = true, ctrl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.DELETE, selecting = true, shift = true))
    }

    @Test
    fun escStopsSelectingBeforeGoingBack() {
        assertEquals(DeskAction.LEAVE_SELECTION, action(DeskKey.ESCAPE, selecting = true))
        assertEquals(DeskAction.LEAVE_SELECTION, action(DeskKey.ESCAPE, selecting = true, canGoBack = false))
        assertEquals(DeskAction.LEAVE_FIELD, action(DeskKey.ESCAPE, selecting = true, focusInside = true), "the field is left first")
        assertEquals(DeskAction.CLOSE, action(DeskKey.ESCAPE, selecting = true, open = true), "what is open over the page closes first")
        assertEquals(DeskAction.BACK, action(DeskKey.ESCAPE))
        assertEquals(DeskAction.BACK, action(DeskKey.LEFT, alt = true, selecting = true), "Alt+← still goes back")
    }

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
            // Alt+Home is the window's: back to Home (altHomeGoesHome).
            // Alt+↑ and Alt+↓ move a reorderable list's row (altArrowsMoveTheRowUnderThePointer).
            if (key !in setOf(DeskKey.HOME, DeskKey.UP, DeskKey.DOWN)) assertEquals(DeskAction.NONE, action(key, alt = true), "Alt+$key")
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
    fun altRightGoesForwardFromAFieldAndOverAnOpenedPostToo() {
        assertEquals(DeskAction.FORWARD, action(DeskKey.RIGHT, alt = true))
        assertEquals(DeskAction.FORWARD, action(DeskKey.RIGHT, alt = true, focusInside = true), "as Alt+← does from a field")
        assertEquals(DeskAction.FORWARD, action(DeskKey.RIGHT, alt = true, canGoBack = false))
        assertEquals(DeskAction.FORWARD, action(DeskKey.RIGHT, alt = true, open = true), "an opened post is a card the page counts as open")
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, alt = true, shift = true))
        assertEquals(DeskAction.NEXT_COLUMN, action(DeskKey.RIGHT, ctrl = true))
    }

    @Test
    fun x11SideButtonsComeAsAwtButtonsSixAndSeven() {
        assertEquals(SideButton.BACK, DesktopKeys.sideButton(6))
        assertEquals(SideButton.FORWARD, DesktopKeys.sideButton(7))
        listOf(1, 2, 3, 4, 5, 8).forEach { assertNull(DesktopKeys.sideButton(it), "button $it") }
    }

    @Test
    fun noKeyOfATextFieldGoesBackOrForward() {
        // Backspace deletes in a field; it is no back key, as browsers dropped it.
        assertEquals(DeskAction.NONE, action(DeskKey.OTHER))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, focusInside = true))
    }

    @Test
    fun aSidewaysScrollOnX11IsNoBackOrForward() {
        // X's 6 and 7 (a touchpad's sideways scroll) come as AWT's 4 and 5, which Compose calls back and forward.
        assertNull(DesktopKeys.pressedSide(4, composeBack = true, composeForward = false))
        assertNull(DesktopKeys.pressedSide(5, composeBack = false, composeForward = true))
        assertEquals(SideButton.BACK, DesktopKeys.pressedSide(6, composeBack = false, composeForward = false))
        assertEquals(SideButton.FORWARD, DesktopKeys.pressedSide(7, composeBack = false, composeForward = false))
        assertNull(DesktopKeys.pressedSide(1, composeBack = false, composeForward = false))
        // No AWT event: Compose's own flags.
        assertEquals(SideButton.BACK, DesktopKeys.pressedSide(null, composeBack = true, composeForward = false))
        assertEquals(SideButton.FORWARD, DesktopKeys.pressedSide(null, composeBack = false, composeForward = true))
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
        // Ctrl+→ is the Deck's: the next column (ctrlArrowsChooseTheColumnTheReadingKeysMove).
        assertEquals(DeskAction.NEXT_COLUMN, action(DeskKey.RIGHT, ctrl = true))
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
        var handed = 0
        val actions = DesktopActions(refocus = { handed++ }, takeKeys = { handed++ })
        actions.sheetOpened()
        actions.sheetOpened()
        actions.sheetClosed()
        assertEquals(0, handed, "the sheet under it keeps its field's focus")
        actions.sheetClosed()
        assertEquals(1, handed)
        actions.sheetClosed()
        assertEquals(2, handed, "never below none")
    }

    @Test
    fun aSheetClosingOverTheScreenItOpenedOnTakesTheKeysFromTheButtonThatOpenedIt() {
        var screen = "deck"
        val refocused = mutableListOf<FocusRequester?>()
        var taken = 0
        val actions = DesktopActions(refocus = { refocused += it }, takeKeys = { taken++ }, screen = { screen })
        actions.sheetOpened()
        actions.sheetClosed()
        assertEquals(1, taken, "the + clicked to open it held the keys: the frame takes them")
        assertEquals(emptyList(), refocused)

        // A pick that went to another screen: a field that screen focuses keeps the keys.
        actions.sheetOpened()
        screen = "search"
        actions.sheetClosed()
        assertEquals(1, taken)
        assertEquals(listOf<FocusRequester?>(null), refocused)

        // Over an overlay (a video full screen), the keys go back to it.
        val video = FocusRequester()
        actions.holdKeys(video)
        refocused.clear()
        actions.sheetOpened()
        actions.sheetClosed()
        assertEquals(1, taken)
        assertEquals(listOf<FocusRequester?>(video), refocused)
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
    fun aFieldsEscapeDropsItsOwnFirstAndOneGoneTakesNone() {
        val actions = DesktopActions()
        assertFalse(actions.escapeInField(), "no field: Esc leaves it")
        var highlighted = true
        val field = Any()
        actions.addFieldEscape(field) { highlighted.also { highlighted = false } }
        assertTrue(actions.escapeInField(), "the first Esc drops the highlight")
        assertFalse(actions.escapeInField(), "the next leaves the field")
        highlighted = true
        actions.removeFieldEscape(field)
        assertFalse(actions.escapeInField())
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
    fun ctrlKAndCtrlLGoToTheSearchAsABrowsersDo() {
        for (key in listOf(DeskKey.K, DeskKey.L)) {
            assertEquals(DeskAction.FIND, action(key, ctrl = true), "$key")
            assertEquals(DeskAction.FIND, action(key, ctrl = true, focusInside = true), "$key from a field, as Ctrl+F")
            assertEquals(DeskAction.NONE, action(key, ctrl = true, open = true), "$key not under what is open")
            assertEquals(DeskAction.NONE, action(key, ctrl = true, shift = true), "$key with Shift")
            assertEquals(DeskAction.NONE, action(key, ctrl = true, alt = true), "$key with Alt")
        }
        // Alone they stay the reading keys.
        assertEquals(DeskAction.PREVIOUS_ITEM, action(DeskKey.K))
        assertEquals(DeskAction.LIKE_POST, action(DeskKey.L))
    }

    @Test
    fun ctrlNMakesSomethingNewOutsideAFieldAndUnderNothingOpen() {
        assertEquals(DeskAction.CREATE, action(DeskKey.N, ctrl = true))
        assertEquals(DeskAction.CREATE, action(DeskKey.N, ctrl = true, canGoBack = false), "on Home's own pages too")
        assertEquals(DeskAction.CREATE, action(DeskKey.N, ctrl = true, selecting = true), "the page decides; Saved declines while selecting")
        assertEquals(DeskAction.NONE, action(DeskKey.N, ctrl = true, focusInside = true), "a field keeps its keys")
        assertEquals(DeskAction.NONE, action(DeskKey.N, ctrl = true, open = true), "not under what is open over the page")
        assertEquals(DeskAction.NONE, action(DeskKey.N, ctrl = true, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.N, ctrl = true, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.N))
    }

    @Test
    fun ctrlZUndoesOutsideAFieldAndUnderNothingOpen() {
        assertEquals(DeskAction.UNDO, action(DeskKey.Z, ctrl = true))
        assertEquals(DeskAction.UNDO, action(DeskKey.Z, ctrl = true, selecting = true))
        assertEquals(DeskAction.NONE, action(DeskKey.Z, ctrl = true, focusInside = true), "a field undoes its typing")
        assertEquals(DeskAction.NONE, action(DeskKey.Z, ctrl = true, open = true), "not under what is open over the page")
        assertEquals(DeskAction.NONE, action(DeskKey.Z, ctrl = true, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.Z, ctrl = true, alt = true))
        assertEquals(DeskAction.NONE, action(DeskKey.Z))
    }

    @Test
    fun theNewShownLastAnswersCtrlNAndOneThatDeclinesLetsTheKeyGo() {
        val actions = DesktopActions()
        val made = mutableListOf<String>()
        assertFalse(actions.create(), "a page with no New: the key goes on")
        val page = Any()
        val layer = Any()
        actions.addCreator(page) { made += "page"; true }
        actions.addCreator(layer) { false }
        assertFalse(actions.create(), "the page on top declines (its editor is open)")
        actions.removeCreator(layer)
        assertTrue(actions.create())
        actions.addCreator(page) { made += "page again"; true }
        assertTrue(actions.create())
        assertEquals(listOf("page", "page again"), made)
        actions.removeCreator(page)
        assertFalse(actions.create(), "a screen resumed twice is listed once")
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
            assertEquals(DeskAction.NONE, action(key, alt = true), "Alt+$key")
        }
    }

    @Test
    fun ctrlAltPlusMinusAndZeroSizeTheWholeInterface() {
        assertEquals(DeskAction.UI_BIGGER, action(DeskKey.PLUS, ctrl = true, alt = true))
        assertEquals(DeskAction.UI_BIGGER, action(DeskKey.PLUS, ctrl = true, alt = true, shift = true), "Ctrl+Alt+Shift+= on a US keyboard")
        assertEquals(DeskAction.UI_SMALLER, action(DeskKey.MINUS, ctrl = true, alt = true, focusInside = true), "from a field too")
        assertEquals(DeskAction.UI_RESET, action(DeskKey.ZERO, ctrl = true, alt = true, open = true), "over a sheet too")
        assertEquals(DeskAction.TEXT_BIGGER, action(DeskKey.PLUS, ctrl = true, shift = true), "Ctrl+Shift+= stays the text's")
        assertEquals(DeskAction.TEXT_RESET, action(DeskKey.ZERO, ctrl = true))
    }

    @Test
    fun theInterfaceSizeStepsThroughItsPresetsAndStaysAtTheEnds() {
        val presets = listOf(0.8, 0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0)
        assertEquals(1.1, DesktopKeys.uiScaled(1.0, DeskAction.UI_BIGGER, presets))
        assertEquals(1.5, DesktopKeys.uiScaled(1.25, DeskAction.UI_BIGGER, presets))
        assertEquals(0.9, DesktopKeys.uiScaled(1.0, DeskAction.UI_SMALLER, presets))
        assertEquals(2.0, DesktopKeys.uiScaled(2.0, DeskAction.UI_BIGGER, presets), "never past the largest")
        assertEquals(0.8, DesktopKeys.uiScaled(0.8, DeskAction.UI_SMALLER, presets), "never below the smallest")
        assertEquals(1.25, DesktopKeys.uiScaled(1.2, DeskAction.UI_BIGGER, presets), "between presets goes onto the next")
        assertEquals(1.1, DesktopKeys.uiScaled(1.2, DeskAction.UI_SMALLER, presets), "or the one before")
        assertEquals(1.0, DesktopKeys.uiScaled(1.75, DeskAction.UI_RESET, presets))
        assertEquals(1.75, DesktopKeys.uiScaled(1.75, DeskAction.TEXT_BIGGER, presets), "the text's keys leave it")
        assertEquals(1.0, DesktopKeys.uiScaled(1.0, DeskAction.UI_BIGGER, emptyList()))
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

    @Test
    fun ctrlCommaOpensTheSettingsAndCtrlWClosesWhatIsOpen() {
        assertEquals(DeskAction.OPEN_SETTINGS, action(DeskKey.COMMA, ctrl = true))
        assertEquals(DeskAction.OPEN_SETTINGS, action(DeskKey.COMMA, ctrl = true, focusInside = true), "from a field too")
        assertEquals(DeskAction.NONE, action(DeskKey.COMMA, ctrl = true, open = true), "not over a sheet")
        assertEquals(DeskAction.CLOSE, action(DeskKey.W, ctrl = true, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.W, ctrl = true), "it never goes back a screen")
        assertTrue(com.aimdi.xtanative.ui.SearchKeys.opensSettingsOver("home"))
        assertFalse(com.aimdi.xtanative.ui.SearchKeys.opensSettingsOver("settings"))
        assertFalse(com.aimdi.xtanative.ui.SearchKeys.opensSettingsOver("settings/{section}"))
        assertFalse(com.aimdi.xtanative.ui.SearchKeys.opensSettingsOver("intro"))
        assertFalse(com.aimdi.xtanative.ui.SearchKeys.opensSettingsOver(com.aimdi.xtanative.ui.settings.SettingsRoutes.PLUGINS), "the intro's plugin store")
        assertEquals(com.aimdi.xtanative.ui.media.ViewerKey.CLOSE, com.aimdi.xtanative.ui.media.ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = true, closeKey = true))
    }

    @Test
    fun ctrlAndTheWheelSizeTheTextANotchAtATime() {
        assertEquals(0f to DeskAction.TEXT_BIGGER, DesktopKeys.wheelText(0f, -1f))
        assertEquals(0f to DeskAction.TEXT_SMALLER, DesktopKeys.wheelText(0f, 1f))
        // A touchpad's small moves add up first.
        val (half, none) = DesktopKeys.wheelText(0f, -0.5f)
        assertEquals(DeskAction.NONE, none)
        assertEquals(DeskAction.TEXT_BIGGER, DesktopKeys.wheelText(half, -0.6f).second)
        // Turning back starts afresh.
        assertEquals(0.4f to DeskAction.NONE, DesktopKeys.wheelText(-0.8f, 0.4f))
    }

    @Test
    fun jAndKMovePostByPostWhileNothingIsTyped() {
        assertEquals(DeskAction.NEXT_ITEM, DesktopKeys.action(DeskKey.J, alt = false, ctrl = false, focusInside = false, canGoBack = false))
        assertEquals(DeskAction.PREVIOUS_ITEM, DesktopKeys.action(DeskKey.K, alt = false, ctrl = false, focusInside = false, canGoBack = false))
        // Typed into a field, with a modifier, or under something open: the key is not the list's.
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.J, alt = false, ctrl = false, focusInside = true, canGoBack = false))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.J, alt = false, ctrl = true, focusInside = false, canGoBack = false))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.K, alt = false, ctrl = false, focusInside = false, canGoBack = false, shift = true))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.J, alt = false, ctrl = false, focusInside = false, canGoBack = false, somethingOpen = true))
    }

    @Test
    fun anItemStepLandsOnAPostsStart() {
        fun item(index: Int, offset: Int, size: Int = 300) = DesktopKeys.ShownItem(index, offset, size)
        // At the top: j brings the second item up, k has nowhere to go.
        assertEquals(1 to 0, DesktopKeys.itemStep(listOf(item(0, 0), item(1, 300), item(2, 600)), 5, forward = true))
        assertNull(DesktopKeys.itemStep(listOf(item(0, 0), item(1, 300)), 5, forward = false))
        // Item 2 partly read: k goes to its start, j to item 3.
        assertEquals(2 to 0, DesktopKeys.itemStep(listOf(item(2, -40), item(3, 260)), 5, forward = false))
        assertEquals(3 to 0, DesktopKeys.itemStep(listOf(item(2, -40), item(3, 260)), 5, forward = true))
        // Item 2 at its start: k goes to the one before.
        assertEquals(1 to 0, DesktopKeys.itemStep(listOf(item(2, 0), item(3, 300)), 5, forward = false))
        assertNull(DesktopKeys.itemStep(listOf(item(4, 0)), 5, forward = true))
        assertNull(DesktopKeys.itemStep(emptyList(), 5, forward = true))
    }

    @Test
    fun aPinnedHeaderStaysAndThePostGoesUnderIt() {
        fun item(index: Int, offset: Int, size: Int = 300) = DesktopKeys.ShownItem(index, offset, size)
        // Tabs (item 1, 48 px) pinned over item 4, which is read from 48 px.
        val shown = listOf(item(1, 0, 48), item(4, 48), item(5, 348))
        assertEquals(5 to -48, DesktopKeys.itemStep(shown, 9, forward = true))
        assertEquals(3 to -48, DesktopKeys.itemStep(shown, 9, forward = false))
        // Item 4 partly under the tabs: k brings its start back under them.
        assertEquals(4 to -48, DesktopKeys.itemStep(listOf(item(1, 0, 48), item(4, -100), item(5, 200)), 9, forward = false))
        // A header in its own place (the next item follows it) is no pinned one: k goes on up the list.
        assertEquals(0 to 0, DesktopKeys.itemStep(listOf(item(1, 0, 48), item(2, 48)), 9, forward = false))
    }

    @Test
    fun aPostCoveredByTabsThatStuckComesOutFromUnderThem() {
        fun item(index: Int, offset: Int, size: Int = 300) = DesktopKeys.ShownItem(index, offset, size)
        // The tabs (1) stuck over post 2, which the step brought to 0.
        assertEquals(48, DesktopKeys.coveredAfterStep(listOf(item(1, 0, 48), item(2, 0), item(3, 300)), 2))
        // Already under them, or no pinned header: nothing to do.
        assertEquals(0, DesktopKeys.coveredAfterStep(listOf(item(1, 0, 48), item(2, 48)), 2))
        assertEquals(0, DesktopKeys.coveredAfterStep(listOf(item(2, 0), item(3, 300)), 2))
    }

    @Test
    fun ctrlVOpensACopiedLinkWhileNothingIsTyped() {
        assertEquals(DeskAction.OPEN_PASTED_LINK, DesktopKeys.action(DeskKey.V, alt = false, ctrl = true, focusInside = false, canGoBack = false))
        // In a field Ctrl+V pastes, as ever; under a menu or with Shift it is not the window's.
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.V, alt = false, ctrl = true, focusInside = true, canGoBack = false))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.V, alt = false, ctrl = true, focusInside = false, canGoBack = false, somethingOpen = true))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.V, alt = false, ctrl = true, focusInside = false, canGoBack = false, shift = true))
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.V, alt = false, ctrl = false, focusInside = false, canGoBack = false))
    }

    @Test
    fun ctrlAndADigitGoToThatTab() {
        assertEquals(0, DesktopKeys.pageAt(1, 5))
        assertEquals(4, DesktopKeys.pageAt(5, 5))
        assertNull(DesktopKeys.pageAt(6, 5))
        // 9 is the last, as in a browser.
        assertEquals(4, DesktopKeys.pageAt(9, 5))
        assertEquals(0, DesktopKeys.pageAt(9, 1))
        assertNull(DesktopKeys.pageAt(1, 0))
        assertNull(DesktopKeys.pageAt(0, 5))
    }

    @Test
    fun altHomeGoesHome() {
        assertEquals(DeskAction.GO_HOME, DesktopKeys.action(DeskKey.HOME, alt = true, ctrl = false, focusInside = false, canGoBack = true))
        assertEquals(DeskAction.GO_HOME, DesktopKeys.action(DeskKey.HOME, alt = true, ctrl = false, focusInside = true, canGoBack = true))
        // Over an opened post, which counts as something open, too.
        assertEquals(DeskAction.GO_HOME, DesktopKeys.action(DeskKey.HOME, alt = true, ctrl = false, focusInside = false, canGoBack = true, somethingOpen = true))
        // Home alone still goes to the list's top.
        assertEquals(DeskAction.TO_TOP, DesktopKeys.action(DeskKey.HOME, alt = false, ctrl = false, focusInside = false, canGoBack = true))
    }

    @Test
    fun ctrlArrowsChooseTheColumnTheReadingKeysMove() {
        assertEquals(DeskAction.NEXT_COLUMN, action(DeskKey.RIGHT, ctrl = true))
        assertEquals(DeskAction.PREVIOUS_COLUMN, action(DeskKey.LEFT, ctrl = true))
        // In the reading direction, as ← and → turn the pages.
        assertEquals(DeskAction.NEXT_COLUMN, action(DeskKey.LEFT, ctrl = true, rtl = true))
        assertEquals(DeskAction.PREVIOUS_COLUMN, action(DeskKey.RIGHT, ctrl = true, rtl = true))
        // A field keeps them (a word at a time); nothing under what is open; Shift (selecting) and Alt+← (Back) are not theirs.
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, ctrl = true, focusInside = true))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, ctrl = true, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, ctrl = true, shift = true))
        assertEquals(DeskAction.BACK, action(DeskKey.LEFT, ctrl = true, alt = true))
    }

    @Test
    fun aColumnStepStopsAtEitherEnd() {
        assertEquals(1, DesktopKeys.columnStep(3, 0, 1))
        assertEquals(2, DesktopKeys.columnStep(3, 2, 1))
        assertEquals(0, DesktopKeys.columnStep(3, 0, -1))
        assertEquals(1, DesktopKeys.columnStep(3, 2, -1))
        // From none (no list read yet, or one outside the columns): the first going on, the last going back.
        assertEquals(0, DesktopKeys.columnStep(3, null, 1))
        assertEquals(2, DesktopKeys.columnStep(3, null, -1))
        assertEquals(0, DesktopKeys.columnStep(3, 7, 1))
        assertNull(DesktopKeys.columnStep(0, null, 1))
    }

    @Test
    fun theChosenColumnsListIsTheOneTheKeysMoveUntilThePointerPointsElsewhere() {
        val actions = DesktopActions()
        val first = LazyListState()
        val second = LazyListState()
        val third = LazyListState()
        assertFalse(actions.stepColumn(1), "no page of columns: the key is not taken")
        actions.addList(first, "a")
        actions.addList(second, "b")
        actions.addList(third, "c")
        val owner = Any()
        actions.takeColumns(owner) { listOf("a", "b", "c") }
        assertSame(first, actions.readList())
        // From the column read now (the first, nothing pointed) to the next.
        assertTrue(actions.stepColumn(1))
        assertEquals("b", actions.chosenColumn)
        assertSame(second, actions.readList())
        // The pointer over the column chosen keeps it; over another, that one is read and none is chosen.
        actions.point(second)
        assertEquals("b", actions.chosenColumn)
        actions.point(third)
        assertNull(actions.chosenColumn)
        assertSame(third, actions.readList())
        // From the pointed column on: the end holds.
        actions.stepColumn(1)
        assertEquals("c", actions.chosenColumn)
        actions.stepColumn(-1)
        actions.stepColumn(-1)
        actions.stepColumn(-1)
        assertSame(first, actions.readList())
        // A column's list replaced (another tab in it) is still the column's; meanwhile, with none, the keys move nothing.
        actions.removeList(first)
        assertNull(actions.readList())
        val again = LazyListState()
        actions.addList(again, "a")
        assertSame(again, actions.readList())
        // The page gone, nothing is chosen.
        actions.releaseColumns(owner)
        assertNull(actions.chosenColumn)
        assertFalse(actions.stepColumn(1))
    }

    @Test
    fun aStillPointerPointsAtNothing() {
        assertTrue(DesktopKeys.pointerMoved(null, Offset(10f, 10f)), "the first place")
        assertTrue(DesktopKeys.pointerMoved(Offset(10f, 10f), Offset(12f, 10f)))
        assertFalse(DesktopKeys.pointerMoved(Offset(10f, 10f), Offset(10.2f, 10f)), "rounding is no move")
        assertTrue(DesktopKeys.pointerMoved(Offset(10f, 10f), null), "a place unknown counts")
    }

    @Test
    fun theDeckScrollingUnderAStillPointerKeepsTheChosenColumn() {
        val actions = DesktopActions()
        val first = LazyListState()
        val second = LazyListState()
        actions.addList(first, "a")
        actions.addList(second, "b")
        actions.takeColumns(Any()) { listOf("a", "b") }
        actions.point(second, Offset(600f, 300f))
        actions.stepColumn(-1)
        // The deck slid under the pointer: the window says the pointer entered the list there again.
        actions.point(second, Offset(600f, 300f))
        assertEquals("a", actions.chosenColumn)
        assertSame(first, actions.readList())
        // The reader moves the mouse: it takes the keys back.
        actions.point(second, Offset(640f, 300f))
        assertNull(actions.chosenColumn)
        assertSame(second, actions.readList())
    }

    @Test
    fun aRemovedChosenColumnLeavesTheKeysToTheOthers() {
        val actions = DesktopActions()
        val first = LazyListState()
        val second = LazyListState()
        actions.addList(first, "a")
        actions.addList(second, "b")
        var order = listOf("a", "b")
        actions.takeColumns(Any()) { order }
        actions.stepColumn(1)
        actions.stepColumn(1)
        assertEquals("b", actions.chosenColumn)
        // The chosen column removed (its menu's Remove): the keys move what is left, and stepping starts from there.
        order = listOf("a")
        actions.removeList(second)
        assertSame(first, actions.readList())
        assertTrue(actions.stepColumn(1))
        assertEquals("a", actions.chosenColumn)
    }

    @Test
    fun aPointerRestingOffAnyListKeepsTheChosenColumnWhenTheDeckSlides() {
        val actions = DesktopActions()
        val first = LazyListState()
        val second = LazyListState()
        actions.addList(first, "a")
        actions.addList(second, "b")
        actions.takeColumns(Any()) { listOf("a", "b") }
        // The pointer rests on a column's error page (no list), then the keys choose a column.
        actions.pointerAt(Offset(900f, 300f))
        actions.stepColumn(1)
        actions.stepColumn(1)
        // The deck slid: a list came under the resting pointer, and the window said so.
        actions.point(first, Offset(900f, 300f))
        assertEquals("b", actions.chosenColumn)
        assertSame(second, actions.readList())
    }

    @Test
    fun aChosenColumnGoneFromThePageIsForgotten() {
        val actions = DesktopActions()
        actions.addList(LazyListState(), "a")
        actions.addList(LazyListState(), "b")
        var order = listOf("a", "b")
        actions.takeColumns(Any()) { order }
        actions.stepColumn(1)
        actions.stepColumn(1)
        order = listOf("a")
        actions.forgetGoneColumn()
        // Added again: not chosen by itself.
        order = listOf("a", "b")
        assertNull(actions.chosen())
    }

    @Test
    fun aListScrolledUnderAStillPointerIsPointedAt() {
        val actions = DesktopActions()
        val first = LazyListState()
        val second = LazyListState()
        actions.addList(first, "a")
        actions.addList(second, "b")
        actions.pointerAt(Offset(900f, 300f))
        actions.point(first, Offset(900f, 300f))
        assertSame(first, actions.readList())
        // The window's own move after a sideways scroll: not a move of the pointer, but the scroll counts as one.
        actions.scrolled()
        actions.point(second, Offset(900f, 300f))
        assertSame(second, actions.readList())
    }

    @Test
    fun aColumnOpenedBesideThePageIsReadWithTheKeys() {
        // The Deck's detail open: the reading keys, j and k, and Ctrl+← and Ctrl+→ go on working.
        assertEquals(DeskAction.LINE_DOWN, action(DeskKey.DOWN, open = true, openIsRead = true))
        assertEquals(DeskAction.SCREEN_DOWN, action(DeskKey.PAGE_DOWN, open = true, openIsRead = true))
        assertEquals(DeskAction.SCREEN_UP, action(DeskKey.SPACE, shift = true, open = true, openIsRead = true))
        assertEquals(DeskAction.TO_END, action(DeskKey.END, open = true, openIsRead = true))
        assertEquals(DeskAction.NEXT_ITEM, action(DeskKey.J, open = true, openIsRead = true))
        assertEquals(DeskAction.PREVIOUS_ITEM, action(DeskKey.K, open = true, openIsRead = true))
        assertEquals(DeskAction.NEXT_COLUMN, action(DeskKey.RIGHT, ctrl = true, open = true, openIsRead = true))
        // Esc, Alt+← and Ctrl+W still close it first.
        assertEquals(DeskAction.CLOSE, action(DeskKey.ESCAPE, open = true, openIsRead = true))
        assertEquals(DeskAction.CLOSE, action(DeskKey.LEFT, alt = true, open = true, openIsRead = true))
        assertEquals(DeskAction.CLOSE, action(DeskKey.W, ctrl = true, open = true, openIsRead = true))
        // A field still keeps its keys.
        assertEquals(DeskAction.NONE, action(DeskKey.DOWN, focusInside = true, open = true, openIsRead = true))
        // Something open over the page still stops them all.
        assertEquals(DeskAction.NONE, action(DeskKey.DOWN, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.J, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.RIGHT, ctrl = true, open = true))
        // With nothing open, the flag changes nothing.
        assertEquals(DeskAction.LINE_DOWN, action(DeskKey.DOWN, openIsRead = true))
    }

    @Test
    fun whatIsOpenIsReadOnlyWhileTheScreenSaysSoAndNoSheetIsOver() {
        val actions = DesktopActions()
        assertFalse(actions.openIsRead(), "no screen answers")
        var detail = true
        actions.take(Any(), refresh = {}, turn = {}, isOpen = { detail }, close = {}, readsOpen = { detail })
        assertTrue(actions.openIsRead())
        actions.sheetOpened()
        assertFalse(actions.openIsRead(), "a sheet over the detail covers it")
        actions.sheetClosed()
        assertTrue(actions.openIsRead())
        detail = false
        assertFalse(actions.openIsRead())
    }

    @Test
    fun aColumnJustOpenedIsReadUntilThePointerOrTheKeysGoElsewhere() {
        var taken = 0
        val actions = DesktopActions(takeKeys = { taken++ })
        val first = LazyListState()
        val second = LazyListState()
        actions.addList(first, "a")
        actions.addList(second, "b")
        var detailOpen = false
        actions.takeColumns(Any()) { listOf("a", "b") + listOfNotNull("detail".takeIf { detailOpen }) }
        actions.point(first, Offset(100f, 300f))
        // A post opened from the first column: the keys move the detail, once its list is shown.
        detailOpen = true
        actions.readOpened("detail")
        assertEquals(1, taken, "the frame takes the keys from the post clicked to open it")
        assertSame(first, actions.readList(), "the detail's list not shown yet")
        val post = LazyListState()
        actions.addList(post, "detail")
        assertSame(post, actions.readList())
        assertNull(actions.chosenColumn, "opened, not marked as chosen")
        // Ctrl+← goes from the detail to the column before it; Ctrl+→ back to the detail.
        assertTrue(actions.stepColumn(-1))
        assertEquals("b", actions.chosenColumn)
        assertSame(second, actions.readList())
        assertTrue(actions.stepColumn(1))
        assertEquals("detail", actions.chosenColumn)
        assertSame(post, actions.readList())
        // Opened again (another post), then the pointer moved over a column: that column is read.
        actions.readOpened("detail")
        assertNull(actions.chosenColumn)
        assertSame(post, actions.readList())
        actions.point(second, Offset(600f, 300f))
        assertSame(second, actions.readList())
        // The detail chosen, then closed: the keys go back to the list the pointer was over, not nowhere.
        actions.stepColumn(1)
        assertEquals("detail", actions.chosenColumn)
        actions.removeList(post)
        detailOpen = false
        assertSame(second, actions.readList())
        assertTrue(actions.stepColumn(-1))
        assertEquals("a", actions.chosenColumn)
    }

    @Test
    fun thePostTheReadingKeysAreOnIsActedOn() {
        assertEquals(DeskAction.OPEN_POST, action(DeskKey.ENTER))
        assertEquals(DeskAction.OPEN_POST, action(DeskKey.O))
        assertEquals(DeskAction.LIKE_POST, action(DeskKey.L))
        assertEquals(DeskAction.SAVE_POST, action(DeskKey.B))
        assertEquals(DeskAction.COPY_POST_LINK, action(DeskKey.C, ctrl = true, shift = true))
        assertEquals(DeskAction.POST_MENU, action(DeskKey.MENU))
        assertEquals(DeskAction.POST_MENU, action(DeskKey.F10, shift = true))
        // Beside the page (the Deck's detail) they still read; over it, not.
        assertEquals(DeskAction.LIKE_POST, action(DeskKey.L, open = true, openIsRead = true))
        listOf(DeskKey.ENTER, DeskKey.O, DeskKey.L, DeskKey.B, DeskKey.MENU).forEach { key ->
            assertEquals(DeskAction.NONE, action(key, open = true), "$key under a sheet")
            assertEquals(DeskAction.NONE, action(key, focusInside = true), "$key in a field")
            assertEquals(DeskAction.NONE, action(key, alt = true), "$key with Alt")
        }
        assertEquals(DeskAction.NONE, action(DeskKey.C, ctrl = true, shift = true, focusInside = true), "a field copies its own text")
        assertEquals(DeskAction.NONE, action(DeskKey.C, ctrl = true, shift = true, open = true))
        assertEquals(DeskAction.NONE, action(DeskKey.C, ctrl = true), "plain Ctrl+C is the selection's")
        assertEquals(DeskAction.NONE, action(DeskKey.L, shift = true))
        assertEquals(DeskAction.NONE, action(DeskKey.F10), "F10 alone is not the menu")
        // A button the mouse clicked keeps Enter to press, while the other keys read on.
        val clicked = DesktopKeys.keysInside(focusInside = true, typing = false, clicked = true)
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.ENTER, alt = false, ctrl = false, focusInside = clicked, canGoBack = true, focusHeld = true))
        assertEquals(DeskAction.LIKE_POST, DesktopKeys.action(DeskKey.L, alt = false, ctrl = false, focusInside = clicked, canGoBack = true, focusHeld = true))
    }

    @Test
    fun theCurrentPostIsTheFirstStartingUnderTheLine() {
        fun item(index: Int, offset: Int, size: Int = 300) = DesktopKeys.ShownItem(index, offset, size)
        // Posts at 0, 300 and 600: the first.
        assertEquals(0, DesktopKeys.currentPost(listOf(item(0, 0), item(1, 300), item(2, 600)), listOf(0, 300, 600), end = 800))
        // The first partly read above the top: the next one, whose start shows.
        assertEquals(1, DesktopKeys.currentPost(listOf(item(0, -40), item(1, 260)), listOf(-40, 260), end = 800))
        // A thread item of two posts, landed on by j: its first post, in any order drawn.
        assertEquals(1, DesktopKeys.currentPost(listOf(item(3, 0, 500), item(4, 500)), listOf(250, 0, 500), end = 800))
        // Tabs pinned over the list (48 px): the post just under them, not the one they cover.
        val pinned = listOf(item(1, 0, 48), item(4, 48), item(5, 348))
        assertEquals(1, DesktopKeys.currentPost(pinned, listOf(10, 48, 348), end = 800))
        // One post taller than the view, read across: that one.
        assertEquals(0, DesktopKeys.currentPost(listOf(item(2, -400, 2000)), listOf(-400, 1600), end = 800))
        // A post laid out past the end of the view is not the one.
        assertNull(DesktopKeys.currentPost(listOf(item(0, 0)), listOf(900), end = 800))
        assertNull(DesktopKeys.currentPost(emptyList(), emptyList(), end = 800))
    }

    @Test
    fun aStepMarksTheFirstPostOfTheItemItWentTo() {
        // Item from 300 to 800 holds a thread of two posts (300, 550); one post before it, one after.
        assertEquals(2, DesktopKeys.firstIn(300, 500, listOf(0, 550, 300, 800)))
        assertNull(DesktopKeys.firstIn(300, 500, listOf(0, 800)), "a row without a post (a header)")
    }

    @Test
    fun atTheEndJAndKWalkThePostsLeftOneByOne() {
        fun item(index: Int, offset: Int, size: Int = 200) = DesktopKeys.ShownItem(index, offset, size)
        // The list at its end: items 6 to 9 in view, a post each, 7 marked (j landed there, below the top).
        val shown = listOf(item(6, -100), item(7, 100), item(8, 300), item(9, 500))
        val tops = listOf(-100, 100, 300, 500)
        assertEquals(DesktopKeys.KeyStep.ToPost(2, 300), DesktopKeys.keyStep(shown, 10, tops, marked = 1, forward = true))
        assertEquals(DesktopKeys.KeyStep.ToPost(3, 500), DesktopKeys.keyStep(shown, 10, tops, marked = 2, forward = true))
        // The last post: nowhere to go, the mark stays (not back up to item 7 from the list's place).
        assertNull(DesktopKeys.keyStep(shown, 10, tops, marked = 3, forward = true))
        // k from there walks back the same way, past the top of the view as well.
        assertEquals(DesktopKeys.KeyStep.ToPost(2, 300), DesktopKeys.keyStep(shown, 10, tops, marked = 3, forward = false))
        assertEquals(DesktopKeys.KeyStep.ToPost(0, -100), DesktopKeys.keyStep(shown, 10, tops, marked = 1, forward = false))
    }

    @Test
    fun aShortListThatCannotScrollStillWalksItsPosts() {
        fun item(index: Int, offset: Int, size: Int = 200) = DesktopKeys.ShownItem(index, offset, size)
        // An opened post (0) and two replies, all in view; the focal post marked at the top.
        val shown = listOf(item(0, 0, 300), item(1, 300), item(2, 500), item(3, 700, 40))
        val tops = listOf(0, 300, 500)
        // j from the focal post: the first reply's item, as any step.
        assertEquals(DesktopKeys.KeyStep.ToItem(1, 0), DesktopKeys.keyStep(shown, 4, tops, marked = 0, forward = true))
        // The first reply marked but left below the top: on to the second, and k back to the focal one.
        assertEquals(DesktopKeys.KeyStep.ToPost(2, 500), DesktopKeys.keyStep(shown, 4, tops, marked = 1, forward = true))
        assertEquals(DesktopKeys.KeyStep.ToPost(0, 0), DesktopKeys.keyStep(shown, 4, tops, marked = 1, forward = false))
        // At the very top k has nowhere to go.
        assertNull(DesktopKeys.keyStep(shown, 4, tops, marked = 0, forward = false))
    }

    @Test
    fun aMarkAtTheLineStepsByItemsSaveWithinAThread() {
        fun item(index: Int, offset: Int, size: Int = 300) = DesktopKeys.ShownItem(index, offset, size)
        // Item 2 a thread of two posts (0, 250), marked its first; item 3 after it.
        val shown = listOf(item(2, 0, 500), item(3, 500), item(4, 800))
        val tops = listOf(0, 250, 500, 800)
        assertEquals(DesktopKeys.KeyStep.ToPost(1, 250), DesktopKeys.keyStep(shown, 9, tops, marked = 0, forward = true))
        // A single-post item at the line: the ordinary item step.
        val plain = listOf(item(2, 0), item(3, 300), item(4, 600))
        assertEquals(DesktopKeys.KeyStep.ToItem(3, 0), DesktopKeys.keyStep(plain, 9, listOf(0, 300, 600), marked = 0, forward = true))
        assertEquals(DesktopKeys.KeyStep.ToItem(1, 0), DesktopKeys.keyStep(plain, 9, listOf(0, 300, 600), marked = 0, forward = false))
        // Nothing marked: from the list's place.
        assertEquals(DesktopKeys.KeyStep.ToItem(3, 0), DesktopKeys.keyStep(plain, 9, listOf(0, 300, 600), marked = null, forward = true))
        // Under pinned tabs (48 px): the line is theirs. At it, the next item goes under them.
        val pinned = listOf(item(1, 0, 48), item(4, 48), item(5, 348))
        assertEquals(DesktopKeys.KeyStep.ToItem(5, -48), DesktopKeys.keyStep(pinned, 6, listOf(48, 348, 500), marked = 0, forward = true))
        // The list at its end, item 5's first post marked below them: on to its second.
        assertEquals(DesktopKeys.KeyStep.ToPost(2, 452), DesktopKeys.keyStep(pinned, 6, listOf(48, 348, 500), marked = 1, forward = true))
    }

    @Test
    fun aPostWindowClosesWithCtrlWAndEscOnItsFirstScreen() {
        val escAtRoot = action(DeskKey.ESCAPE, canGoBack = false)
        assertTrue(DesktopKeys.closesWindow(DeskKey.ESCAPE, alt = false, ctrl = false, shift = false, action = escAtRoot))
        val escDeeper = action(DeskKey.ESCAPE, canGoBack = true)
        assertFalse(DesktopKeys.closesWindow(DeskKey.ESCAPE, alt = false, ctrl = false, shift = false, action = escDeeper), "Esc goes back first")
        val ctrlW = action(DeskKey.W, ctrl = true, canGoBack = true)
        assertTrue(DesktopKeys.closesWindow(DeskKey.W, alt = false, ctrl = true, shift = false, action = ctrlW), "Ctrl+W closes it from any screen")
        val ctrlWOverMenu = action(DeskKey.W, ctrl = true, open = true)
        assertFalse(DesktopKeys.closesWindow(DeskKey.W, alt = false, ctrl = true, shift = false, action = ctrlWOverMenu), "what is open closes first")
        assertFalse(DesktopKeys.closesWindow(DeskKey.W, alt = false, ctrl = false, shift = false, action = DeskAction.NONE), "w alone is a letter")
        assertFalse(DesktopKeys.closesWindow(DeskKey.W, alt = false, ctrl = true, shift = true, action = DeskAction.NONE))
        assertFalse(DesktopKeys.closesWindow(DeskKey.Q, alt = false, ctrl = true, shift = false, action = DeskAction.LEAVE_APP), "Ctrl+Q still quits")
    }
}
