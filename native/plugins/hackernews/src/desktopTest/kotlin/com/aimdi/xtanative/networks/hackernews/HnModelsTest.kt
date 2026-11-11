package com.aimdi.xtanative.networks.hackernews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HnModelsTest {
    @Test
    fun htmlFlattensParagraphsAndKeepsLinkText() {
        assertEquals("Hello\n\nnext bit and X", HnHtml.toText("""Hello<p>next <i>bit</i> and <a href="https://x.com">X</a>"""))
        assertEquals("a &lt; b\n\ncode", HnHtml.toText("a &amp;lt; b<br>code"))
        assertEquals("", HnHtml.toText(null))
        assertEquals("", HnHtml.toText("   "))
    }

    @Test
    fun aLinkKeepsWhereItLeadsThoughHackerNewsShortenedItsText() {
        val read = HnHtml.read("""<p>See <a href="https://example.com/a/very/long/path">https://example.com/a/very/lo...</a> and <a href="item?id=8863">this</a>""")
        val text = "See https://example.com/a/very/lo... and this"
        assertEquals(text, read.text)
        assertEquals(
            listOf(
                HnTextLink(4, text.indexOf(" and"), "https://example.com/a/very/long/path"),
                HnTextLink(text.length - 4, text.length, "https://news.ycombinator.com/item?id=8863"),
            ),
            read.links,
        )
    }

    @Test
    fun aLinkThatLeadsNowhereReadableIsPlainText() {
        val read = HnHtml.read("""<a href="javascript:alert(1)">x</a> <a>y</a>""")
        assertEquals("x y", read.text)
        assertEquals(emptyList(), read.links)
    }

    @Test
    fun aThreadsCommentsAndAStorysTextCarryTheirLinks() {
        val story = HnParser.firebaseStory(com.aimdi.xtanative.core.json.J.parse("""{"id":1,"type":"story","title":"Ask HN","text":"Try <a href=\"https://kotlinlang.org\">Kotlin</a>"}"""), 1)
        assertEquals(listOf(HnTextLink(4, 10, "https://kotlinlang.org")), story?.links)
    }

    @Test
    fun aStoryKnowsItsHnPageAndItsSite() {
        val story = HnStory(id = 7, title = "Hello", url = "https://www.Example.com/x")
        assertEquals("example.com", story.host)
        assertEquals("https://news.ycombinator.com/item?id=7", story.hnUrl)
        assertNull(HnStory(id = 8, title = "Ask").host)
    }

    @Test
    fun collapsedCommentsHideTheirChildrenFromTheList() {
        val tree = listOf(
            HnComment(
                id = 1,
                author = "a",
                text = "top",
                children = listOf(
                    HnComment(id = 2, author = "b", text = "child"),
                    HnComment(id = 3, author = "c", text = "branch", children = listOf(HnComment(id = 4, author = "d", text = "leaf"))),
                ),
            ),
        )

        assertEquals(listOf(1L to 0, 2L to 1, 3L to 1, 4L to 2), HnComment.visible(tree, emptySet()).map { (c, depth) -> c.id to depth })
        assertEquals(listOf(1L, 2L, 3L), HnComment.visible(tree, setOf(3L)).map { it.first.id })
        assertEquals(listOf(1L), HnComment.visible(tree, setOf(1L)).map { it.first.id })
    }

    @Test
    fun linksToAStoryOrAUserAreRead() {
        assertEquals(HnLink.Story(8863), HnLinks.parse("https://news.ycombinator.com/item?id=8863"))
        assertEquals(HnLink.User("pg"), HnLinks.parse("https://www.news.ycombinator.com/user?id=pg"))
        assertEquals(HnLink.Story(1), HnLinks.parse("news.ycombinator.com/item?id=1"), "without a scheme, HTTPS is meant")
    }

    @Test
    fun theFrontPageOtherPagesAndOtherHostsAreNotLinks() {
        assertNull(HnLinks.parse("https://news.ycombinator.com/"))
        assertNull(HnLinks.parse("https://example.com/item?id=1"))
        assertNull(HnLinks.parse("https://news.ycombinator.com/item?id=abc"))
        assertNull(HnLinks.parse("https://news.ycombinator.com/item"))
        assertNull(HnLinks.parse("https://news.ycombinator.com/from?site=example.com&id=1"))
        assertNull(HnLinks.parse("mailto:news.ycombinator.com"))
        assertNull(HnLinks.parse(" "))
    }
}
