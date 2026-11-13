package com.aimdi.xtanative.networks.rss

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
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
    fun anImportReadsAFewFeedsAtOnceAndFollowsThemInTheFilesOrder(): Unit = runBlocking {
        val urls = (1..10).map { "https://f$it.example/feed" }
        val followed = ArrayList<String>()
        var reading = 0
        var most = 0

        RssOpml.import(
            urls = urls,
            isFollowed = { false },
            lookup = { url ->
                reading++
                most = maxOf(most, reading)
                delay(if ("f1." in url) 20L else 1L)
                reading--
                RssFeed(id = url, feedUrl = url, name = url)
            },
            follow = { followed += it.id },
        )

        assertEquals(urls, followed)
        assertEquals(RssOpml.AT_ONCE, most)
    }
}
