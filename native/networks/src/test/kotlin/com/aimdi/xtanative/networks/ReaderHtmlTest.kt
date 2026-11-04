package com.aimdi.xtanative.networks

import com.aimdi.xtanative.networks.ReaderBlock.Text.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderHtmlTest {
    private fun blocks(html: String) = ReaderHtml.blocks(html, base = "https://blog.example/posts/1")

    @Test
    fun paragraphsKeepTheirRunsAndOneSpaceBetweenWords() {
        val found = blocks("<p>Hello   <b>bold</b> and <i>italic</i>\n world</p><p>Second <code>x()</code></p>")

        assertEquals(
            listOf(
                ReaderBlock.Text(Kind.PARAGRAPH, listOf(ReaderRun("Hello "), ReaderRun("bold", bold = true), ReaderRun(" and "), ReaderRun("italic", italic = true), ReaderRun(" world"))),
                ReaderBlock.Text(Kind.PARAGRAPH, listOf(ReaderRun("Second "), ReaderRun("x()", code = true))),
            ),
            found,
        )
    }

    @Test
    fun linksAndPicturesAreMadeAbsoluteAgainstTheArticle() {
        val found = blocks("""<p>See <a href="/about">this</a>.</p><img src="pics/a.png" alt=" A cat "><p><a href="mailto:x@y">mail</a></p>""")

        assertEquals(ReaderRun("this", href = "https://blog.example/about"), (found[0] as ReaderBlock.Text).runs[1])
        assertEquals(ReaderBlock.Image("https://blog.example/posts/pics/a.png", "A cat"), found[1])
        assertEquals(listOf(ReaderRun("mail")), (found[2] as ReaderBlock.Text).runs, "what is no web address is no link")
    }

    @Test
    fun headingsQuotesListsAndCodeAreTheirOwnBlocks() {
        val found = blocks(
            "<h2>Title</h2><blockquote><p>Quoted</p></blockquote>" +
                "<ul><li>One<ul><li>Inner</li></ul></li><li>Two</li></ul><ol><li>First</li></ol>" +
                "<pre>  let x = 1\n  x + 1\n</pre><hr><p>End</p>",
        )

        assertEquals(ReaderBlock.Text(Kind.HEADING, listOf(ReaderRun("Title", bold = true)), level = 2), found[0])
        assertEquals(ReaderBlock.Text(Kind.QUOTE, listOf(ReaderRun("Quoted"))), found[1])
        assertEquals(listOf("• One 0", "• Inner 1", "• Two 0", "1. First 0"), found.filterIsInstance<ReaderBlock.Text>().filter { it.kind == Kind.LIST_ITEM }.map { "${it.marker} ${it.text} ${it.level}" })
        assertEquals(ReaderBlock.Text(Kind.CODE, listOf(ReaderRun("  let x = 1\n  x + 1", code = true))), found.first { it is ReaderBlock.Text && it.kind == Kind.CODE })
        assertEquals(ReaderBlock.Divider, found[found.size - 2])
        assertEquals("End", (found.last() as ReaderBlock.Text).text)
    }

    @Test
    fun lineBreaksStayAndEmptyMarkupGivesNothing() {
        assertEquals("a\nb", (blocks("<p>a <br> b</p>").single() as ReaderBlock.Text).text)
        assertEquals(emptyList(), blocks("<p> </p><div>\n</div><hr>"))
        assertEquals(listOf("Loose text", "after"), blocks("Loose text<p>after</p>").map { (it as ReaderBlock.Text).text })
    }

    @Test
    fun scriptsNeverReachTheReaderAndTablesReadRowByRow() {
        val found = blocks("<script>alert(1)</script><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>")

        assertEquals(listOf("A · B", "1 · 2"), found.map { (it as ReaderBlock.Text).text })
        assertTrue(found.none { it is ReaderBlock.Text && "alert" in it.text })
    }
}
