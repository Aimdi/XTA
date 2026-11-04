package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RssRoutesTest {
    private val blog = RssFeed(id = "https://blog.example/feed", feedUrl = "https://blog.example/feed", name = "Tom & \"Jerry\"", siteUrl = "https://blog.example/")

    @Test
    fun aRouteKeepsWhatItNeedsToOpenItsScreenAgain() {
        val item = RssItem(id = "https://blog.example/feed::a&b=c d", title = "Ä title?", feedId = "https://blog.example/feed", feedTitle = "Blog", link = "https://blog.example/a?x=1")

        assertEquals(RssRoute.Item(item.id, item.feedId, "Blog", "Ä title?", item.link), RssRoutes.parse(RssRoutes.item(item)))
        assertEquals(item.copy(bodyHtml = null), (RssRoutes.parse(RssRoutes.item(item)) as RssRoute.Item).stub)
        assertEquals(RssRoute.Feed(blog.id, blog.feedUrl, blog.name), RssRoutes.parse(RssRoutes.feed(blog)))
        assertEquals(RssFeed(id = "https://x.example/feed", feedUrl = "https://x.example/feed", name = ""), (RssRoutes.parse("feed?id=https%3A%2F%2Fx.example%2Ffeed") as RssRoute.Feed).stub)
        assertEquals(RssRoute.Add, RssRoutes.parse(RssRoutes.ADD))
    }

    @Test
    fun aRouteWithoutWhatItNamesOpensNothing() {
        assertNull(RssRoutes.parse("item?title=x"))
        assertNull(RssRoutes.parse("feed?name=x"))
        assertNull(RssRoutes.parse("elsewhere"))
    }

    @Test
    fun theRoutesAreWrittenAsBefore() {
        assertEquals("feed?id=https%3A%2F%2Fblog.example%2Ffeed&url=https%3A%2F%2Fblog.example%2Ffeed&name=Tom+%26+%22Jerry%22", RssRoutes.feed(blog), "a screen kept on the back stack opens again after an update")
    }
}
