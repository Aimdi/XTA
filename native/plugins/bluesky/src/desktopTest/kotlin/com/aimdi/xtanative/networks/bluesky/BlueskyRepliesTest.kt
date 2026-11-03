package com.aimdi.xtanative.networks.bluesky

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class BlueskyRepliesTest {
    private fun day(date: String) = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC)

    private fun rows(thread: BlueskyThread, authorOnly: Boolean = false, order: BlueskyReplySort = BlueskyReplySort.RELEVANT, collapsed: Set<String> = emptySet()) =
        BlueskyReplies.visible(BlueskyReplies.branches(thread, authorOnly, order), collapsed)

    @Test
    fun authorFocusKeepsTheRepliesLeadingToTheAuthorAndDropsTheRest() {
        val other = "did:plc:other"
        val thread = BlueskyThread(
            post = bluePost("root"),
            ancestors = listOf(bluePost("context")),
            replies = listOf(
                bluePost("question", parent = "root", did = other),
                bluePost("answer", parent = "question"),
                bluePost("other", parent = "root", did = other),
                bluePost("followup", parent = "answer"),
                bluePost("context"),
            ),
        )
        val shown = rows(thread, authorOnly = true)
        assertEquals(listOf("question", "answer", "followup"), shown.map { it.branch.post.text })
        assertEquals(listOf(true, false, false), shown.map { it.branch.contextOnly })
        assertEquals(listOf(0, 1, 2), shown.map { it.depth })
        assertEquals(2, shown.first().branch.descendants)
        assertEquals(1, rows(thread, authorOnly = true, collapsed = setOf(uriOf("question"))).size)
    }

    @Test
    fun replyOrderSortsSiblingsStablyAndKeepsTheirRepliesUnderThem() {
        val thread = BlueskyThread(
            post = bluePost("root"),
            replies = listOf(
                bluePost("a", parent = "root", likes = 10, publishedAt = day("2026-01-01")),
                bluePost("b", parent = "root", likes = 20, publishedAt = day("2026-01-03")),
                bluePost("a1", parent = "a", likes = 100, publishedAt = day("2026-01-04")),
                bluePost("c", parent = "root", likes = 20, publishedAt = day("2026-01-02")),
                bluePost("undated", parent = "root"),
            ),
        )
        fun order(sort: BlueskyReplySort) = rows(thread, order = sort).map { it.branch.post.text }
        assertEquals(listOf("a", "a1", "b", "c", "undated"), order(BlueskyReplySort.RELEVANT))
        assertEquals(listOf("b", "c", "a", "a1", "undated"), order(BlueskyReplySort.MOST_LIKED))
        assertEquals(listOf("b", "c", "a", "a1", "undated"), order(BlueskyReplySort.RECENT))
        assertEquals(listOf("a", "a1", "c", "b", "undated"), order(BlueskyReplySort.OLDEST))
    }

    @Test
    fun twentyThousandNestedRepliesBuildCountAndCollapseWithoutRecursion() {
        val replies = (0 until 20_000).map { bluePost("reply-$it", parent = if (it == 0) "root" else "reply-${it - 1}") }
        val branches = BlueskyReplies.branches(BlueskyThread(post = bluePost("root"), replies = replies))
        assertEquals(19_999, branches.single().descendants)
        val expanded = BlueskyReplies.visible(branches, emptySet())
        assertEquals(20_000, expanded.size)
        assertEquals(19_999, expanded.last().depth)
        assertEquals(1, BlueskyReplies.visible(branches, setOf(replies.first().uri)).size)
    }

    @Test
    fun collapsingKeepsSiblingsOrphansAndCyclicPostsExactlyOnce() {
        val thread = BlueskyThread(
            post = bluePost("root"),
            replies = listOf(
                bluePost("a", parent = "root"),
                bluePost("b", parent = "root"),
                bluePost("a1", parent = "a"),
                bluePost("orphan", parent = "missing"),
                bluePost("cycle-a", parent = "cycle-b"),
                bluePost("cycle-b", parent = "cycle-a"),
                bluePost("a", parent = "root"),
                bluePost("root"),
            ),
        )
        val branches = BlueskyReplies.branches(thread)
        val expanded = BlueskyReplies.visible(branches, emptySet())
        assertEquals(listOf("a", "a1", "b"), expanded.take(3).map { it.branch.post.text })
        assertEquals(listOf("a", "a1", "b", "orphan", "cycle-a", "cycle-b"), expanded.map { it.branch.post.text })
        assertEquals(1, expanded.first().branch.descendants)
        val collapsed = BlueskyReplies.visible(branches, setOf(uriOf("a"))).map { it.branch.post.text }
        assertEquals(listOf("a", "b", "orphan", "cycle-a", "cycle-b"), collapsed)
        assertEquals(listOf(true, false), BlueskyReplies.visible(branches, setOf(uriOf("a"), uriOf("b"))).take(2).map { it.collapsed }, "a reply without replies does not fold")
    }

    @Test
    fun collapseAllFoldsEveryReplyWithRepliesUnderIt() {
        val thread = BlueskyThread(
            post = bluePost("root"),
            replies = listOf(bluePost("a", parent = "root"), bluePost("a1", parent = "a"), bluePost("a2", parent = "a1"), bluePost("b", parent = "root")),
        )
        assertEquals(setOf(uriOf("a"), uriOf("a1")), BlueskyReplies.foldable(BlueskyReplies.branches(thread)))
    }

    @Test
    fun aParsedThreadBuildsItsTreeFromWhereEachReplySits() {
        val thread = BlueskyParser.thread(fixtureJson("post_thread.json"))!!
        val shown = BlueskyReplies.visible(BlueskyReplies.branches(thread), emptySet())
        assertEquals(listOf("first reply" to 0, "nested" to 1, "the author again" to 0), shown.map { it.branch.post.text to it.depth })
        val authorOnly = BlueskyReplies.visible(BlueskyReplies.branches(thread, authorOnly = true), emptySet())
        assertEquals(listOf("the author again"), authorOnly.map { it.branch.post.text })
    }
}
