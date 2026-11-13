package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PluginPostsTest {
    /** Each plugin's answers, given when the test says, read on the thread that gives them. */
    private class Answers {
        val pending = HashMap<String, CompletableDeferred<List<NetworkPost>>>()
        val asked = ArrayList<Pair<String, SourceAsk>>()

        suspend fun read(source: String, ask: SourceAsk, force: Boolean): List<NetworkPost> {
            asked += source to ask
            return CompletableDeferred<List<NetworkPost>>().also { pending[source] = it }.await()
        }

        fun answer(source: String, vararg ids: Pair<String, Long>) {
            pending.getValue(source).complete(ids.map { (id, at) -> NetworkPost(id, "a", "A", "a", createdAt = Instant.ofEpochSecond(at)) })
        }

        fun fail(source: String, error: Throwable) = pending.getValue(source).completeExceptionally(error)
    }

    private fun reader(answers: Answers, block: (PluginPosts, CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            block(PluginPosts(scope, answers::read), scope)
        } finally {
            scope.cancel()
        }
    }

    private val ask = SourceAsk(followed = true)

    @Test
    fun onePluginFailingKeepsTheOthersPostsAndSaysWhy() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.load(mapOf("bluesky" to ask, "reddit" to ask))
            answers.answer("bluesky", "b1" to 200, "b2" to 100)
            answers.fail("reddit", IOException("offline"))
            val state = posts.state.value
            assertEquals(listOf("b1", "b2"), state.shown.map { it.post.id })
            assertEquals(mapOf("reddit" to Problem.Offline), state.failed)
            assertEquals(false, state.loading)
        }
    }

    @Test
    fun whatArrivesWhileTheReaderIsDownTheListWaitsUntilTheyComeBackUp() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.load(mapOf("bluesky" to ask))
            answers.answer("bluesky", "old" to 100)
            posts.readingAway(true)
            posts.load(mapOf("bluesky" to ask), force = true)
            answers.answer("bluesky", "new" to 300, "old" to 100)
            assertEquals(listOf("old"), posts.state.value.shown.map { it.post.id }, "nothing moves under the reader")
            assertEquals(1, posts.state.value.newHeld, "the pill counts the one not shown")
            posts.readingAway(false)
            assertEquals(listOf("new", "old"), posts.state.value.shown.map { it.post.id })
            assertEquals(0, posts.state.value.newHeld)
        }
    }

    @Test
    fun theFirstAnswerIsShownEvenDownTheListWhenNothingIsShownYet() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.readingAway(true)
            posts.load(mapOf("bluesky" to ask))
            answers.answer("bluesky", "a" to 1)
            assertEquals(listOf("a"), posts.state.value.shown.map { it.post.id })
        }
    }

    @Test
    fun aPluginNoLongerAskedLeavesWithItsPostsAndALateAnswerIsDropped() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.load(mapOf("bluesky" to ask, "reddit" to ask))
            answers.answer("bluesky", "b" to 1)
            val late = answers.pending.getValue("reddit")
            posts.load(mapOf("reddit" to ask))
            assertTrue(posts.state.value.shown.isEmpty(), "bluesky's posts went with it")
            late.complete(listOf(NetworkPost("r-old", "a", "A", "a", createdAt = Instant.ofEpochSecond(5))))
            assertTrue(posts.state.value.shown.isEmpty(), "the answer to a read asked again is not the answer")
            answers.answer("reddit", "r" to 2)
            assertEquals(listOf("r"), posts.state.value.shown.map { it.post.id })
        }
    }

    @Test
    fun anAskChangedStartsEmptyAndAnUnchangedOneKeepsItsPostsUntilItAnswers() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.load(mapOf("bluesky" to ask, "reddit" to SourceAsk(listOf("kotlin"))))
            answers.answer("bluesky", "b" to 1)
            answers.answer("reddit", "r" to 2)
            posts.load(mapOf("bluesky" to ask, "reddit" to SourceAsk(listOf("android"))))
            assertEquals(listOf("b"), posts.state.value.shown.map { it.post.id })
            assertEquals(SourceAsk(listOf("android")), answers.asked.last().second)
        }
    }

    @Test
    fun aRetryReadsOnlyThatPluginAndAPostWithoutATimeIsLeftOut() {
        val answers = Answers()
        reader(answers) { posts, _ ->
            posts.load(mapOf("bluesky" to ask))
            answers.fail("bluesky", IllegalStateException("no"))
            posts.retry("bluesky")
            assertNull(posts.state.value.failed["bluesky"])
            answers.pending.getValue("bluesky").complete(listOf(NetworkPost("undated", "a", "A", "a"), NetworkPost("dated", "a", "A", "a", createdAt = Instant.EPOCH)))
            assertEquals(listOf("dated"), posts.state.value.shown.map { it.post.id })
            assertEquals(2, answers.asked.size)
        }
    }

    /** What the device kept, in memory: what each source last answered, and what was written. */
    private class Kept(var answer: SavedAnswer? = null) : SourceSnapshots {
        val written = ArrayList<List<NetworkPost>>()

        override suspend fun read(source: String, ask: SourceAsk): SavedAnswer? = answer

        override suspend fun write(source: String, ask: SourceAsk, posts: List<NetworkPost>) {
            written += posts
        }
    }

    @Test
    fun whatAPluginGaveLastTimeShowsAtOnceUntilItAnswersAndItsAnswerIsKept() {
        val answers = Answers()
        val kept = Kept(SavedAnswer(42, listOf(NetworkPost("saved", "a", "A", "a", createdAt = Instant.ofEpochSecond(9)))))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val posts = PluginPosts(scope, answers::read, snapshots = kept)
            posts.load(mapOf("bluesky" to ask))
            val waiting = posts.state.value
            assertEquals(listOf("saved"), waiting.shown.map { it.post.id })
            assertEquals(SourceRead(waiting.sources.getValue("bluesky").posts, loading = true, cachedAt = 42), waiting.sources["bluesky"])
            assertTrue(waiting.chips.isEmpty(), "a copy shown while the plugin reads is no chip")
            answers.answer("bluesky", "new" to 20)
            assertEquals(listOf("new"), posts.state.value.shown.map { it.post.id })
            assertNull(posts.state.value.sources.getValue("bluesky").cachedAt)
            assertEquals(listOf("new"), kept.written.single().map { it.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aPluginThatFailsKeepsItsCopyAndIsAChip() {
        val answers = Answers()
        val kept = Kept(SavedAnswer(42, listOf(NetworkPost("saved", "a", "A", "a", createdAt = Instant.ofEpochSecond(9)))))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val posts = PluginPosts(scope, answers::read, snapshots = kept)
            posts.load(mapOf("bluesky" to ask))
            answers.fail("bluesky", IOException("offline"))
            assertEquals(listOf("saved"), posts.state.value.shown.map { it.post.id })
            assertEquals(setOf("bluesky"), posts.state.value.chips.keys)
            assertEquals(42, posts.state.value.sources.getValue("bluesky").cachedAt)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aPluginTakingLongerThanTheTimelineWaitsHasTimedOut() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val posts = PluginPosts(scope, { _, _, _ -> awaitCancellation() }, timeoutMillis = 50)
            posts.load(mapOf("bluesky" to ask))
            val failed = withTimeout(5_000) { posts.state.first { it.failed.isNotEmpty() } }
            assertEquals(mapOf("bluesky" to Problem.TimedOut), failed.failed)
        } finally {
            scope.cancel()
        }
    }
}
