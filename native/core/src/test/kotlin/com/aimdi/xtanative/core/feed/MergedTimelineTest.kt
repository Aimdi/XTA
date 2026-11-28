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
