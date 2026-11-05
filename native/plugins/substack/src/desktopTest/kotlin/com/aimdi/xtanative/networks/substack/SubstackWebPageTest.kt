package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.store.reading.ArticleReadPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The web-view reader's page, its links and "Speak from here": XTA's substack_html, substack_reader and tts-from-here tests. */
class SubstackWebPageTest {
    private val colors = SubstackPageColors("#ffffff", "#111111", "#666666", "#1d9bf0", isDark = false)

    private fun post(title: String = "A post", excerpt: String? = null, canonical: String? = "https://example.substack.com/p/a-post") =
        SubstackPost("1", title, "a-post", "https://example.substack.com", "Example", subtitle = excerpt, canonicalUrl = canonical)

    @Test
    fun thePageEscapesWhatThePublisherWroteAroundTheBody() {
        val page = SubstackWebPage.wrap(post(title = "<script>x()</script> & more", excerpt = "\"quoted\""), "<p>Body</p>", colors, SubstackPageWords(readTime = "3 min read"))
        assertFalse("<script>x()</script>" in page)
        assertTrue("&lt;script&gt;x()&lt;/script&gt; &amp; more" in page)
        assertTrue("<p class=\"subtitle\">&quot;quoted&quot;</p>" in page)
        assertTrue("Example · 3 min read" in page)
        assertTrue("<div class=\"content\"><p>Body</p></div>" in page)
        assertTrue("window.xtaSpeakFromSelection" in page, "a selection can start reading aloud")
    }

    @Test
    fun theLabelsCannotCloseTheirScript() {
        val page = SubstackWebPage.wrap(post(), "", colors, SubstackPageWords(embedLabel = "</script><script>alert(1)</script>"))
        assertFalse("</script><script>alert(1)" in page)
        assertTrue("\\u003c/script>" in page)
    }

    @Test
    fun aPreviewEndsWithItsNoteAndTheWayToTheRest() {
        val words = SubstackPageWords(footer = "The preview ends here.", footerLink = "https://example.substack.com/p/a-post", footerLinkLabel = "Continue on the site")
        val page = SubstackWebPage.wrap(post(), "<p>Free part</p>", colors, words)
        assertTrue("<div class=\"preview-end\"><p>The preview ends here.</p><p><a href=\"https://example.substack.com/p/a-post\">Continue on the site</a></p></div>" in page)
        assertFalse("preview-end\"" in SubstackWebPage.wrap(post(), "<p>All</p>", colors, SubstackPageWords()))
    }

    @Test
    fun theBridgeStartsFromTheKeptPlaceAndTheJumpQuotesItsAnchor() {
        val bridge = SubstackWebPage.readingBridge(ArticleReadPoint(fraction = 0.5, paragraph = 7), 20.0, 1.8)
        assertTrue("\"paragraph\":7" in bridge)
        assertTrue("window.xtaArticle.apply(20.0, 1.8)" in bridge)
        assertTrue("const anchor = \"it's \\\"here\\\"\";" in SubstackWebPage.jump("it's \"here\""))
    }

    @Test
    fun aScriptsAnswerIsReadAsText() {
        assertEquals("Line one\nLine two", SubstackWebPage.scriptResult("\"Line one\\nLine two\""))
        assertEquals("true", SubstackWebPage.scriptResult("true"))
        assertEquals(null, SubstackWebPage.scriptResult("null"))
        assertEquals(null, SubstackWebPage.scriptResult(null))
    }

    @Test
    fun linksInTheArticleGoWhereXtasReaderSendsThem() {
        val post = post()
        fun step(url: String, live: Boolean = false) = SubstackWebNavigation.step(url, post, live)
        assertEquals(SubstackWebStep.Follow, step("about:blank"))
        assertEquals(SubstackWebStep.Jump("footnote-1"), step("https://example.substack.com/p/a-post#footnote-1"))
        assertEquals(SubstackWebStep.Jump("footnote-1"), step("#footnote-1"))
        assertEquals(SubstackWebStep.OpenPost(SubstackPostLink("https://other.substack.com", "another")), step("https://other.substack.com/p/another"))
        assertEquals(SubstackWebStep.OpenLink, step("https://example.com/elsewhere"))
        assertEquals(SubstackWebStep.Follow, step("https://example.substack.com/p/a-post", live = true), "the live page may reload itself")
        assertEquals(SubstackWebStep.Follow, step("https://example.com/elsewhere", live = true), "the live page is a website")
        assertEquals(SubstackWebStep.OpenPost(SubstackPostLink("https://other.substack.com", "another")), step("https://other.substack.com/p/another", live = true))
    }

    @Test
    fun speakingFromAParagraphReadsOnFromThere() {
        val body = "<p>Same words.</p><p>The first paragraph<a class=\"footnote-anchor\" href=\"#footnote-1\">1</a> says this.</p>" +
            "<figure><img src=\"https://example.com/a.png\"></figure><p>Same words.</p><p>The last one.</p>"
        val article = SubstackArticle.parse(body)
        val full = SubstackHtml.speakText("A post", bodyPlain = article.plainText)
        val blocks = article.blocks
        assertEquals(full, SubstackSpeakFrom.fromBlock(full, blocks, -1), "the header reads it all")
        val first = SubstackSpeakFrom.fromBlock(full, blocks, 1)
        assertTrue(first.startsWith("The first paragraph says this."), "footnote numbers are not spoken")
        assertEquals(full.substring(full.indexOf("The first")), first)
        assertEquals(full.substring(full.lastIndexOf("Same words.")), SubstackSpeakFrom.fromBlock(full, blocks, 2), "a picture starts at what follows, and a repeated paragraph is the later one")
        assertEquals("The last one.", SubstackSpeakFrom.fromBlock(full, blocks, 4))
    }
}
