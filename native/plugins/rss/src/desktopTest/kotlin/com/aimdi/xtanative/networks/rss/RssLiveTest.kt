package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live feeds, as XTA's test/live/sources_smoke_test.dart reads them: an RSS
 * feed, an Atom feed, and a feed found from a site's home page. Skipped
 * unless RUN_LIVE=true.
 */
class RssLiveTest {
    @Test
    fun anRssFeedAnAtomFeedAndDiscoveryFromASite() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live feeds")
        val client = RssClient(OkHttpXHttp())

        val rss = client.channel("https://www.astralcodexten.com/feed")
        assertTrue(rss.items.isNotEmpty(), "RSS items")
        val atom = client.channel("https://github.com/flutter/flutter/releases.atom")
        assertTrue(atom.items.isNotEmpty(), "Atom entries")
        val found = client.lookup("https://www.theverge.com")
        val items = client.items(found).items
        assertTrue(items.isNotEmpty(), "items of ${found.feedUrl}")

        println("LIVE RSS: ${rss.items.size} RSS items, ${atom.items.size} Atom entries, ${items.size} from ${found.feedUrl}")
    }
}
