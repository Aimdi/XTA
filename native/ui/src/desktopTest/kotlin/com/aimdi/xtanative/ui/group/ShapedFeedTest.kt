package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ShapedFeedTest {
    private val threads = (0 until 5).map { XThread("t$it", listOf(XPost("t$it", XUser("1", "a", "A"), null, "post $it"))) }
    private val feed = GroupFeedUi(run = FeedRun(shown = FeedShown(threads = threads), jump = FeedJump(threadIndex = 3, offset = 7, token = 1)))

    @Test
    fun withoutAFilterTheFeedIsShownAsItIs() {
        val shaped = ShapedFeed.of(feed, null)
        assertSame(feed, shaped.state)
        assertEquals(2, shaped.original(2))
    }

    @Test
    fun aColumnShowsTheKeptThreadsAndKeepsTheFeedsOwnPlace() {
        val shaped = ShapedFeed.of(feed) { it.id != "t1" && it.id != "t2" }
        assertEquals(listOf("t0", "t3", "t4"), shaped.state.run.shown.threads.map { it.id })
        assertEquals(1, shaped.state.run.jump?.threadIndex, "the jump to t3 lands on t3")
        assertEquals(7, shaped.state.run.jump?.offset)
        assertEquals(3, shaped.original(1), "the second thread shown is the feed's fourth")
        assertEquals(4, shaped.original(2))
        assertEquals(0, shaped.original(0))
    }
}
