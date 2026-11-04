package com.aimdi.xtanative.networks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssItemsTest {
    private fun item(id: String) = RssItem(id = id, title = id, feedId = "https://a.example/feed", feedTitle = "A")

    @Test
    fun feedIdNormalisesHostCaseAndTrailingSlashes() {
        assertEquals("https://example.com/feed", RssParser.feedId("https://Example.COM/feed/"))
        assertEquals("https://example.com", RssParser.feedId(" https://example.com/// "))
        assertEquals("https://example.com/feed", RssParser.feedId("https://example.com:443/feed?format=rss#top"))
        assertEquals("http://example.com:8080/feed", RssParser.feedId("http://example.com:8080/feed"))
        assertEquals("example.com/feed", RssParser.feedId("example.com/feed"), "what is no web address stays as written")
    }

    @Test
    fun aBodyIsWorthReadingWhenItSaysMoreThanItsExcerpt() {
        assertTrue(item("a").copy(bodyHtml = "<p>Long</p>", excerpt = "Long").hasReadableBody)
        assertFalse(item("a").copy(bodyHtml = " Same ", excerpt = "Same").hasReadableBody)
        assertFalse(item("a").copy(bodyHtml = "  ").hasReadableBody)
    }
}
