package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    ) = DesktopKeys.action(key, alt, ctrl, focusInside, canGoBack, rtl, open)

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
}
