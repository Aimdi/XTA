package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.RssParser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssModelsTest {
    private fun item(id: String, at: Instant? = null, title: String = id) =
        RssItem(id = id, title = title, feedId = "https://a.example/feed", feedTitle = "A", publishedAt = at)

    @Test
    fun mergeSortsNewestFirstAndDropsDuplicateIds() {
        val older = item("a", Instant.parse("2026-01-01T00:00:00Z"))
        val newer = item("b", Instant.parse("2026-08-01T00:00:00Z"))
        val updated = item("a", Instant.parse("2026-08-02T00:00:00Z"), title = "a, edited")

        val merged = Rss.merge(listOf(older, newer), listOf(updated))

        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), merged.first().publishedAt)
        assertEquals("a, edited", merged.first().title)
    }

    @Test
    fun itemsWithoutADateSinkBelowDatedOnes() {
        val dated = item("dated", Instant.parse("2026-01-01T00:00:00Z"))
        val undated = item("undated")

        assertEquals(listOf("dated", "undated"), Rss.merge(listOf(undated), listOf(dated)).map { it.id })
    }

    @Test
    fun recognisesFeedPathsAndNotSiteHomes() {
        assertTrue(Rss.looksLikeFeedUrl("https://example.com/feed"))
        assertTrue(Rss.looksLikeFeedUrl("https://example.com/atom.xml"))
        assertTrue(Rss.looksLikeFeedUrl("https://example.com/blog/rss/"))
        assertTrue(Rss.looksLikeFeedUrl("https://example.com/index.RSS"))
        assertFalse(Rss.looksLikeFeedUrl("https://example.com/"))
        assertFalse(Rss.looksLikeFeedUrl("https://example.com/feedback"))
        assertFalse(Rss.looksLikeFeedUrl("example.com/feed"))
    }

    @Test
    fun aFeedIsNamedByItsAddressAndAWebSourcesListIsNotOne() {
        assertTrue(Rss.isFeedId(RssParser.feedId("https://example.com/feed/")))
        assertTrue(Rss.isFeedId("http://example.com/rss"))
        assertFalse(Rss.isFeedId("websources:lobsters"))
    }
}
