package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Hacker News, as XTA's test/live/sources_smoke_test.dart reads it: the
 * front page, its first thread, a user, a search, and a page of Best.
 * Skipped unless RUN_LIVE=true.
 */
class HackerNewsLiveTest {
    @Test
    fun frontPageAThreadAUserAndSearch() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Hacker News")
        val client = HackerNewsClient(OkHttpXHttp())

        val page = client.feed(HnFeed.TOP)
        assertTrue(page.items.isNotEmpty(), "front page")
        val thread = client.thread(page.items.first().id)
        assertEquals(page.items.first().id, thread.story.id)
        val user = client.user("pg")
        assertTrue(user.karma > 0)
        val found = client.search("flutter")
        assertTrue(found.items.isNotEmpty(), "search")
        val best = client.feed(HnFeed.BEST)
        assertTrue(best.items.isNotEmpty(), "best")

        println("LIVE HN: ${page.items.size} stories, ${thread.comments.size} top comments, search ${found.items.size}, best ${best.items.size}")
    }
}
