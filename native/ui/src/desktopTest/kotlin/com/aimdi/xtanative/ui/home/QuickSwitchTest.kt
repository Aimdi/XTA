package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** The quick switcher's filter and its keys. */
class QuickSwitchTest {
    private fun place(title: String, kind: QuickKind = QuickKind.SCREEN) = QuickTarget(title, title, kind, QuickGo.Route(title.lowercase()))

    private val places = listOf(
        place("Subscriptions"),
        place("Saved"),
        place("Café group", QuickKind.GROUP),
        place("News of the day", QuickKind.GROUP),
        place("General", QuickKind.SETTINGS),
        place("Daily news", QuickKind.ANTENNA),
    )

    private fun titles(query: String) = QuickSwitch.matching(places, query).map { it.title }

    @Test
    fun aBlankQueryListsEveryPlaceInOrder() {
        assertEquals(places.map { it.title }, titles(""))
        assertEquals(places.map { it.title }, titles("   "))
        assertEquals(2, QuickSwitch.matching(places, "", limit = 2).size)
    }

    @Test
    fun everyWordMustBeThereWhateverItsCaseAndAccents() {
        assertEquals(listOf("Café group"), titles("CAFE"))
        assertEquals(listOf("News of the day", "Daily news"), titles("news"))
        assertEquals(listOf("News of the day"), titles("day news"))
        assertEquals(emptyList(), titles("zzz"))
    }

    @Test
    fun aTitleStartingWithTheQueryComesFirstThenAWordStartingWithIt() {
        // Both start with "s"; the other two only hold one.
        assertEquals(listOf("Subscriptions", "Saved", "News of the day", "Daily news"), titles("s"))
        // A word of "Daily news" starts with "news": after the title that does.
        assertEquals(listOf("News of the day", "Daily news"), titles("new"))
        assertEquals(listOf("General", "Café group"), titles("g"))
        assertEquals(listOf("Daily news"), titles("dai"))
    }

    @Test
    fun theArrowsGoRoundAndPagesStopAtTheEnds() {
        assertEquals(1, QuickSwitch.moved(0, 3, 1))
        assertEquals(0, QuickSwitch.moved(2, 3, 1))
        assertEquals(2, QuickSwitch.moved(0, 3, -1))
        assertEquals(0, QuickSwitch.moved(0, 0, 1))
        assertEquals(2, QuickSwitch.paged(0, 3, 8))
        assertEquals(0, QuickSwitch.paged(2, 3, -8))
        assertEquals(0, QuickSwitch.paged(0, 0, 8))
    }

    @Test
    fun theLabelFindsEveryPlaceOfItsKindAfterThoseNamedSo() {
        val labelled = listOf(
            QuickTarget("g1", "Café", QuickKind.GROUP, QuickGo.Route("group/1"), label = "Groups"),
            QuickTarget("s", "Groups", QuickKind.SETTINGS, QuickGo.Route("settings/groups"), label = "Settings"),
            QuickTarget("a", "News", QuickKind.ANTENNA, QuickGo.Route("antenna/1"), label = "Antennas"),
        )
        assertEquals(listOf("Groups", "Café"), QuickSwitch.matching(labelled, "group").map { it.title })
        assertEquals(listOf("News"), QuickSwitch.matching(labelled, "antennas").map { it.title })
    }

    @Test
    fun theListMovesOnlyAsFarAsTheChosenRowNeeds() {
        // Rows 3 to 9 in view.
        assertEquals(null, QuickSwitch.scrolledTo(5, first = 3, last = 9))
        assertEquals(2, QuickSwitch.scrolledTo(2, first = 3, last = 9))
        assertEquals(4, QuickSwitch.scrolledTo(10, first = 3, last = 9), "row 10 ends the view")
        assertEquals(0, QuickSwitch.scrolledTo(0, first = 8, last = 14), "round to the top")
        assertEquals(10, QuickSwitch.scrolledTo(16, first = 0, last = 6), "round to the end")
    }

    @Test
    fun aPlaceListedTwiceShowsOnce() {
        val twice = listOf(place("Saved"), place("Saved"), place("General"))
        assertEquals(listOf("Saved", "General"), QuickSwitch.distinct(twice).map { it.title })
    }

    @Test
    fun ctrlPAndCtrlKOpenTheSwitcherAndCtrlLStillSearches() {
        fun action(key: DeskKey, focusInside: Boolean = false, open: Boolean = false) =
            DesktopKeys.action(key, alt = false, ctrl = true, focusInside = focusInside, canGoBack = true, somethingOpen = open)
        assertEquals(DeskAction.QUICK_SWITCH, action(DeskKey.P))
        assertEquals(DeskAction.QUICK_SWITCH, action(DeskKey.K))
        assertEquals(DeskAction.QUICK_SWITCH, action(DeskKey.K, focusInside = true), "from a field too, as the search's keys")
        assertEquals(DeskAction.FIND, action(DeskKey.L))
        assertEquals(DeskAction.NONE, action(DeskKey.P, open = true), "not over a sheet")
        assertEquals(DeskAction.NONE, DesktopKeys.action(DeskKey.P, alt = false, ctrl = true, focusInside = false, canGoBack = true, shift = true))
    }
}
