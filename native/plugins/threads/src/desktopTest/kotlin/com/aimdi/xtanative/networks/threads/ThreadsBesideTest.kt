package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * What a conversation shows after its replies (round 23). Before, Meta's
 * guest suggestions, popular posts by anyone, were shown as "Similar
 * threads"; now only the author's own are kept, as "More from @author", and
 * "More on <topic>" reads the post's topic page once the reader reached the
 * end, never when the conversation opens.
 */
class ThreadsBesideTest {
    private fun ids(posts: List<ThreadsPost>) = posts.map { it.id }

    private fun post(id: String, handle: String = "zuck", topic: String? = null, replyTo: String? = null) = ThreadsPost(
        id = id, handle = handle, authorName = handle, text = "t$id", url = "https://www.threads.com/@$handle/post/C$id",
        topicTag = topic, isReply = replyTo != null, replyToHandle = replyTo,
    )

    private val conversation = ThreadsConversation(
        focus = post("1", topic = "Ceramics"),
        ancestors = listOf(post("0", "ola")),
        continuation = listOf(post("1b")),
        replies = listOf(listOf(post("2", "a"), post("3")), listOf(post("4", "b"))),
        loaded = true,
        fromAuthor = listOf(post("7")),
    )

    @Test
    fun onlyTheAuthorsOwnSuggestionsAreKeptOnceAndOutsideTheConversation() {
        val related = listOf(post("80", "else"), post("7"), post("1b"), post("1"), post("7"), post("81", "ZUCK"), post("82", "other"))
        assertEquals(listOf("7", "81"), ids(ThreadsBeside.fromAuthor(related, conversation.focus, setOf("1b"))))
        assertEquals(emptyList(), ThreadsBeside.fromAuthor(related, post("x", handle = "")), "no author, nothing is theirs")
        assertEquals(ThreadsBeside.FROM_AUTHOR, ThreadsBeside.fromAuthor((10..30).map { post("$it") }, conversation.focus).size)
    }

    @Test
    fun aPagesSuggestionsByOthersAreDroppedAndTheAuthorsBecomeMoreFrom() {
        val html = fixture("post_page_queries.html")
        val stub = ThreadsPost.linkStub("https://www.threads.com/@zuck/post/DQzuck0602")
        assertEquals(emptyList(), ids(ThreadsConversation.from(ThreadsPostPage.read(html), stub).fromAuthor), "Meta's generic picks name no one related")
        val own = ThreadsPostPage.read(html.replace(""""username":"else"""", """"username":"zuck""""))
        assertEquals(listOf("681", "682"), ids(own.related), "the page still reads what Meta suggested")
        assertEquals(listOf("681"), ids(ThreadsConversation.from(own, stub).fromAuthor))
    }

    @Test
    fun theTopicIsThePostsOwnAndOnlyOnceItWasRead() {
        assertEquals("Ceramics", ThreadsBeside.topicOf(conversation))
        assertEquals("Kiln firing", ThreadsBeside.topicOf(conversation.copy(focus = post("1", topic = " #Kiln firing "))))
        assertNull(ThreadsBeside.topicOf(conversation.copy(loaded = false)), "the tapped card alone asks nothing")
        assertNull(ThreadsBeside.topicOf(conversation.copy(focus = post("1"))))
        assertNull(ThreadsBeside.topicOf(conversation.copy(focus = post("1", topic = "#"))))
    }

    @Test
    fun moreOnTheTopicIsAskedOnlyOnceTheReaderScrolledToTheEnd() {
        assertTrue(ThreadsBeside.asksTopic(conversation, endShown = true, readerScrolled = true, repliesPending = false))
        assertFalse(ThreadsBeside.asksTopic(conversation, endShown = true, readerScrolled = false, repliesPending = false), "a short conversation's end on screen when it opens asks nothing")
        assertFalse(ThreadsBeside.asksTopic(conversation, endShown = false, readerScrolled = true, repliesPending = false))
        assertFalse(ThreadsBeside.asksTopic(conversation, endShown = true, readerScrolled = true, repliesPending = true), "more replies are still to come")
        assertFalse(ThreadsBeside.asksTopic(conversation.copy(focus = post("1")), endShown = true, readerScrolled = true, repliesPending = false), "a post in no topic")
    }

    @Test
    fun moreOnTheTopicLeavesOutTheConversationAndRepeats() {
        val on = "ceramics"
        val found = listOf(
            post("1", topic = on), post("3", topic = on), post("0", "ola", topic = on), post("7", topic = on),
            post("50", "c", topic = "#Ceramics"),
            post("49", "x", topic = "Gardening"), post("48", "y"),
            post("51", "d", topic = on, replyTo = "zuck"),
            post("52", "e", topic = on, replyTo = "someone"),
            post("50", "c", topic = on),
            ThreadsPost(id = "other-id", handle = "a", authorName = "a", text = "same post, another id", url = "https://www.threads.net/@a/post/C2", topicTag = on),
            post("53", "f", topic = on), post("54", "g", topic = on), post("55", "h", topic = on), post("56", "i", topic = on),
        )
        assertEquals(listOf("50", "52", "53", "54", "55"), ids(ThreadsBeside.onTopic(conversation, found)))
        assertEquals(emptyList(), ThreadsBeside.onTopic(conversation.copy(focus = post("1")), found), "a post in no topic has no such section")
    }

    private val time = FakeTime()
    private val meta = FakeMeta { request ->
        delay(10)
        when {
            request.path.startsWith("/@zuck/post/") -> ok(page(listOf(listOf(postJson("1")), listOf(postJson("2", user = "a")))))
            request.path == "/search" -> ok(fixture("search_page.html"))
            else -> XResponse(404, "")
        }
    }

    private fun reader() = ThreadsReader(
        direct = ThreadsDirectClient(meta, { ThreadsSession() }, ThreadsMemory.InMemory(), { ThreadsRepairs() }, ThreadsPacing.NONE, time.clock),
        rssHub = ThreadsRssHub(meta),
        xy = ThreadsXy(meta),
        clock = time.clock,
    )

    private fun asked(): List<String> = meta.asked.map { "${it.method} ${it.path}" }.also { meta.asked.clear() }

    @Test
    fun openingAConversationReadsNoTopicAndTheTopicIsOneSharedRead() = runBlocking {
        val reader = reader()
        reader.conversation(post("1"))
        assertEquals(listOf("GET /@zuck/post/C1"), asked(), "the post's page alone")
        val posts = reader.topicPosts("Ceramics")
        val shown = ThreadsBeside.onTopic(conversation.copy(focus = post("1", topic = "Ceramics")), posts)
        assertTrue(shown.isNotEmpty() && shown.all { it.topicTag == "Ceramics" }, "${posts.map { it.topicTag }}")
        assertEquals(listOf("GET /search"), asked(), "the topic's page, once")
        assertEquals(posts, reader.topicPosts("#ceramics"))
        reader.extras(emptyList(), topics = listOf("Ceramics"))
        assertEquals(emptyList(), asked(), "held as Home holds a followed topic's, both ways")
        time.now += ThreadsReader.EXTRA_TTL_MILLIS + 1
        reader.topicPosts("Ceramics")
        assertEquals(listOf("GET /search"), asked(), "past half an hour it is read again")
    }

    @Test
    fun aTopicThatCannotBeReadIsNoFailure() = runBlocking {
        val refusing = FakeMeta { XResponse(429, "") }
        val reader = ThreadsReader(
            direct = ThreadsDirectClient(refusing, { ThreadsSession() }, ThreadsMemory.InMemory(), { ThreadsRepairs() }, ThreadsPacing.NONE, time.clock),
            rssHub = ThreadsRssHub(refusing),
            xy = ThreadsXy(refusing),
            clock = time.clock,
        )
        assertEquals(emptyList(), reader.topicPosts("Ceramics"))
        assertEquals(emptyList(), reader.topicPosts(" "))
    }
}
