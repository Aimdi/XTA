package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PixivPagingTest {
    private val pages = mapOf<String?, Page<Int>>(null to Page(listOf(1, 2), "p2"), "p2" to Page(listOf(2, 3), "p3"), "p3" to Page(listOf(4), null))

    private suspend fun <T> PixivList<*>.settled(check: (PixivListState<*>) -> Boolean, value: () -> T): T =
        withTimeout(5_000) { state.first { check(it) }.let { value() } }

    @Test
    fun aSecondNextPageCallWaitsForTheOneInFlight(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        var asked = 0
        val release = CompletableDeferred<Unit>()
        val list = PixivList<Int>(scope, { it }, { next ->
            asked++
            if (next == null) Page(listOf(1), "p2") else {
                release.await()
                Page(listOf(2), null)
            }
        })
        list.refresh()
        list.settled({ it.loaded }) {}
        list.more()
        list.more()
        list.settled({ it.loadingMore }) {}
        withTimeout(5_000) { while (asked < 2) kotlinx.coroutines.yield() }
        assertEquals(2, asked, "the first page and one next page only")
        release.complete(Unit)
        list.settled({ !it.loadingMore }) {}
        assertEquals(listOf(1, 2), list.state.value.items)
    }

    @Test
    fun pagesJoinInOrderWithoutRepeatsToTheEnd(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val list = PixivList<Int>(scope, { it }, { next -> pages.getValue(next) })
        list.ensure()
        list.settled({ it.loaded }) {}
        list.more()
        list.settled({ it.items.size == 3 && !it.loadingMore }) {}
        list.more()
        list.settled({ !it.hasMore }) {}
        assertEquals(listOf(1, 2, 3, 4), list.state.value.items)
    }

    @Test
    fun aFailedNextPageKeepsTheListAndRetries(): Unit = runBlocking {
        var failing = true
        val list = PixivList<Int>(CoroutineScope(coroutineContext), { it }, { next -> if (next == "p2" && failing) error("offline") else pages.getValue(next) })
        list.refresh()
        list.settled({ it.loaded }) {}
        list.more()
        list.settled({ it.moreFailed }) {}
        assertEquals(listOf(1, 2), list.state.value.items)
        failing = false
        list.retryMore()
        list.settled({ it.items.size == 3 }) {}
        assertFalse(list.state.value.moreFailed)
    }

    @Test
    fun aFirstPageThatFailsSaysWhyAndALaterRefreshKeepsWhatWasShown(): Unit = runBlocking {
        var failing = true
        val list = PixivList<Int>(CoroutineScope(coroutineContext), { it }, { next -> if (failing) error("offline") else pages.getValue(next) })
        list.refresh()
        list.settled({ it.error != null }) {}
        failing = false
        list.refresh()
        list.settled({ it.items.isNotEmpty() }) {}
        failing = true
        list.refresh()
        list.settled({ !it.loading }) {}
        assertEquals(listOf(1, 2), list.state.value.items)
        assertEquals(null, list.state.value.error)
    }

    @Test
    fun aPageTheFilterEmptiesIsReadPast(): Unit = runBlocking {
        val list = PixivList<Int>(CoroutineScope(coroutineContext), { it }, { next -> pages.getValue(next) }) { items -> items.filter { it > 2 } }
        list.refresh()
        list.settled({ it.loaded }) {}
        assertEquals(listOf(3), list.state.value.items)
    }

    @Test
    fun whatTheDeviceKeepsIsReadAgainOnlyWhenItChanges(): Unit = runBlocking {
        var reads = 0
        val local = PixivLocalList<String, Int>(CoroutineScope(coroutineContext), { it }) {
            reads++
            it.length
        }
        local.show(listOf("a", "bb"))
        local.list.settled({ it.loaded }) {}
        local.show(listOf("a", "bb"))
        local.list.settled({ !it.loading }) {}
        assertEquals(listOf(1, 2), local.list.state.value.items)
        assertEquals(2, reads)
        local.show(listOf("ccc"))
        local.list.settled({ it.items == listOf(3) }) {}
        assertTrue(reads == 3)
    }
}
