package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsPost
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** "More on …" is read when asked and once; a conversation kept before round 23 drops Meta's picks by others. */
class ThreadsTopicBesideTest {
    private fun post(id: String, handle: String = "zuck") =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "t$id", url = "https://www.threads.com/@$handle/post/C$id")

    @Test
    fun nothingIsReadUntilAskedThenOnceHoweverOftenAsked() = runBlocking {
        val reads = AtomicInteger()
        val answer = CompletableDeferred<List<ThreadsPost>>()
        coroutineScope {
            val beside = ThreadsTopicBeside(this) { topic -> reads.incrementAndGet(); assertEquals("Ceramics", topic); answer.await() }
            yield()
            assertNull(beside.state.value, "made with the screen, it asks nothing")
            assertEquals(0, reads.get())
            beside.ask("Ceramics")
            beside.ask("Ceramics")
            yield()
            assertTrue(beside.state.value!!.loading)
            answer.complete(listOf(post("50", "c")))
            yield()
            beside.ask("Ceramics")
            yield()
            assertEquals(listOf("50"), beside.state.value!!.posts!!.map { it.id })
        }
        assertEquals(1, reads.get())
    }

    @Test
    fun aConversationKeptBeforeKeepsOnlyTheAuthorsOwnSuggestions() {
        val read = ThreadsConversation(focus = post("1"), loaded = true, fromAuthor = listOf(post("9")))
        val json = ThreadsConversationsKept.json(read)
        val older = J(json).raw.toString().replace(""""related":[""", """"related":[${ThreadsConversationsKept.json(read.copy(focus = post("8", "else"))).let { J(it)["focus"].raw }},""")
        val back = ThreadsConversationsKept.conversationOf(J.parse(older))!!
        assertEquals(listOf("9"), back.fromAuthor.map { it.id })
    }
}
