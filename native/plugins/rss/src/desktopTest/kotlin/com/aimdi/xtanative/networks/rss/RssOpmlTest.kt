package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssItem
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RssOpmlTest {
    private val blog = RssFeed(id = "https://blog.example/feed", feedUrl = "https://blog.example/feed", name = "Tom & \"Jerry\"", siteUrl = "https://blog.example/")

    @Test
    fun theFeedsAreWrittenAsOpmlTheyReadBackFrom() {
        val opml = RssOpml.export(listOf(blog), "RSS <follows>")

        assertTrue("<title>RSS &lt;follows&gt;</title>" in opml)
        assertTrue("""text="Tom &amp; &quot;Jerry&quot;"""" in opml)
        assertTrue("""htmlUrl="https://blog.example/"""" in opml)
        assertEquals(listOf("https://blog.example/feed"), RssOpml.feedUrls(opml))
    }

    @Test
    fun anOpmlFilesFeedsAreReadOnceEachFoldersAndSitesLeftOut() {
        val opml = """
            <opml version="1.0"><body>
              <outline text="Tech">
                <outline type="rss" text="A" xmlUrl="https://a.example/feed/" htmlUrl="https://a.example/"/>
                <outline type="rss" text="A again" xmlurl="https://A.example/feed"/>
              </outline>
              <outline text="Just a site" htmlUrl="https://site.example/"/>
              <outline type="rss" text="B" xmlUrl=" https://b.example/atom.xml "/>
              <outline type="rss" text="Broken" xmlUrl="not an address"/>
            </body></opml>
        """.trimIndent()

        assertEquals(listOf("https://a.example/feed/", "https://b.example/atom.xml"), RssOpml.feedUrls(opml))
        assertEquals(emptyList(), RssOpml.feedUrls("<html>no opml</html>"))
    }

    @Test
    fun anImportFollowsWhatIsNewKeepsWhatIsThereAndCountsWhatFails(): Unit = runBlocking {
        val followed = mutableListOf(blog)
        val heard = ArrayList<RssOpmlImport>()

        val result = RssOpml.import(
            urls = listOf("https://blog.example/feed/", "https://new.example/rss", "https://down.example/feed"),
            isFollowed = { id -> followed.any { it.id == id } },
            lookup = { url -> if ("down" in url) throw IOException("down") else RssFeed(id = url, feedUrl = url, name = "New") },
            follow = { followed += it },
            progress = { heard += it },
        )

        assertEquals(RssOpmlImport(added = 1, alreadyFollowed = 1, failed = 1, total = 3), result)
        assertEquals(listOf(0, 1, 2, 3), heard.map { it.done })
        assertEquals(listOf("https://blog.example/feed", "https://new.example/rss"), followed.map { it.id })
    }

    @Test
    fun aRouteKeepsWhatItNeedsToOpenItsScreenAgain() {
        val item = RssItem(id = "https://blog.example/feed::a&b=c d", title = "Ä title?", feedId = "https://blog.example/feed", feedTitle = "Blog", link = "https://blog.example/a?x=1")

        assertEquals(RssRoute.Item(item.id, item.feedId, "Blog", "Ä title?", item.link), RssRoutes.parse(RssRoutes.item(item)))
        assertEquals(item.copy(bodyHtml = null), (RssRoutes.parse(RssRoutes.item(item)) as RssRoute.Item).stub)
        assertEquals(RssRoute.Feed(blog.id, blog.feedUrl, blog.name), RssRoutes.parse(RssRoutes.feed(blog)))
        assertEquals(RssFeed(id = "https://x.example/feed", feedUrl = "https://x.example/feed", name = ""), (RssRoutes.parse("feed?id=https%3A%2F%2Fx.example%2Ffeed") as RssRoute.Feed).stub)
        assertEquals(RssRoute.Add, RssRoutes.parse(RssRoutes.ADD))
        assertEquals(null, RssRoutes.parse("item?title=x"))
        assertEquals(null, RssRoutes.parse("elsewhere"))
    }
}
