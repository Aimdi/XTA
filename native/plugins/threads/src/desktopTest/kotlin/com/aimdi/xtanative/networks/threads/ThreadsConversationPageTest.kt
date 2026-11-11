package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A post page as Meta now preloads it: the post, what it answers, its nested replies, and posts that are not part of it. */
class ThreadsConversationPageTest {
    private val page by lazy { ThreadsPostPage.read(fixture("post_page_queries.html")) }

    private fun ids(posts: List<ThreadsPost>) = posts.map { it.id }

    @Test
    fun theTargetQueryNamesThePostAndTheUpwardOneWhatItAnswers() {
        assertEquals("602", page.target?.id)
        assertEquals(listOf("601"), ids(page.above))
        assertEquals(listOf("603"), ids(page.continuation))
    }

    @Test
    fun eachReplyEdgeIsAThreadWithItsLevelsAndWhetherItGoesOn() {
        assertEquals(listOf(listOf("611", "612", "613", "614"), listOf("621")), page.replies.map { ids(it.posts) })
        assertEquals(mapOf("611" to 2, "612" to 3, "613" to 3, "614" to 4), page.replies[0].levels)
        assertTrue(page.replies[0].hasMore)
        assertFalse(page.replies[1].hasMore)
        assertTrue(page.moreReplies)
        assertEquals("DQquot0699", page.replies[1].posts.single().quoted?.shortcode, "a quoted post stays inside its reply")
    }

    @Test
    fun relatedPostsAreNeverTakenForReplies() {
        assertEquals(listOf("681", "682"), ids(page.related))
        val all = page.conversationChains.flatten().map { it.id }
        assertTrue("681" !in all && "682" !in all)
        assertTrue("690" !in all, "a reply to the root beside this post is not this post's")
    }

    @Test
    fun aShortLinkLandsOnThePostThePageNames() {
        val conversation = ThreadsConversation.from(page, ThreadsPost.linkStub("https://www.threads.com/t/Short"))
        assertEquals("602", conversation.focus.id)
        assertEquals(listOf("601"), ids(conversation.ancestors))
        assertEquals(listOf("681", "682"), ids(conversation.related))
        assertEquals(setOf("611"), conversation.unfinished)
        assertTrue(conversation.moreReplies)
    }

    @Test
    fun repliesNestByTheirLevelAndAnOnlyAnswerContinuesItsChain() {
        val conversation = ThreadsConversation.from(page, ThreadsPost.linkStub("https://www.threads.com/@zuck/post/DQzuck0602"))
        val rows = conversation.replyRows(ThreadsThreadView(expanded = setOf("611"))).filterIsInstance<ThreadsReplyRow.Post>()
        assertEquals(listOf(0, 1, 1, 1, 0), rows.map { it.depth })
        val (maya, thanks, agreed, ha) = rows
        assertFalse(maya.connectBottom, "two answers step in instead of a rail")
        assertFalse(thanks.connectTop)
        assertTrue(agreed.connectBottom && ha.connectTop, "the only answer to Agreed continues its chain")
    }

    @Test
    fun aThreadMetaHoldsMoreOfSaysSoOnceItIsShownWhole() {
        val conversation = ThreadsConversation.from(page, ThreadsPost.linkStub("https://www.threads.com/@zuck/post/DQzuck0602"))
        assertTrue(conversation.replyRows().none { it is ThreadsReplyRow.OnWeb }, "cut, its More row comes first")
        val opened = conversation.replyRows(ThreadsThreadView(expanded = setOf("611")))
        assertEquals(listOf("611"), opened.filterIsInstance<ThreadsReplyRow.OnWeb>().map { it.chain.first().id })
        assertTrue(opened.last() is ThreadsReplyRow.Post, "the short thread after it ends with its post")
    }

    @Test
    fun theReaderDoesNotDependOnWhatAPostsConnectionHoldsItsPostsIn() {
        val listed = fixture("post_page_queries.html")
            .replace(""""posts":{"edges":""", """"posts":{"items":""")
            .replace("""{"node":{"pk"""", """{"post":{"pk"""")
        val read = ThreadsPostPage.read(listed)
        assertEquals(page.replies.map { ids(it.posts) }, read.replies.map { ids(it.posts) })
        assertEquals(ids(page.above), ids(read.above))
    }

    @Test
    fun withoutEveryLevelAReplyThreadIsOneChainAsBefore() {
        val shapes = ThreadsReplyShape.of(listOf(2, null, 3))
        assertEquals(listOf(0, 0, 0), shapes.map { it.depth })
        assertEquals(listOf(false, true, true), shapes.map { it.railAbove })
        assertEquals(ThreadsReplyShape.MAX_DEPTH, ThreadsReplyShape.of(listOf(1, 2, 3, 4, 5, 6, 5, 4, 3, 2)).maxOf { it.depth })
    }

    @Test
    fun anOlderPageKeepsItsChainsWithoutTheRelatedPosts() {
        val older = ThreadsPostPage.read(fixture("post_page.html"))
        assertNull(older.target)
        assertEquals(listOf(listOf("501", "502", "503", "504"), listOf("505", "506"), listOf("507")), older.chains.map(::ids))
    }

    @Test
    fun aPageWithNothingInItIsEmpty() {
        assertTrue(ThreadsPostPage.read("<html><body>nothing</body></html>").isEmpty)
        assertTrue(ThreadsPostPage.read("""<script data-sjs>{"x":["adp_BarcelonaPostPageTargetQueryRelayPreloader_1",{"__bbox":{"result":{"data":{"media":{"user":{}}}}}}]}</script>""").isEmpty)
    }

    @Test
    fun theWholeConversationIsReadAloudPostByPost() {
        val conversation = ThreadsConversation.from(page, ThreadsPost.linkStub("https://www.threads.com/@zuck/post/DQzuck0602"))
        val spoken = conversation.spokenText()
        assertTrue(spoken.startsWith("Meta. What is new on Threads?\n\nMark Zuckerberg. Replying to Meta"))
        assertTrue(spoken.endsWith("Dana. First!"))
    }

    @Test
    fun theClientKeepsThePageAndStillHandsOutItsChains() = runBlocking {
        val meta = FakeMeta { ok(fixture("post_page_queries.html")) }
        val client = direct(meta)
        assertEquals("602", client.postPage("https://www.threads.com/@zuck/post/DQzuck0602").target?.id)
        assertEquals(listOf("601", "602", "603"), ids(client.postChains("https://www.threads.com/@zuck/post/DQzuck0602").first()))
        assertEquals(1, meta.asked.size)
    }
}
