package com.aimdi.xtanative.networks.reddit

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Why a Reddit read sometimes did not load, held off: a stalled host may not
 * hold a screen for minutes, one slow answer may not park a route, and the
 * requests made at once stay few.
 */
class RedditReliabilityTest {
    @Test
    fun aListingWhoseHostsAllStallFailsWithinOneBudgetNotOneWaitPerRoute() {
        val fake = FakeReddit { delay(5_000); page(WWW_LISTING) }
        val client = RedditClient(fake, readBudgetMillis = 300)
        val started = System.currentTimeMillis()

        assertFailsWith<IOException> { runBlocking { client.listing("dartlang") } }
        assertTrue(System.currentTimeMillis() - started < 2_000, "one budget for the whole walk")
        assertEquals(1, fake.asked.size, "the routes after the budget ran out are not asked")
    }

    @Test
    fun aThreadWhoseHostsAllStallFailsWithinOneBudget() {
        val fake = FakeReddit { delay(5_000); page("") }
        val client = RedditClient(fake, readBudgetMillis = 300)
        val started = System.currentTimeMillis()

        assertFailsWith<IOException> { runBlocking { client.thread("/r/dartlang/comments/abc123/dart_4_is_out/") } }
        assertTrue(System.currentTimeMillis() - started < 2_000)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun aSlowFirstRouteLeavesTheRestOfTheBudgetToTheNext() = runBlocking {
        val fake = FakeReddit { if (it.isWww) { delay(5_000); page(WWW_LISTING) } else page(LISTING_HTML) }
        val listing = RedditClient(fake, readBudgetMillis = 3_000, timeoutMillis = 200).listing("dartlang")

        assertEquals("abc123", listing.items.single().id)
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
    }
}
