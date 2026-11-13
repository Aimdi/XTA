package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class LinkPreviewsTest {
    private val article = "https://www.washingtonpost.com/politics/2026/10/08/budget-vote/"

    @Test
    fun anArticleIsCompactWithOrWithoutAPicture() {
        assertEquals(LinkPreviewLayout.COMPACT, LinkPreviews.layoutFor(article, hasImage = true))
        assertEquals(LinkPreviewLayout.COMPACT, LinkPreviews.layoutFor(article, hasImage = false))
    }

    @Test
    fun aVideoPageOrAPhotoKeepsTheLargePicture() {
        listOf("https://www.youtube.com/watch?v=abc", "https://youtu.be/abc", "https://m.youtube.com/watch?v=abc", "https://vimeo.com/123").forEach {
            assertEquals(LinkPreviewLayout.LARGE, LinkPreviews.layoutFor(it, hasImage = true), it)
        }
        assertEquals(LinkPreviewLayout.LARGE, LinkPreviews.layoutFor(article, hasImage = true, kind = "video"))
        assertEquals(LinkPreviewLayout.LARGE, LinkPreviews.layoutFor(article, hasImage = true, kind = "Photo"))
        assertEquals(LinkPreviewLayout.COMPACT, LinkPreviews.layoutFor("https://youtu.be/abc", hasImage = false))
    }

    @Test
    fun theDomainDropsWwwAndCase() {
        assertEquals("washingtonpost.com", LinkPreviews.domain("https://WWW.WashingtonPost.com/a"))
        assertEquals("news.ycombinator.com", LinkPreviews.domain("https://news.ycombinator.com/item"))
        assertEquals("nytimes.com", LinkPreviews.domain("nytimes.com"))
    }

    @Test
    fun aMissingTitleFallsBackToTheAddress() {
        assertEquals("www.washingtonpost.com/politics/2026/10/08/budget-vote/", LinkPreview(article, title = "  ").shownTitle)
        assertEquals("Budget vote", LinkPreview(article, title = " Budget vote ").shownTitle)
        assertEquals(false, LinkPreview(article, imageUrl = " ").hasImage)
    }

    @Test
    fun aPagesOwnTagsMakeItsPreview() {
        val html = """
            <html><head><title>Fallback title</title>
            <meta property="og:title" content="Budget vote &amp; what follows">
            <meta content="Senate leaders strike a deal." property="og:description">
            <meta name="twitter:image" content="/img/vote.jpg">
            <meta property="og:site_name" content="The Post">
            <meta property="og:type" content="video.other">
            </head><body></body></html>
        """.trimIndent()
        val preview = LinkPreviews.parse(html, article)!!
        assertEquals("Budget vote & what follows", preview.title)
        assertEquals("Senate leaders strike a deal.", preview.description)
        assertEquals("https://www.washingtonpost.com/img/vote.jpg", preview.imageUrl)
        assertEquals("The Post", preview.siteName)
        assertEquals("video", preview.kind)
        assertEquals(LinkPreviewLayout.LARGE, preview.layout)
    }

    @Test
    fun withoutTagsThePageTitleStandsAndWithoutAnythingThereIsNoPreview() {
        assertEquals("Just a title", LinkPreviews.parse("<title> Just a title </title>", article)?.title)
        assertNull(LinkPreviews.parse("<html><body>nothing</body></html>", article))
    }

    @Test
    fun theReaderFetchesPagesAndLeavesEverythingElse() = runBlocking {
        val http = FakeHttp { asked ->
            when (asked.url.encodedPath) {
                "/page" -> response(200, "<meta property=\"og:title\" content=\"A page\">", mapOf("content-type" to "text/html; charset=utf-8"))
                "/picture.png" -> response(200, "\u0089PNG", mapOf("content-type" to "image/png"))
                else -> response(404)
            }
        }
        val reader = LinkPreviewReader(http)
        assertEquals("A page", reader.read("https://example.com/page")?.title)
        assertNull(reader.read("https://example.com/picture.png"))
        assertNull(reader.read("https://example.com/gone"))
        assertNull(reader.read("mailto:a@b.c"))
        assertEquals(3, http.asked.size)
        assertEquals(true, http.asked.first().followRedirects)
    }
}
