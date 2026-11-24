package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The reading keys on a page that scrolls as a whole (a ScrollState: an article, a settings page), not a lazy list. */
class ScrolledPageKeysTest {
    private fun to(action: DeskAction, value: Int, max: Int = 5000, viewport: Int = 800, line: Float = 64f) =
        DesktopKeys.scrolledTo(action, value, max, viewport, line)

    @Test
    fun aLineIsTheLineAndAScreenLeavesSomeOfWhatWasReadInView() {
        assertEquals(1064, to(DeskAction.LINE_DOWN, 1000))
        assertEquals(936, to(DeskAction.LINE_UP, 1000))
        // 800 less its 12 %: 704.
        assertEquals(1704, to(DeskAction.SCREEN_DOWN, 1000))
        assertEquals(296, to(DeskAction.SCREEN_UP, 1000))
    }

    @Test
    fun homeAndEndGoToTheStartAndTheEnd() {
        assertEquals(0, to(DeskAction.TO_TOP, 1234))
        assertEquals(5000, to(DeskAction.TO_END, 1234))
    }

    @Test
    fun neverPastEitherEnd() {
        assertEquals(5000, to(DeskAction.SCREEN_DOWN, 4900))
        assertEquals(0, to(DeskAction.SCREEN_UP, 100))
        assertEquals(0, to(DeskAction.LINE_UP, 10))
        assertEquals(5000, to(DeskAction.LINE_DOWN, 5000), "at the end: the key is still the page's, it just stays")
    }

    @Test
    fun aPageThatFitsDoesNotMove() {
        DeskAction.entries.forEach { action ->
            val target = to(action, 0, max = 0)
            if (target != null) assertEquals(0, target, "$action")
        }
    }

    @Test
    fun aViewportNotLaidOutYetStillMovesAScreenForward() {
        assertEquals(1, to(DeskAction.SCREEN_DOWN, 0, viewport = 0))
    }

    @Test
    fun jAndKAndEverythingElseLeaveSuchAPageToOthers() {
        assertNull(to(DeskAction.NEXT_ITEM, 1000))
        assertNull(to(DeskAction.PREVIOUS_ITEM, 1000))
        assertNull(to(DeskAction.REFRESH, 1000))
    }

    @Test
    fun aScrolledPageIsWhatTheKeysMoveLikeAList() {
        val actions = DesktopActions()
        val page = ScrollState(0)
        actions.addList(page)
        assertSame(page, actions.readList())
        // A list beside it, pointed at, takes them; pointing back gives them to the page again.
        val list = LazyListState()
        actions.addList(list)
        assertSame(page, actions.readList(), "the first on screen until the pointer says otherwise")
        actions.point(list, Offset(10f, 10f))
        assertSame(list, actions.readList())
        actions.point(page, Offset(20f, 10f))
        assertSame(page, actions.readList())
        actions.removeList(page)
        assertSame(list, actions.readList())
        actions.removeList(list)
        assertNull(actions.readList())
    }
}
