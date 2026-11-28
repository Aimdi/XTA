package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.page
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The merged timeline's loads stopped halfway, partial answers and the retry count. */
class MergedTimelineTest {
    private class Source(val key: String, var answer: suspend (String?) -> XTimelinePage) {
        val reads = ArrayList<String?>()
        val timeline = TimelineSource(key) { cursor -> synchronized(reads) { reads += cursor }; answer(cursor) }
    }

    @Test
    fun theRetryCountKeepsSourcesThatFailedInAnEarlierLoad() = runBlocking {
        val a = Source("a") { cursor -> if (cursor == null) page(thread(100), thread(90), next = "a2") else page(thread(80), next = "a3") }
        val b = Source("b") { throw IOException("offline") }
        val timeline = MergedTimeline(listOf(a.timeline, b.timeline))
        timeline.first()
        assertEquals(1, timeline.progress.value.failed)
        timeline.more()
        assertEquals(1, timeline.progress.value.failed, "a later page that read only a still leaves b to retry")
        assertTrue(timeline.progress.value.canRetry)
    }

    @Test
    fun aFirstPageStoppedHalfwayReadsOnlyTheSourcesThatHadNotAnswered() = runBlocking {
        val bAsked = CompletableDeferred<Unit>()
        val never = CompletableDeferred<XTimelinePage>()
        val a = Source("a") { page(thread(100, by = "a"), next = null) }
        val b = Source("b") { bAsked.complete(Unit); never.await() }
        val timeline = MergedTimeline(listOf(a.timeline, b.timeline))
        val load = launch { timeline.first() }
        bAsked.await()
        while (synchronized(a.reads) { a.reads.isEmpty() }) kotlinx.coroutines.yield()
        load.cancelAndJoin()

        b.answer = { page(thread(95, by = "b"), next = null) }
        val resumed = timeline.resumeFirst()!!
        assertEquals(listOf("100", "95"), ids(resumed.threads), "what a brought before stays")
        assertEquals(1, a.reads.size, "a is not asked again")
        assertEquals(2, b.reads.size)
        assertNull(timeline.resumeFirst(), "once ended there is nothing to resume")
    }

    @Test
    fun aFirstPageStoppedLongAgoIsReadAgainFromTheTop() = runBlocking {
        var now = 0L
        val asked = CompletableDeferred<Unit>()
        val never = CompletableDeferred<XTimelinePage>()
        val a = Source("a") { asked.complete(Unit); never.await() }
        val timeline = MergedTimeline(listOf(a.timeline), clock = { now })
        val load = launch { timeline.first() }
        asked.await()
        load.cancelAndJoin()
        now += RESUME_MILLIS
        assertNull(timeline.resumeFirst())
    }

    @Test
    fun aFailureInAStoppedLoadIsKeptForTheResumedOne() = runBlocking {
        val bAsked = CompletableDeferred<Unit>()
        val never = CompletableDeferred<XTimelinePage>()
        val failure = IOException("offline")
        val a = Source("a") { throw failure }
        val b = Source("b") { bAsked.complete(Unit); never.await() }
        val timeline = MergedTimeline(listOf(a.timeline, b.timeline))
        val load = launch { timeline.first() }
        bAsked.await()
        while (synchronized(a.reads) { a.reads.isEmpty() }) kotlinx.coroutines.yield()
        load.cancelAndJoin()

        b.answer = { page(thread(95, by = "b"), next = null) }
        val resumed = timeline.resumeFirst()!!
        assertSame(failure, resumed.failures.single())
        assertEquals(1, a.reads.size)
        assertEquals(1, timeline.progress.value.failed)
    }

    @Test
    fun aPartialAnswerIsShownAndItsSourceStaysFailed() = runBlocking {
        val cause = IOException("half")
        val a = Source("a") { page(thread(100, by = "a"), next = null) }
        val b = Source("b") { throw PartialReadException(listOf(thread(99, by = "b")), cause) }
        val timeline = MergedTimeline(listOf(a.timeline, b.timeline))
        val first = timeline.first()
        assertEquals(listOf("100", "99"), ids(first.threads))
        assertIs<IOException>(first.failures.single())
        assertTrue(timeline.state().chunks[1].failed)
        assertEquals(1, timeline.progress.value.failed)
    }
}
