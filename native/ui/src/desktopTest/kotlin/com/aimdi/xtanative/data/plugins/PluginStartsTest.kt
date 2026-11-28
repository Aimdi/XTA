package com.aimdi.xtanative.data.plugins

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Installed plugins load side by side at start, a few at once, and come back in the order asked. */
class PluginStartsTest {
    @Test
    fun loadsComeBackInTheOrderAskedWhicheverFinishesFirst(): Unit = runBlocking {
        val ids = listOf("bluesky", "pixiv", "reddit", "rss", "threads")
        val loaded = PluginStarts.loadAll(ids) { id ->
            delay(if (id == "bluesky") 60L else 1L)
            "$id loaded"
        }
        assertEquals(ids.map { "$it loaded" }, loaded)
    }

    @Test
    fun noMoreThanAFewLoadAtOnceAndThatManyDo(): Unit = runBlocking {
        val now = AtomicInteger()
        val most = AtomicInteger()
        val allIn = CompletableDeferred<Unit>()
        val loaded = PluginStarts.loadAll((1..13).toList()) { n ->
            val running = now.incrementAndGet()
            most.accumulateAndGet(running, ::maxOf)
            if (running == PluginStarts.AT_ONCE) allIn.complete(Unit)
            // The first ones wait for each other: they only all finish when that many ran at the same time.
            if (n <= PluginStarts.AT_ONCE) withTimeout(10_000) { allIn.await() } else delay(2)
            now.decrementAndGet()
            n * 2
        }
        assertEquals((1..13).map { it * 2 }, loaded)
        assertEquals(PluginStarts.AT_ONCE, most.get())
    }

    @Test
    fun nothingToLoadAsksNothing(): Unit = runBlocking {
        assertEquals(emptyList(), PluginStarts.loadAll(emptyList<String>()) { error("asked $it") })
    }

    @Test
    fun aLoadThatThrowsFailsTheStartAsItDidOneByOne(): Unit = runBlocking {
        val failure = assertFailsWith<IllegalStateException> {
            PluginStarts.loadAll(listOf("a", "b")) { if (it == "b") error("store unreadable") else it }
        }
        assertTrue(failure.message!!.contains("store unreadable"))
    }
}
