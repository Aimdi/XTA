package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The post j or k marked is acted on only in the list the reading keys move, and is forgotten when they move to another. */
class MarkedPostTest {
    /** Laid out and attached, as a post drawn on screen is. */
    private object Drawn : LayoutCoordinates {
        override val size = IntSize(100, 100)
        override val providedAlignmentLines: Set<AlignmentLine> = emptySet()
        override val parentLayoutCoordinates: LayoutCoordinates? = null
        override val parentCoordinates: LayoutCoordinates? = null
        override val isAttached = true
        override fun windowToLocal(relativeToWindow: Offset) = relativeToWindow
        override fun localToWindow(relativeToLocal: Offset) = relativeToLocal
        override fun localToRoot(relativeToLocal: Offset) = relativeToLocal
        override fun localPositionOf(sourceCoordinates: LayoutCoordinates, relativeToSource: Offset) = relativeToSource
        override fun localBoundingBoxOf(sourceCoordinates: LayoutCoordinates, clipBounds: Boolean) = Rect.Zero
        override fun get(alignmentLine: AlignmentLine) = AlignmentLine.Unspecified
    }

    private val keys = PostKeys({}, {}, {}, {}, {})

    private fun post(list: LazyListState) = KeyedPost(list) { keys }.also { it.place = Drawn }

    /** Two columns side by side, each with a list and a post marked in the first. */
    private fun columns(): Triple<DesktopActions, Pair<LazyListState, LazyListState>, KeyedPost> {
        val actions = DesktopActions()
        val left = LazyListState()
        val right = LazyListState()
        actions.addList(left, "left")
        actions.addList(right, "right")
        actions.takeColumns(Any()) { listOf("left", "right") }
        val marked = post(left)
        actions.addPost(marked)
        actions.addPost(post(right))
        actions.markPost(marked)
        return Triple(actions, left to right, marked)
    }

    @Test
    fun theMarkedPostIsTheOneActedOnInItsOwnList() {
        val (actions, lists, marked) = columns()
        assertSame(marked, actions.markedIn(lists.first))
        assertNull(actions.markedIn(lists.second), "a post marked in another column is not the right one's")
    }

    @Test
    fun ctrlArrowsToAnotherColumnUnmarkIt() {
        val (actions, lists, _) = columns()
        actions.stepColumn(1)
        assertSame(lists.second, actions.readList())
        assertNull(actions.markedPost)
        assertNull(actions.currentPost(), "l, b, Enter act on nothing in the left column")
    }

    @Test
    fun aColumnOpenedBesideUnmarksIt() {
        val (actions, _, _) = columns()
        actions.readOpened("right")
        assertNull(actions.markedPost)
    }

    @Test
    fun turningOrJumpingToAnotherTabUnmarksIt() {
        val (actions, lists, marked) = columns()
        actions.take(Any(), refresh = {}, turn = {}, isOpen = { false }, close = {}, jump = {})
        actions.turn(1)
        assertNull(actions.markedPost)
        actions.markPost(marked)
        actions.jump(2)
        assertNull(actions.markedIn(lists.first))
    }

    @Test
    fun thePointerOverAnotherListUnmarksIt() {
        val (actions, lists, _) = columns()
        actions.point(lists.second, Offset(10f, 10f))
        assertNull(actions.markedPost)
    }
}
