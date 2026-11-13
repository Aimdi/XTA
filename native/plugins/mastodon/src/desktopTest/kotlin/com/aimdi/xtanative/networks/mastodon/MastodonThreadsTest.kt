package com.aimdi.xtanative.networks.mastodon

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** A conversation laid out, with the rules of XTA's mastodon_thread_reader_test.dart and mastodon_reading_experience_test.dart. */
class MastodonThreadsTest {
    private fun post(id: String, parent: String? = null, acct: String = "writer@studio.example", hour: Int? = null, likes: Long = 0) = MastodonPost(
        id = id,
        replyToId = parent,
        acct = acct,
        authorName = acct.substringBefore('@'),
        text = "Post $id",
        url = "https://studio.example/@writer/$id",
        publishedAt = hour?.let { Instant.parse("2026-09-01T%02d:00:00Z".format(it)) },
        favouritesCount = likes,
    )

    private val conversation = MastodonThread(
        status = post("root", parent = "parent"),
        ancestors = listOf(post("parent", parent = "grandparent"), post("grandparent")),
        descendants = listOf(
            post("bridge", parent = "root", acct = "reader@studio.example"),
            post("unrelated", parent = "root", acct = "other@studio.example"),
            post("continuation", parent = "bridge"),
            post("nested", parent = "continuation"),
        ),
    )

    @Test
    fun authorFocusKeepsTheRepliesLeadingToTheAuthorsOwn() {
        val rows = MastodonThreads.replies(conversation, authorOnly = true)
        assertEquals(listOf("bridge", "continuation", "nested"), rows.map { it.post.id })
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
        assertEquals(listOf(true, false, false), rows.map { it.contextOnly })
        assertEquals(listOf(2, 1, 0), rows.map { it.descendants })
        assertEquals(2, MastodonThreads.replies(conversation, setOf("bridge"), authorOnly = true).single().descendants)
    }

    @Test
    fun theAuthorIsMatchedWithoutCaseAndNotOnAnotherServer() {
        val thread = MastodonThread(
            status = post("root"),
            descendants = listOf(post("same", parent = "root", acct = "WRITER@STUDIO.EXAMPLE"), post("different", parent = "root", acct = "writer@other.example")),
        )
        assertEquals("same", MastodonThreads.replies(thread, authorOnly = true).single().post.id)
    }

    @Test
    fun ancestorsFollowTheirReplyLinksWithoutRepeatingTheOpenedPost() {
        assertEquals(listOf("grandparent", "parent"), MastodonThreads.ancestors(conversation).map { it.id })
        val cyclic = MastodonThread(
            status = post("root", parent = "a"),
            ancestors = listOf(post("a", parent = "b"), post("b", parent = "a"), post("a", parent = "b"), post("root"), post("extra")),
        )
        assertEquals(listOf("extra", "b", "a"), MastodonThreads.ancestors(cyclic).map { it.id })
    }

    @Test
    fun duplicateCyclicOrphanAndAncestorRepliesStayFinite() {
        val thread = MastodonThread(
            status = post("root"),
            ancestors = listOf(post("ancestor")),
            descendants = listOf(
                post("a", parent = "b"),
                post("b", parent = "a"),
                post("a", parent = "b"),
                post("orphan", parent = "missing"),
                post("self", parent = "self"),
                post("ancestor"),
                post("root"),
            ),
        )
        val rows = MastodonThreads.replies(thread)
        assertEquals(listOf("orphan", "self", "a", "b"), rows.map { it.post.id })
        assertEquals(listOf(0, 0, 1, 0), rows.map { it.descendants })
        assertEquals(listOf("orphan", "self", "a"), MastodonThreads.replies(thread, setOf("a")).map { it.post.id })
    }

    @Test
    fun aDeepConversationIsWalkedWithoutRecursion() {
        val thread = MastodonThread(status = post("root"), descendants = (0 until 10_000).map { post("$it", parent = if (it == 0) "root" else "${it - 1}") })
        val rows = MastodonThreads.replies(thread)
        assertEquals(10_000, rows.size)
        assertEquals(9_999, rows.first().descendants)
        assertEquals(9_999, rows.last().depth)
        assertEquals(1, MastodonThreads.replies(thread, setOf("0")).size)
    }

    @Test
    fun branchesKeepTheirSiblingOrderAndFoldOnlyTheirOwnReplies() {
        val thread = MastodonThread(
            status = post("root"),
            descendants = listOf(post("a", "root"), post("b", "root"), post("a1", "a"), post("a2", "a1"), post("orphan", "gone")),
        )
        assertEquals(listOf("a", "a1", "a2", "b", "orphan"), MastodonThreads.replies(thread).map { it.post.id })
        val folded = MastodonThreads.replies(thread, setOf("a"))
        assertEquals(listOf("a", "b", "orphan"), folded.map { it.post.id })
        assertEquals(2, folded.first().descendants)
        assertEquals(true, folded.first().collapsed)
    }

    @Test
    fun cyclicAndDuplicateRepliesNeitherLoopNorRepeat() {
        val thread = MastodonThread(status = post("root"), descendants = listOf(post("a", "b"), post("b", "a"), post("a", "b"), post("root", "a")))
        val rows = MastodonThreads.replies(thread)
        assertEquals(setOf("a", "b"), rows.map { it.post.id }.toSet())
        assertEquals(2, rows.size)
    }

    @Test
    fun siblingsAreOrderedAsAsked() {
        val thread = MastodonThread(
            status = post("root"),
            descendants = listOf(post("early", "root", hour = 8, likes = 1), post("undated", "root", likes = 9), post("late", "root", hour = 10, likes = 5)),
        )
        fun order(order: MastodonReplyOrder) = MastodonThreads.replies(thread, order = order).map { it.post.id }
        assertEquals(listOf("early", "undated", "late"), order(MastodonReplyOrder.OLDEST), "the conversation as the server sent it")
        assertEquals(listOf("late", "early", "undated"), order(MastodonReplyOrder.RECENT), "undated last")
        assertEquals(listOf("undated", "late", "early"), order(MastodonReplyOrder.MOST_LIKED))
    }
}
