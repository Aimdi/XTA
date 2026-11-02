package com.aimdi.xtanative.networks.rss

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

class RssParserTest {
    private fun fixture(name: String) = javaClass.getResource("/rss/$name")!!.readText()

    @Test
    fun readsRss2ChannelMetadataAndItems() {
        val channel = RssParser.parse(fixture("slow_boring.xml"), feedUrl = "https://www.slowboring.com/feed")

        assertEquals("Slow Boring", channel.title)
        assertEquals("Pragmatic takes.", channel.description)
        assertEquals("https://www.slowboring.com", channel.link)
        assertEquals("https://example.org/logo.png", channel.imageUrl)
        val item = channel.items.single()
        assertEquals("https://www.slowboring.com/feed::https://www.slowboring.com/p/hello-there", item.id)
        assertEquals("Hello", item.title)
        assertEquals("https://www.slowboring.com/p/hello-there", item.link)
        assertEquals("Matt", item.author)
        assertEquals(listOf("Politics"), item.categories)
        assertEquals("https://example.org/cover.jpg", item.imageUrl)
        assertEquals(Instant.parse("2026-08-04T23:03:08Z"), item.publishedAt)
        assertEquals("An opening paragraph.", item.excerpt)
        assertEquals("<p>An opening paragraph.</p>", item.bodyHtml)
        assertEquals("Slow Boring", item.feedTitle)
    }

    @Test
    fun readsAtomEntriesAndHtmlLinks() {
        val channel = RssParser.parse(fixture("atom_example.xml"), feedUrl = "https://example.org/atom.xml")

        assertEquals("Example Feed", channel.title)
        assertEquals("https://example.org/", channel.link)
        val item = channel.items.single()
        assertEquals("Atom-Powered Robots Run Amok", item.title)
        assertEquals("https://example.org/2003/12/13/atom03", item.link)
        assertEquals("John Doe", item.author)
        assertEquals(listOf("robots"), item.categories)
        assertEquals(Instant.parse("2003-12-13T18:30:02Z"), item.publishedAt)
        assertEquals("Some text.", item.excerpt)
        assertEquals("https://example.org/atom.xml::urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a", item.id)
    }

    @Test
    fun skipsItemsThatHaveNoTitleLinkOrGuid() {
        val xml = """<rss><channel>
            <item><description>orphan</description></item>
            <item><title>Kept</title><link>https://example.org/kept</link></item>
            </channel></rss>"""

        val channel = RssParser.parse(xml, feedUrl = "https://example.org/feed")

        assertEquals(listOf("Kept"), channel.items.map { it.title })
    }

    @Test
    fun doesNotThrowOnMangledMarkup() {
        for (body in listOf("<not-a-feed", "", "<rss><channel><item><title>Cut", "<html><body><p>A page</p></body></html>")) {
            RssParser.parse(body, feedUrl = "https://example.org/feed")
        }
        assertTrue(RssParser.parse("<not-a-feed", feedUrl = "https://example.org/feed").items.isEmpty())
        assertTrue(RssParser.parse("", feedUrl = "https://example.org/feed").items.isEmpty())
    }

    @Test
    fun theChannelsOwnTitleIsReadNotItsImagesWhichXtaTookWhenTheImageCameFirst() {
        val channel = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml")

        assertEquals("Example Podcast – Weekly", channel.title, "numeric entities are decoded too")
        assertEquals("Talk & music.", channel.description)
        assertEquals("https://podcast.example/", channel.link, "an atom:link beside it is not the site")
        assertEquals("https://podcast.example/logo.png", channel.imageUrl)
    }

    @Test
    fun everyEntityIsDecodedOnceWhereXtaLeftNumericOnesAndDecodedAmpersandsTwice() {
        val xml = """<rss><channel><title>A &#8217;B&#x2019; &amp;lt;tag&amp;gt;</title>
            <item><title>It&#8217;s</title><link>https://x.example/1</link></item></channel></rss>"""

        val channel = RssParser.parse(xml, feedUrl = "https://x.example/feed")

        assertEquals("A ’B’ &lt;tag&gt;", channel.title)
        assertEquals("It’s", channel.items.single().title)
    }

    @Test
    fun contentEncodedIsTheBodyAndTheExcerptIsItsText() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[0]

        assertEquals("https://podcast.example/feed.xml::ep-1", item.id)
        assertEquals("Episode 1: It’s here", item.title)
        assertEquals("""<p>The <b>full</b> notes &amp; links.</p><img src="https://podcast.example/notes.jpg">""", item.bodyHtml)
        assertEquals("The full notes & links.", item.excerpt)
        assertEquals("host@podcast.example (Host)", item.author)
        assertEquals(listOf("Music"), item.categories)
        assertTrue(item.hasReadableBody)
    }

    @Test
    fun anRfc822DateKeepsItsOffsetWhichXtaDropped() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[0]

        assertEquals(Instant.parse("2026-08-04T21:03:08Z"), item.publishedAt)
    }

    @Test
    fun enclosuresAreKeptAndAnAudioOneIsNotTakenForThePictureAsXtaTookIt() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[0]

        assertEquals(
            listOf(
                RssEnclosure("https://cdn.podcast.example/images/ep1.mp3", "audio/mpeg", 12_345_678),
                RssEnclosure("https://cdn.podcast.example/ep1-cover", "image/jpeg", 2048),
            ),
            item.enclosures,
        )
        assertEquals("https://cdn.podcast.example/ep1-cover", item.imageUrl, "the typed picture, not the mp3 under /images/")
    }

    @Test
    fun mediaContentThatIsAVideoIsPassedOverForTheOneThatIsAPicture() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[1]

        assertEquals("https://cdn.podcast.example/still.jpg", item.imageUrl)
        assertEquals(Instant.parse("2026-08-03T08:00:00Z"), item.publishedAt, "dc:date, with its offset")
        assertEquals("Fallback Author", item.author, "an empty dc:creator gives way to author")
        assertEquals("Plain text, no markup.", item.excerpt)
        assertFalse(item.hasReadableBody, "a body that is its excerpt has nothing more to read")
    }

    @Test
    fun aThumbnailInAMediaGroupIsThePictureAndADateThatDoesNotReadGivesWayToTheNext() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[2]

        assertEquals("https://cdn.podcast.example/thumb.jpg", item.imageUrl)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), item.publishedAt)
    }

    @Test
    fun anUntitledItemTakesTheFeedsTitleAndTheBodysFirstPicture() {
        val item = RssParser.parse(fixture("podcast.xml"), feedUrl = "https://podcast.example/feed.xml").items[3]

        assertEquals("Example Podcast – Weekly", item.title)
        assertEquals("https://podcast.example/feed.xml::https://podcast.example/4", item.id)
        assertEquals("https://podcast.example/4.png", item.imageUrl)
    }

    @Test
    fun aFeedWithoutATitleIsNamedByItsId() {
        val channel = RssParser.parse("<rss><channel><item><link>https://x.example/a</link></item></channel></rss>", "https://X.example/feed/")

        assertNull(channel.title)
        assertEquals("https://x.example/feed", channel.items.single().title)
        assertEquals("https://x.example/feed", channel.items.single().feedTitle)
    }

    @Test
    fun rdfItemsBesideTheChannelAreReadWhereXtaFoundNone() {
        val channel = RssParser.parse(fixture("rdf.xml"), feedUrl = "https://news.example/index.rdf")

        assertEquals("News Example", channel.title)
        assertEquals("News for readers", channel.description)
        assertEquals("https://news.example/", channel.link)
        assertEquals("https://news.example/logo.gif", channel.imageUrl)
        assertEquals(listOf("First story", "Second story"), channel.items.map { it.title })
        val first = channel.items[0]
        assertEquals("https://news.example/story/1", first.link)
        assertEquals("editor", first.author)
        assertEquals("The first story.", first.excerpt)
        assertEquals(Instant.parse("2026-10-29T08:00:00Z"), first.publishedAt)
        assertEquals("https://news.example/index.rdf::https://news.example/story/1", first.id)
    }

    @Test
    fun atomFeedLinksPreferTheHtmlAlternateAndTheLogo() {
        val channel = RssParser.parse(fixture("atom_rich.xml"), feedUrl = "https://atom.example/feed.atom")

        assertEquals("Rich Atom", channel.title)
        assertEquals("Everything Atom can say", channel.description)
        assertEquals("https://atom.example/", channel.link)
        assertEquals("https://atom.example/logo.png", channel.imageUrl)
        assertEquals(3, channel.items.size)
    }

    @Test
    fun atomXhtmlContentKeepsItsMarkupWhichXtaFlattenedToText() {
        val entry = RssParser.parse(fixture("atom_rich.xml"), feedUrl = "https://atom.example/feed.atom").items[0]

        assertEquals("<p>Hello <b>there</b></p>", entry.bodyHtml)
        assertEquals("Hello there", entry.excerpt)
        assertEquals("https://atom.example/1", entry.link, "replies and enclosure links are not the entry's link")
        assertEquals(Instant.parse("2026-10-29T09:00:00.250Z"), entry.publishedAt, "published wins over updated")
        assertEquals(listOf("news"), entry.categories)
        assertEquals(listOf(RssEnclosure("https://atom.example/1.png", "image/png", 512)), entry.enclosures)
        assertEquals("https://atom.example/1.png", entry.imageUrl)
    }

    @Test
    fun anAtomAuthorWithoutANameIsItsTextWhereXtaKeptItsRawMarkup() {
        val entry = RssParser.parse(fixture("atom_rich.xml"), feedUrl = "https://atom.example/feed.atom").items[0]

        assertEquals("someone@atom.example", entry.author)
    }

    @Test
    fun aYouTubeEntryTakesItsThumbnailNotItsPlayer() {
        val entry = RssParser.parse(fixture("atom_rich.xml"), feedUrl = "https://atom.example/feed.atom").items[1]

        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", entry.imageUrl)
        assertEquals("https://www.youtube.com/watch?v=abc", entry.link)
        assertEquals("Channel", entry.author)
        assertEquals(Instant.parse("2026-10-28T10:00:00Z"), entry.publishedAt, "updated stands in for published")
        assertNull(entry.excerpt)
    }

    @Test
    fun anAtomEntryWithOnlyASelfLinkUsesItAndAnEscapedSummaryIsItsBody() {
        val entry = RssParser.parse(fixture("atom_rich.xml"), feedUrl = "https://atom.example/feed.atom").items[2]

        assertEquals("Rich Atom", entry.title)
        assertEquals("https://atom.example/entries/3.atom", entry.link)
        assertEquals("https://atom.example/feed.atom::urn:only-self", entry.id)
        assertEquals("<p>Escaped <em>summary</em></p>", entry.bodyHtml)
        assertEquals("Escaped summary", entry.excerpt)
    }

    @Test
    fun discoverFeedUrlsResolvesAlternateRssAndAtomLinksAgainstThePage() {
        val found = RssParser.discoverFeedUrls(fixture("site.html"), page = "https://blog.example.org/about".toHttpUrl())

        assertEquals(listOf("https://blog.example.org/feed.xml", "https://cdn.example.org/atom.xml"), found)
    }

    @Test
    fun commonFeedPathsAreOnTheSitesHostWithoutItsQuery() {
        assertEquals(
            listOf(
                "https://blog.example.org/feed",
                "https://blog.example.org/rss",
                "https://blog.example.org/atom.xml",
                "https://blog.example.org/feed.xml",
                "https://blog.example.org/index.xml",
                "https://blog.example.org/rss.xml",
            ),
            RssParser.commonFeedPaths("https://blog.example.org/about?tab=1#top".toHttpUrl()),
        )
    }

    @Test
    fun aFeedDocumentIsToldFromAWebPage() {
        assertTrue(RssParser.looksLikeFeed(fixture("slow_boring.xml")))
        assertTrue(RssParser.looksLikeFeed(fixture("rdf.xml")))
        assertTrue(RssParser.looksLikeFeed("\uFEFF  <feed xmlns=\"http://www.w3.org/2005/Atom\">"))
        assertFalse(RssParser.looksLikeFeed(fixture("site.html")))
        assertFalse(RssParser.looksLikeFeed("<html><body><rss></rss></body></html>"))
        assertFalse(RssParser.looksLikeFeed("{\"json\": true}"))
    }
}
