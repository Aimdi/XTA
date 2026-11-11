package com.aimdi.xtanative.networks.threads

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A post page arranged around its post, and its replies in order (XTA's threads_conversation_test). */
class ThreadsConversationTest {
    private fun post(id: String, handle: String = "zuck") =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "post $id", url = "https://www.threads.com/@$handle/post/C$id")

    private fun ids(chains: List<List<ThreadsPost>>) = chains.map { chain -> chain.map { it.id } }

    @Test
    fun theFocusChainSplitsIntoWhatItAnswersThePostAndItsContinuation() {
        val conversation = ThreadsConversation.from(
            listOf(
                listOf(post("1", "meta"), post("2"), post("3"), post("4", "other")),
                listOf(post("5", "a"), post("6", "b")),
            ),
            post("2"),
        )
        assertEquals(listOf("1"), conversation.ancestors.map { it.id })
        assertEquals("2", conversation.focus.id)
        assertEquals(listOf("3"), conversation.continuation.map { it.id })
        assertEquals(listOf(listOf("4"), listOf("5", "6")), ids(conversation.replies))
        assertEquals(3, conversation.replyCount)
        assertTrue(conversation.loaded)
    }

    @Test
    fun aRealPostPageArrangesTheSameWay() {
        val seed = ThreadsPost.linkStub("https://www.threads.net/@zuck/post/DPzuck0002?xmt=AQG", "zuck")
        val conversation = ThreadsConversation.from(ThreadsPages.chains(fixture("post_page.html")), seed)
        assertEquals("502", conversation.focus.id)
        assertEquals("Threads turns three", conversation.focus.text)
        assertEquals(listOf("501"), conversation.ancestors.map { it.id })
        assertEquals(listOf("503"), conversation.continuation.map { it.id })
        assertEquals(listOf(listOf("504"), listOf("505", "506"), listOf("507")), ids(conversation.replies))
        assertFalse(conversation.focusIsStub)
    }

    @Test
    fun aLinkStubFindsItsPostByShortCodeWhateverTheHost() {
        val stub = ThreadsPost.linkStub("https://www.threads.net/@zuck/post/C2?igshid=x", "zuck")
        assertTrue(ThreadsConversation.seed(stub).focusIsStub)
        val conversation = ThreadsConversation.from(listOf(listOf(post("1", "meta"), post("2"))), stub)
        assertEquals("2", conversation.focus.id)
        assertEquals("post 2", conversation.focus.text)
        assertEquals(listOf("1"), conversation.ancestors.map { it.id })
    }

    @Test
    fun aShortLinkWhoseCodeIsNotOnThePageFocusesTheFirstRoot() {
        val conversation = ThreadsConversation.from(listOf(listOf(post("9")), listOf(post("10", "a"))), ThreadsPost.linkStub("https://www.threads.com/t/Short"))
        assertEquals("9", conversation.focus.id)
        assertEquals(listOf(listOf("10")), ids(conversation.replies))
    }

    @Test
    fun anOpenedRepostKeepsSayingWhoRepostedIt() {
        val repost = post("2").repostedBy(id = "outer", handle = "friend", name = "Friend")
        val conversation = ThreadsConversation.from(listOf(listOf(post("2"))), repost)
        assertEquals("Friend", conversation.focus.reposterDisplayName)
        assertEquals("friend", conversation.focus.repostedByHandle)
    }

    @Test
    fun anEmptyPageKeepsTheSeed() {
        val conversation = ThreadsConversation.from(listOf(emptyList()), post("1"))
        assertEquals("1", conversation.focus.id)
        assertTrue(conversation.replies.isEmpty())
        assertTrue(conversation.loaded)
    }

    private val long = ThreadsConversation(
        focus = post("1"),
        replies = listOf(
            listOf(post("2", "a"), post("3", "b"), post("4", "a"), post("5", "b")),
            listOf(post("6", "c"), post("7")),
        ),
        loaded = true,
    )

    private fun shownIds(rows: List<ThreadsReplyRow>) = rows.filterIsInstance<ThreadsReplyRow.Post>().map { it.post.id }

    @Test
    fun aLongReplyThreadIsCutUntilItIsOpened() {
        val rows = long.replyRows()
        assertEquals(listOf("2", "3", "4", "6", "7"), shownIds(rows))
        assertEquals(1, rows.filterIsInstance<ThreadsReplyRow.More>().single().hidden)
        assertTrue(rows.filterIsInstance<ThreadsReplyRow.Post>().single { it.post.id == "4" }.connectBottom, "the rail runs on to the rest")
        val first = rows.filterIsInstance<ThreadsReplyRow.Post>().first()
        assertFalse(first.connectTop)
        assertTrue(first.connectBottom)
    }

    @Test
    fun anOpenedThreadShowsEverything() {
        val rows = long.replyRows(ThreadsThreadView(expanded = setOf("2")))
        assertTrue(rows.none { it is ThreadsReplyRow.More })
        assertEquals(6, rows.size)
        assertFalse(rows.filterIsInstance<ThreadsReplyRow.Post>().single { it.post.id == "5" }.connectBottom)
    }

    @Test
    fun theAuthorsRepliesAreTheThreadsTheAuthorTookPartIn() {
        assertEquals(listOf("6", "7"), shownIds(long.replyRows(ThreadsThreadView(authorOnly = true))))
        assertEquals(listOf(listOf("6", "7")), ids(long.authorReplies))
    }

    @Test
    fun theOpenedPostsAuthorsRepliesAreMarkedAndNoOneElses() {
        val marked = long.replyRows().filterIsInstance<ThreadsReplyRow.Post>().filter { long.byAuthor(it.post) }.map { it.post.id }
        assertEquals(listOf("7"), marked)
        assertFalse(ThreadsConversation(focus = post("x", handle = "")).byAuthor(post("y", handle = "")), "a stub names no author")
    }

    private fun on(id: String, day: Int, handle: String = "a") = ThreadsPost(
        id = id,
        handle = handle,
        authorName = handle,
        text = "post $id",
        publishedAt = LocalDate.of(2026, 10, day).atStartOfDay().toInstant(ZoneOffset.UTC),
        likeCount = day.toLong(),
    )

    private val ordered = ThreadsConversation(
        focus = post("1"),
        replies = listOf(listOf(on("2", 3), on("3", 9, "b")), listOf(on("4", 5)), listOf(on("5", 1, "zuck"))),
        loaded = true,
    )

    private fun order(view: ThreadsThreadView) = shownIds(ordered.replyRows(view))

    @Test
    fun topKeepsTheOrderMetaSent() {
        assertEquals(listOf("2", "3", "4", "5"), order(ThreadsThreadView()))
    }

    @Test
    fun newestAndOldestMoveEachReplyThreadWholeByItsFirstReply() {
        assertEquals(listOf("4", "2", "3", "5"), order(ThreadsThreadView(order = ThreadsReplyOrder.NEWEST)))
        assertEquals(listOf("5", "2", "3", "4"), order(ThreadsThreadView(order = ThreadsReplyOrder.OLDEST)))
    }

    @Test
    fun theOrderAppliesToTheAuthorsRepliesToo() {
        assertEquals(listOf("5"), order(ThreadsThreadView(authorOnly = true, order = ThreadsReplyOrder.OLDEST)))
    }

    @Test
    fun undatedReplyThreadsSortLastEitherWay() {
        val undated = ThreadsConversation(focus = post("1"), replies = listOf(listOf(post("u")), listOf(on("d", 2))), loaded = true)
        assertEquals(listOf("d", "u"), undated.orderedReplies(ThreadsReplyOrder.NEWEST).map { it.first().id })
        assertEquals(listOf("d", "u"), undated.orderedReplies(ThreadsReplyOrder.OLDEST).map { it.first().id })
    }

    @Test
    fun aRealPageSortsItsReplyThreadsByTheirFirstReply() {
        val conversation = ThreadsConversation.from(ThreadsPages.chains(fixture("post_page.html")), post("502"))
        assertEquals(listOf("505", "504", "507"), conversation.orderedReplies(ThreadsReplyOrder.NEWEST).map { it.first().id })
        assertEquals(listOf("507", "504", "505"), conversation.orderedReplies(ThreadsReplyOrder.OLDEST).map { it.first().id })
        assertEquals(Instant.ofEpochSecond(1761000600), conversation.focus.publishedAt)
    }
}
