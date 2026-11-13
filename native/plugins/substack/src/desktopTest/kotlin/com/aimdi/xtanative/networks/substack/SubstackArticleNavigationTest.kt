package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.store.reading.ArticleReadPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jsoup.Jsoup

/** XTA's substack_article_navigation tests: the contents sheet's rows, the jump into the page, and the page's direction. */
class SubstackArticleNavigationTest {
    private val garden = SubstackArticle.parse(
        """
        <h2>Field notes from the garden</h2><p>It rained through the morning.</p>
        <h3>Making room for wildflowers</h3><p>Wildflowers need time and space, even in a small garden.</p>
        <h2>What changed this season</h2><p>A quiet place to read and notice the changing light.</p>
        """,
    )

    @Test
    fun contentsBecomeMatchingPassagesAndClearingTheSearchBringsBackTheHeadings() {
        assertEquals(listOf("Field notes from the garden", "Making room for wildflowers", "What changed this season"), garden.contents("").map { it.text })
        assertEquals(listOf("Wildflowers need time and space, even in a small garden."), garden.contents("time space").map { it.text })
        assertTrue(garden.contents("absent").isEmpty())
        assertEquals(garden.headings, garden.contents("  "))
    }

    @Test
    fun anArticleWithoutHeadingsStillFindsEverything() {
        val article = SubstackArticle.parse("<p>A short public article.</p>")
        assertTrue(article.contents("").isEmpty())
        assertEquals(listOf("A short public article."), article.contents("public").map { it.text })
    }

    @Test
    fun aChosenHeadingLeadsToItsOwnPlace() {
        val heading = garden.contents("")[1]
        assertEquals(garden.headings[1].anchor, heading.anchor)
        assertEquals(1, Jsoup.parseBodyFragment(garden.body).select("[data-xta-block=\"${heading.anchor}\"]").size)
        assertTrue(garden.blockOf(heading) != null)
    }

    @Test
    fun theJumpCarriesAnUntrustedTargetAsDataNotCode() {
        val anchor = "chapter'); steal(); //\n\"name"
        val script = SubstackWebPage.jump(anchor, publisherAnchor = true)
        assertTrue("const anchor = \"chapter'); steal(); //\\n\\\"name\";" in script, script)
        assertTrue("document.querySelector('.content')" in script)
        assertTrue("node.scrollIntoView" in script)
        assertTrue("window.xtaArticle.jumpTo(node)" in script)
    }

    @Test
    fun aJumpStopsTheFirstRestoringWithoutCountingAsReading() {
        val script = SubstackWebPage.readingBridge(ArticleReadPoint(), 18.0, 1.7)
        val jump = script.substring(script.indexOf("jumpTo: function"), script.indexOf("\n  };", script.indexOf("jumpTo: function")))
        assertTrue("jumped = true" in jump)
        assertTrue("observer.disconnect()" in jump)
        assertTrue("userScrolled = false" in jump)
        assertTrue("if (!jumped) move(saved)" in script)
        assertTrue("if (!interacted && !jumped) move(saved)" in script)
    }

    @Test
    fun thePageReadsRightToLeftWithScrollingTablesAndAMarkedPassage() {
        val post = SubstackPost("1", "مرحبا", "p", "https://example.substack.com", "Example")
        val colors = SubstackPageColors("#fff", "#000", "#888", "#05c", isDark = false, isRtl = true)
        val page = SubstackWebPage.wrap(post, "<h2>عنوان</h2><p>مقال</p>", colors, SubstackPageWords())
        assertEquals("rtl", Jsoup.parse(page).selectFirst("html")?.attr("dir"))
        listOf("border-inline-start", "padding-inline-start", "overflow-x: auto", "[data-xta-selected]").forEach { assertTrue(it in page, it) }
    }
}
