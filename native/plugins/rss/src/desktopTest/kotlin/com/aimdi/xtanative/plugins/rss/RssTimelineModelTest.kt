package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssClient
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssTimelineCopies
import com.aimdi.xtanative.networks.rss.RssTimelineCopy
import com.aimdi.xtanative.store.StoreFile
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.HttpUrl

/** The timeline last read is shown at a start while the feeds are read again, and kept after each read. */
class RssTimelineModelTest {
    private val feed = RssFeed(id = "https://a.example/feed", feedUrl = "https://a.example/feed", name = "A")
    private val rss = """<rss version="2.0"><channel><title>A</title>
        <item><title>Fresh</title><link>https://a.example/fresh</link><guid>fresh</guid></item>
        </channel></rss>"""

    private fun file(): StoreFile = StoreFile(File(Files.createTempDirectory("rss-timeline").toFile(), "timeline.json"))

    private fun kept(id: String, feedId: String) = RssItem(id = id, title = id, feedId = feedId, feedTitle = "A")

    @Test
    fun aStartShowsTheKeptItemsOfFeedsStillFollowedThenTheFreshRead(): Unit = runBlocking {
        val store = file()
        val old = Instant.parse("2020-01-01T00:00:00Z")
        store.write(RssTimelineCopies.encode(RssTimelineCopy(listOf(kept("old", feed.id), kept("gone", "https://b.example/feed")), old, setOf(feed.id))))
        val answer = CompletableDeferred<Unit>()
        val web = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                answer.await()
                return XResponse(200, rss)
            }
        }
        val model = RssTimelineModel(RssClient(web), this, store) { listOf(feed) }

        model.refresh()
        val shown = model.state.first { it.loaded }
        assertEquals(listOf("old"), shown.items.map { it.id }, "an unfollowed feed's kept items are left out")

        answer.complete(Unit)
        val read = model.state.first { it.fetchedAt != old && !it.loading && it.items.isNotEmpty() }
        assertEquals(listOf("Fresh"), read.items.map { it.title })
        val copy = withTimeout(5_000) {
            var titles: List<String>? = null
            while (titles != listOf("Fresh")) {
                yield()
                titles = RssTimelineCopies.decode(store.read())?.items?.map { it.title }
            }
            titles
        }
        assertEquals(listOf("Fresh"), copy, "the fresh read is kept for the next start")
    }

    @Test
    fun aFreshKeptCopyOfTheSameFeedsAsksNothing(): Unit = runBlocking {
        val store = file()
        store.write(RssTimelineCopies.encode(RssTimelineCopy(listOf(kept("kept", feed.id)), Instant.now(), setOf(feed.id))))
        var asked = 0
        val web = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                asked++
                return XResponse(200, rss)
            }
        }
        val model = RssTimelineModel(RssClient(web), this, store) { listOf(feed) }

        model.refresh()
        assertTrue(model.state.first { it.loaded }.items.any { it.id == "kept" })
        assertEquals(0, asked)
    }
}
