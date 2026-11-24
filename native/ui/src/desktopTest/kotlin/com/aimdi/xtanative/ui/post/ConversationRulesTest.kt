package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XWithheld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRulesTest {
    private val focal = post("10", by = "maya")

    private fun conversation(replies: List<XThread>, cursor: String? = null, showMore: String? = null) =
        XConversation(before = listOf(post("9", by = "maya")), focal = focal, replies = replies, repliesCursor = cursor, showMoreCursor = showMore)

    private fun page(replies: List<XThread>, cursor: String? = null, showMore: String? = null) =
        XConversation(emptyList(), null, replies, cursor, showMore)

    @Test
    fun theFirstPageKeepsItsRepliesAndReadsOnWithItsCursor() {
        val paging = ConversationPaging("10")
        val step = paging.first(conversation(listOf(thread(post("11")), thread(post("12"))), cursor = "c1"), expectedReplies = 2)
        assertEquals(listOf("11", "12"), step.replies.map { it.id })
        assertEquals("c1", step.next)
        assertNull(step.showMore, "offered only where the thread ends")
        assertFalse(step.repliesMissing)
    }

    @Test
    fun repliesXSendsAgainAreDroppedAndAThreadAddedToGetsAnIdOfItsOwn() {
        val paging = ConversationPaging("10")
        paging.first(conversation(listOf(XThread("t1", listOf(post("11"), post("12")))), cursor = "c1"), expectedReplies = 3)
        val step = paging.next(page(listOf(XThread("t1", listOf(post("12"), post("13"))), thread(post("11"))), cursor = "c2"), cursor = "c1")
        assertEquals(listOf("t1-13"), step.replies.map { it.id })
        assertEquals(listOf("13"), step.replies.single().posts.map { it.id })
        assertEquals("c2", step.next)
    }

    @Test
    fun aPageWithNothingNewOrTheSameCursorEndsTheConversation() {
        val paging = ConversationPaging("10")
        paging.first(conversation(listOf(thread(post("11"))), cursor = "c1"), expectedReplies = 1)
        val again = paging.next(page(listOf(thread(post("11"))), cursor = "c2"), cursor = "c1")
        assertTrue(again.replies.isEmpty())
        assertNull(again.next)
        val repeated = paging.next(page(listOf(thread(post("12"))), cursor = "c1"), cursor = "c1")
        assertEquals(listOf("12"), repeated.replies.map { it.id }, "what it brought is kept")
        assertNull(repeated.next)
    }

    @Test
    fun theShowMorePromptComesWhereTheThreadEndsAndNeverForTheCursorJustFollowed() {
        val paging = ConversationPaging("10")
        val first = paging.first(conversation(listOf(thread(post("11"))), cursor = null, showMore = "more"), expectedReplies = 4)
        assertEquals("more", first.showMore)
        val followed = paging.next(page(listOf(thread(post("12"))), cursor = null, showMore = "more"), cursor = "more")
        assertNull(followed.showMore)
        assertNull(followed.next)
    }

    @Test
    fun repliesAllHeldBehindShowMoreAreFollowedAtOnce() {
        val step = ConversationPaging("10").first(conversation(emptyList(), cursor = null, showMore = "more"), expectedReplies = 5)
        assertEquals("more", step.next)
        assertNull(step.showMore)
        assertFalse(step.repliesMissing)
    }

    @Test
    fun aCountWithNoRepliesAndNoWayToThemOffersARetry() {
        assertTrue(ConversationPaging("10").first(conversation(emptyList()), expectedReplies = 3).repliesMissing)
        assertFalse(ConversationPaging("10").first(conversation(emptyList()), expectedReplies = 0).repliesMissing)
        assertFalse(ConversationPaging("10").first(conversation(emptyList(), cursor = "c"), expectedReplies = 3).repliesMissing)
    }

    @Test
    fun aWithheldReplyKeepsItsPlaceAndTheOpenedPostIsNeverAReply() {
        val withheld = XThread("t9", emptyList(), listOf(XWithheld("15", "This post is unavailable.")))
        val step = ConversationPaging("10").first(conversation(listOf(thread(focal, post("11")), withheld)), expectedReplies = 2)
        assertEquals(listOf(listOf("11"), emptyList()), step.replies.map { thread -> thread.posts.map { it.id } })
        assertEquals("15", step.replies[1].withheld.single().id)
    }

    @Test
    fun repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue() {
        val a = thread(post("20", replyToId = "10"))
        val b = thread(post("100", replyToId = "10"))
        val a1 = thread(post("21", replyToId = "20"))
        val a1x = thread(post("22", replyToId = "21"))
        val orphan = thread(post("30", replyToId = "999"))
        val nodes = ThreadTree.nodes(listOf(b, a1x, a1, a, orphan))
        assertEquals(listOf("20" to 1, "21" to 2, "22" to 3, "30" to 1, "100" to 1), nodes.map { it.thread.id to it.depth })

        val rows = ThreadTree.rows(nodes)
        assertEquals(5, rows.size)
        val continueRow = rows[2] as ThreadRow.Continue
        assertEquals("22", continueRow.target.thread.id)
        assertEquals(2, continueRow.indentDepth)
        val first = rows[0] as ThreadRow.Reply
        val second = rows[1] as ThreadRow.Reply
        assertFalse(first.connectTop)
        assertTrue(first.connectBottom, "its answer is below")
        assertTrue(second.connectTop)
        assertTrue(second.connectBottom, "the continue row is its child")
        assertFalse((rows[4] as ThreadRow.Reply).connectBottom)
        assertEquals(0, ThreadTree.indentLevels(1))
        assertEquals(1, ThreadTree.indentLevels(2))
    }

    @Test
    fun onlyXsOwnOrderIsKeptAndNeverACountWithNothingUnderIt() {
        assertTrue(ThreadCache.keeps(ReplySort.RELEVANT))
        assertTrue(ThreadCache.keeps(ReplySort.OLDEST), "X has no oldest-first: its own order is read")
        assertFalse(ThreadCache.keeps(ReplySort.RECENT))
        assertFalse(ThreadCache.keeps(ReplySort.MOST_LIKED))
        assertTrue(ThreadCache.worthKeeping(conversation(listOf(thread(post("11")))), expectedReplies = 1))
        assertTrue(ThreadCache.worthKeeping(conversation(emptyList(), showMore = "more"), expectedReplies = 3))
        assertTrue(ThreadCache.worthKeeping(conversation(emptyList()), expectedReplies = 0), "a post without replies is kept as it is")
        assertFalse(ThreadCache.worthKeeping(conversation(emptyList(), cursor = "c"), expectedReplies = 3), "a bare cursor would stick a page with no replies")
        assertEquals(10 * 60 * 1000L, ThreadCache.MAX_AGE_MS)
    }

    @Test
    fun aKeptPageReadsBackWithItsCursors() {
        val page = com.aimdi.xtanative.core.x.model.XTimelinePage(listOf(thread(post("1"))), "bottom", "top", showMoreCursor = "more")
        val back = page.cached().page()
        assertEquals(page.threads, back.threads)
        assertEquals(listOf("bottom", "top", "more"), listOf(back.bottomCursor, back.topCursor, back.showMoreCursor))
    }

    @Test
    fun aReplyCycleCannotLoopAndEveryThreadShowsOnce() {
        val x = thread(post("41", replyToId = "42"))
        val y = thread(post("42", replyToId = "41"))
        val nodes = ThreadTree.nodes(listOf(x, y))
        assertEquals(setOf("41", "42"), nodes.map { it.thread.id }.toSet())
        assertEquals(2, nodes.size)
    }


    @Test
    fun aCollapsedReplyIsOneRowForItselfAndAllUnderIt() {
        val a = XThread("20", listOf(post("20", replyToId = "10"), post("23", replyToId = "20")))
        val a1 = thread(post("21", replyToId = "20"))
        val a1x = thread(post("22", replyToId = "21"))
        val a2 = thread(post("24", replyToId = "23"))
        val b = thread(post("100", replyToId = "10"))
        val nodes = ThreadTree.nodes(listOf(a, a1, a1x, a2, b))
        val open = ThreadTree.rows(nodes)
        assertEquals(listOf("reply-20", "reply-21", "continue-22", "reply-24", "reply-100"), open.map(ThreadTree::key))

        val rows = ThreadTree.rows(nodes, collapsed = setOf("20"))
        assertEquals(listOf("reply-20", "reply-100"), rows.map(ThreadTree::key), "the reply keeps its key, so the list stays at it")
        val row = rows[0] as ThreadRow.Collapsed
        assertEquals("20", row.node.thread.id)
        assertEquals(1, row.visualDepth)
        assertEquals(4, row.hidden, "its two posts and the two replies drawn under it; what Continue thread led to was not on the screen")
        assertEquals(open.last(), rows.last(), "the next branch is as it was")
        assertEquals(ThreadTree.replyKey("20"), ThreadTree.key(row))
    }

    @Test
    fun aBranchCollapsesInsideAnOpenOneAndOpensAgainAsItWas() {
        val a = thread(post("20", replyToId = "10"))
        val a1 = thread(post("21", replyToId = "20"))
        val a1x = thread(post("22", replyToId = "21"))
        val a2 = thread(post("25", replyToId = "20"))
        val nodes = ThreadTree.nodes(listOf(a, a1, a1x, a2))
        val inner = ThreadTree.toggled(emptySet(), "21")
        val rows = ThreadTree.rows(nodes, inner)
        assertEquals(listOf("reply-20", "reply-21", "reply-25"), rows.map(ThreadTree::key), "its Continue thread goes with it")
        val collapsed = rows[1] as ThreadRow.Collapsed
        assertEquals(2, collapsed.visualDepth)
        assertEquals(1, collapsed.hidden)
        assertTrue((rows[0] as ThreadRow.Reply).connectBottom, "the collapsed row is still its answer")

        // Collapsed inside a collapsed branch: the outer row stands for both, and opening it finds the inner one as left.
        val both = ThreadTree.toggled(inner, "20")
        assertEquals(3, (ThreadTree.rows(nodes, both).single() as ThreadRow.Collapsed).hidden)
        assertEquals(rows, ThreadTree.rows(nodes, ThreadTree.toggled(both, "20")))
        assertEquals(ThreadTree.rows(nodes), ThreadTree.rows(nodes, ThreadTree.toggled(inner, "21")))
    }

    @Test
    fun collapsedBranchesTakeInWhatLoadsLaterAndIdsNotLoadedDoNothing() {
        val a = thread(post("20", replyToId = "10"))
        val b = thread(post("100", replyToId = "10"))
        val collapsed = setOf("20", "gone")
        val before = ThreadTree.rows(ThreadTree.nodes(listOf(a, b)), collapsed)
        assertEquals(1, (before[0] as ThreadRow.Collapsed).hidden)
        // The next page brings an answer to the collapsed reply, and another order of the same replies.
        val later = ThreadTree.rows(ThreadTree.nodes(listOf(b, thread(post("26", replyToId = "20")), a)), collapsed)
        assertEquals(before.map(ThreadTree::key), later.map(ThreadTree::key), "the list's keys do not move")
        assertEquals(2, (later[0] as ThreadRow.Collapsed).hidden)
        assertEquals(ThreadTree.rows(ThreadTree.nodes(listOf(b))), ThreadTree.rows(ThreadTree.nodes(listOf(b)), collapsed))
    }

    @Test
    fun aCollapsedBranchCountsThePostsXWithheldInIt() {
        val a = XThread("20", listOf(post("20", replyToId = "10")), listOf(XWithheld("27", null)))
        assertEquals(2, (ThreadTree.rows(ThreadTree.nodes(listOf(a)), setOf("20")).single() as ThreadRow.Collapsed).hidden)
    }
}
