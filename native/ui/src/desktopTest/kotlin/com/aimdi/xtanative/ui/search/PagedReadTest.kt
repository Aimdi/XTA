package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.data.Problem
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PagedReadTest {
    /** Launches run at once, up to their first real wait: each step below sees what it did. */
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun pages(vararg pages: Pair<String?, Page<String>>): suspend (String?) -> Page<String> {
        val byCursor = pages.toMap()
        return { cursor -> byCursor[cursor] ?: error("no page for $cursor") }
    }

    @Test
    fun readsTheFirstPageThenTheOnesAfter() {
        val read = PagedRead<String, String>(PageEnd.POSTS) { it }
        read.start(scope, pages(null to Page(listOf("a", "b"), "c1"), "c1" to Page(listOf("c"), null)))
        assertEquals(listOf("a", "b"), read.state.value.items)
        assertEquals("c1", read.state.value.next)

        read.more(scope)
        assertEquals(listOf("a", "b", "c"), read.state.value.items)
        assertNull(read.state.value.next)

        // At the end, asking for more asks nothing.
        read.more(scope)
        assertEquals(listOf("a", "b", "c"), read.state.value.items)
    }

    @Test
    fun aFailedFirstPageSaysWhyAndIsReadAgain() {
        var fail = true
        val read = PagedRead<String, String>(PageEnd.POSTS) { it }
        read.start(scope) { if (fail) throw IOException("offline") else Page(listOf("a"), null) }
        assertEquals(Problem.Offline, read.state.value.problem)

        fail = false
        read.retry(scope)
        assertNull(read.state.value.problem)
        assertEquals(listOf("a"), read.state.value.items)
    }

    @Test
    fun aFailedPageAfterKeepsWhatIsShown() {
        val read = PagedRead<String, String>(PageEnd.POSTS) { it }
        read.start(scope) { cursor -> if (cursor == null) Page(listOf("a"), "c1") else throw IOException("offline") }
        read.more(scope)
        assertEquals(listOf("a"), read.state.value.items)
        assertEquals(Problem.Offline, read.state.value.moreProblem)
        assertTrue(read.state.value.canLoadMore)
    }

    @Test
    fun aReadStartedOverDropsTheAnswerOfTheOneBefore() = runBlocking {
        val slow = CompletableDeferred<Page<String>>()
        val read = PagedRead<String, String>(PageEnd.POSTS) { it }
        read.start(scope) { slow.await() }
        read.start(scope) { Page(listOf("new"), null) }
        slow.complete(Page(listOf("old"), null))
        assertEquals(listOf("new"), read.state.value.items)
    }

    @Test
    fun clearedShowsNothingAndDropsTheReadUnderWay() = runBlocking {
        val slow = CompletableDeferred<Page<String>>()
        val read = PagedRead<String, String>(PageEnd.POSTS) { it }
        read.start(scope) { slow.await() }
        read.clear()
        slow.complete(Page(listOf("late"), null))
        assertEquals(Paged(), read.state.value)
    }
}
