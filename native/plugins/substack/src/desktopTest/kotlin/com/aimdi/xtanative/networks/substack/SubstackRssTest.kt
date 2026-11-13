package com.aimdi.xtanative.networks.substack

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

/** XTA's test/substack_rss_test.dart and the Beehiiv and home-page readers of substack_plugin_test.dart. */
class SubstackRssTest {
    private val channel = SubstackRss.parse(fixture("feed.xml"), "https://www.fieldnotes.example", "fallback")

    @Test
    fun aFeedGivesItsChannelAndItsPostsWithAudioEnclosuresAsEpisodes() {
        assertEquals("Field Notes", channel.title)
        assertEquals("Notes from the field.", channel.description)
        assertEquals("https://www.fieldnotes.example", channel.link)
        assertEquals("https://substackcdn.com/image/fetch/logo.png", channel.imageUrl)
        assertTrue(channel.looksLikeSubstack)
        assertEquals(listOf("episode-12", "tom-and-jerry"), channel.posts.map { it.slug }, "an item without a title or a post link is left out")

        val episode = channel.posts.first()
        assertEquals("episode-12", episode.id, "RSS has no number, so the slug names the post")
        assertEquals("Episode 12: the harbour", episode.title)
        assertEquals("Field Notes", episode.publicationName, "the channel's own title")
        assertEquals("Jordan", episode.authorName)
        assertEquals("2026-08-04T23:03:08.000Z", episode.postDate)
        assertEquals(Instant.parse("2026-08-04T23:03:08Z"), episode.publishedAt)
        assertEquals("https://api.substack.com/feed/podcast/12/ep.mp3?a=1&b=2", episode.audioUrl)
        assertTrue(episode.isPodcast)
        assertEquals("podcast", episode.type)
        assertEquals("https://substackcdn.com/image/fetch/cover.jpeg", episode.coverImage)
        assertEquals("An opening paragraph . And more.", episode.subtitle)
        assertTrue(episode.bodyHtml!!.startsWith("<p>An opening <strong>"))
        assertEquals("everyone", episode.audience)
        assertEquals("https://www.fieldnotes.example/p/episode-12", episode.canonicalUrl)
    }

    @Test
    fun rssTextOutsideCdataIsDecodedWhereXtaShowedTheEntitiesAsTheyStood() {
        val plain = channel.posts[1]
        assertEquals("Tom & Jerry <3", plain.title)
        assertEquals("Sam & Lee", plain.authorName)
        assertEquals("<p>Escaped &amp; plain.</p>", plain.bodyHtml)
        assertEquals("Escaped & plain.", plain.subtitle)
        assertNull(plain.audioUrl, "an image enclosure is no episode")
        assertEquals("newsletter", plain.type)
        assertEquals(Instant.parse("2026-08-05T10:00:00Z"), plain.publishedAt, "an RFC 822 date with an offset is read too")
    }

    @Test
    fun substacksFeedIsToldFromGhostsAndOthers() {
        assertTrue(SubstackRss.looksLikeSubstack("<rss><channel><generator>Substack</generator></channel></rss>"))
        assertFalse(SubstackRss.looksLikeSubstack("<rss><channel><generator>Ghost 6.59</generator></channel></rss>"))
        assertTrue(SubstackRss.looksLikeSubstack("<rss><channel><image><url>https://substackcdn.com/x.png</url></image></channel></rss>"))
    }

    @Test
    fun aMangledFeedGivesWhatItCanAndNeverThrows() {
        assertTrue(SubstackRss.parse("not xml at all", "https://x.example", "X").posts.isEmpty())
        val long = "word ".repeat(100)
        val item = """<rss><channel><item><title>T</title><link>https://x.example/p/t</link><description>$long</description></item></channel></rss>"""
        val post = SubstackRss.parse(item, "https://x.example", "X").posts.single()
        assertEquals(238, post.subtitle!!.length, "237 characters and an ellipsis")
        assertTrue(post.subtitle!!.endsWith("…"))
        assertNull(post.postDate)
        val untitled = SubstackRss.parse("""<rss><channel><link>https://x.example</link></channel></rss>""", "https://x.example", "X")
        assertNull(untitled.title)
        assertTrue(untitled.posts.isEmpty())
    }

    @Test
    fun aHomePagesOpenGraphTagsNameAPublicationThatLeftSubstacksJson() {
        val html = """<html><head><title>Home | Garbage Day</title><meta property="og:site_name" content="Garbage Day">
            <meta property="og:image" content="https://example.com/logo.png"></head></html>"""
        val found = SubstackPages.publicationOfHomepage(html, "https://www.garbageday.email".toHttpUrl())
        assertEquals(SubstackPublication("garbageday", "https://www.garbageday.email", "Garbage Day", logoUrl = "https://example.com/logo.png"), found)
        val titled = SubstackPages.publicationOfHomepage("<title>Home | Real Name | Substack</title>", "https://real.example".toHttpUrl())
        assertEquals("Real Name", titled?.name)
        assertNull(SubstackPages.publicationOfHomepage("<title>www</title>", "https://www.x.example".toHttpUrl()))
    }

    @Test
    fun aBeehiivSiteIsReadFromItsRemixStateElseItsTags() {
        val base = "https://www.garbageday.email".toHttpUrl()
        val remix = SubstackPages.publicationOfBeehiivHomepage(fixture("beehiiv_remix.html"), base)
        assertEquals("Garbage Day", remix?.name)
        assertEquals("A newsletter about having fun online {really}", remix?.description, "a brace inside a value does not end the state early")
        assertEquals("https://media.beehiiv.com/logo.png", remix?.logoUrl)
        assertEquals("garbageday", remix?.subdomain)
        assertEquals("Garbage Day", SubstackPages.publicationOfBeehiivHomepage(fixture("beehiiv_home.html"), base)?.name)
        assertNull(SubstackPages.publicationOfBeehiivHomepage("<html><title>Some blog</title></html>", base), "a page that is not Beehiiv's")
        assertEquals("""{"a":"x{y}z","b":{"c":1}}""", SubstackPages.jsonObjectAt("""{"a":"x{y}z","b":{"c":1}} trailing""", 0))
        assertEquals("v", SubstackPages.remixContext("""window.__remixContext = {"k":"v"};""")["k"].string)
    }

    @Test
    fun aBeehiivPostPageIsReadFromRemixThenJsonLdThenOpenGraph() {
        val fromRemix = SubstackPages.beehiivPost(fixture("beehiiv_remix.html"), "microdramas", "https://www.garbageday.email", "Garbage Day")
        assertEquals("Microdramas, from Remix", fromRemix?.title)
        assertTrue(fromRemix!!.isPaywalled)
        val otherSlug = fixture("beehiiv_remix.html").replace("\"slug\":\"microdramas\"", "\"slug\":\"other\"")
        assertNull(SubstackPages.beehiivPost(otherSlug, "microdramas", "https://www.garbageday.email", "Garbage Day"), "another slug's state is not this post")

        val og = SubstackPages.beehiivPost(fixture("beehiiv_post.html"), "microdramas", "https://www.garbageday.email", "Garbage Day")!!
        assertEquals("Ryan Broderick", og.authorName, "attributes in either order")
        assertEquals("https://example.com/cover.png", og.coverImage)
        assertEquals("Read to the end", og.subtitle)
        assertEquals("2026-08-26T19:49:07.448Z", og.postDate)

        fun page(ld: String) = """<html><head><script type="application/ld+json">$ld</script>
            <meta property="og:type" content="article"><meta property="og:title" content="From the OG tags"></head></html>"""
        fun read(ld: String) = SubstackPages.beehiivPost(page(ld), "odd", "https://www.garbageday.email", "Garbage Day")
        val ld = read("""{"@type":"Article","headline":"From JSON-LD","author":{"name":"Ryan"},"image":{"url":"https://example.com/c.png"},"datePublished":"2026-08-26","isAccessibleForFree":false}""")!!
        assertEquals("From JSON-LD", ld.title)
        assertEquals("Ryan", ld.authorName)
        assertEquals("https://example.com/c.png", ld.coverImage)
        assertEquals("2026-08-26", ld.postDate)
        assertTrue(ld.isPaywalled)
        assertEquals("https://www.garbageday.email/p/odd", ld.canonicalUrl)
        val odd = read("""{"@type":"Article","headline":"Odd","description":["a","b"],"datePublished":20260826,"url":{"@id":"x"},"author":[{"name":"Ryan"}]}""")!!
        assertEquals("Odd", odd.title, "threw a TypeError in XTA before BUG-15")
        assertNull(odd.subtitle)
        assertNull(odd.postDate)
        assertEquals("https://www.garbageday.email/p/odd", odd.canonicalUrl)
        assertEquals("From the OG tags", read("""{"@type":"Article","headline":["Not","a","title"]}""")?.title)
    }
}
