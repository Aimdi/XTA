package com.aimdi.xtanative.services.util

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class UtilTest {
    @Test
    fun byteSizesReadAsXtaWritesThem() {
        assertEquals("512 B", formatByteSize(512))
        assertEquals("2.0 KB", formatByteSize(2048))
        assertEquals("5.0 MB", formatByteSize(5L * 1048576))
        assertEquals("1.5 GB", formatByteSize(3L * 1073741824 / 2))
    }

    @Test
    fun onlyTheLastCallOfABurstRuns() = runBlocking {
        val runs = AtomicInteger()
        val last = CompletableDeferred<String>()
        coroutineScope {
            val debouncer = Debouncer(this)
            listOf("a", "ab", "abc").forEach { query ->
                debouncer.debounce("search", 30) { runs.incrementAndGet(); last.complete(query) }
            }
            assertTrue(debouncer.isWaiting("search"))
        }
        assertEquals(1, runs.get())
        assertEquals("abc", last.await())
    }

    @Test
    fun aCancelledCallNeverRunsAndOtherIdsAreUntouched() = runBlocking {
        val ran = ArrayList<String>()
        coroutineScope {
            val debouncer = Debouncer(this)
            debouncer.debounce("one", 20) { ran += "one" }
            debouncer.debounce("two", 20) { ran += "two" }
            debouncer.cancel("one")
            assertFalse(debouncer.isWaiting("one"))
        }
        assertEquals(listOf("two"), ran)
    }

    @Test
    fun aStalledCacheOpenAndWriteCannotHoldASuccessfulPage() = runBlocking {
        coroutineScope {
            val page = loadCachedPage(
                readFresh = { awaitCancellation() },
                readStale = { null },
                fetch = { "posts" },
                write = { awaitCancellation() },
                background = this,
                cacheTimeoutMillis = 20,
            )
            assertEquals("posts", page)
            coroutineContext[Job]!!.children.forEach { it.cancel() }
        }
    }

    @Test
    fun aStalledStaleReadKeepsTheNetworksOwnError() = runBlocking {
        val error = IllegalStateException("network failed")
        coroutineScope {
            val thrown = assertFailsWith<IllegalStateException> {
                loadCachedPage<String>(
                    readFresh = { throw IllegalStateException("must bypass") },
                    readStale = { awaitCancellation() },
                    fetch = { throw error },
                    write = {},
                    background = this,
                    bypassCache = true,
                    cacheTimeoutMillis = 20,
                )
            }
            assertSame(error, thrown)
        }
    }

    @Test
    fun aCachedPageStandsInWhenTheNetworkFails() = runBlocking {
        coroutineScope {
            val page = loadCachedPage(readFresh = { null }, readStale = { "saved posts" }, fetch = { error("offline") }, write = {}, background = this)
            assertEquals("saved posts", page)
        }
    }

    @Test
    fun aFreshCopyWinsAndIsNotFetchedAgain() = runBlocking {
        var fetched = 0
        coroutineScope {
            val page = loadCachedPage(readFresh = { "kept" }, readStale = { null }, fetch = { fetched++; "new" }, write = {}, background = this)
            assertEquals("kept", page)
        }
        assertEquals(0, fetched)
    }

    @Test
    fun aLinkIsTakenApartAsDartReadsIt() {
        val link = WebLink.parse("HTTPS://X.com/i/Article/42?a=1&b=two+words&c=%C3%A9#top")!!
        assertEquals("https", link.scheme)
        assertEquals("x.com", link.host)
        assertEquals(listOf("i", "Article", "42"), link.segments)
        assertEquals("two words", link.query("b"))
        assertEquals("é", link.query("c"))
        assertEquals("HTTPS://X.com/i/Article/42?a=1&c=%C3%A9#top", link.withoutQuery { it == "b" })
        assertNull(WebLink.parse("not a url at all"))
        assertEquals("bsky.app", WebLink.web("bsky.app/profile/a")?.host)
        assertNull(WebLink.web("ftp://bsky.app/profile/a"))
    }
}
