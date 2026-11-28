package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostExportTest {
    private val at = Instant.parse("2026-11-27T12:30:00Z")
    private val author = XUser("1", "jane", "Jane Doe")

    private fun post(id: String = "10", text: String = "hello", media: List<XMedia> = emptyList(), quoted: XPost? = null) =
        XPost(id, author, at, text, media = media, quoted = quoted)

    @Test
    fun `a post is its author, time, text and link`() {
        val markdown = PostExport.markdown(null, listOf(PostExport.of(post())))
        assertEquals("**Jane Doe** @jane · 2026-11-27 12:30 UTC\n\nhello\n\n<https://x.com/jane/status/10>\n", markdown)
    }

    @Test
    fun `markdown marks in a post are written as they are`() {
        assertEquals("\\# not a heading", PostExport.block("# not a heading"))
        assertEquals("a \\*b\\* \\_c\\_ \\[d\\]", PostExport.inline("a *b* _c_ [d]"))
        assertEquals("  \\- item", PostExport.block("  - item"))
        assertEquals("1\\. one", PostExport.block("1. one"))
    }

    @Test
    fun `line breaks stay line breaks`() {
        val markdown = PostExport.markdown(null, listOf(PostExport.of(post(text = "one\ntwo"))))
        assertTrue("one  \ntwo" in markdown)
    }

    @Test
    fun `pictures link to the web, or to the copy kept on the device`() {
        val media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/a.jpg", 1, 1), XMedia(XMedia.Kind.VIDEO, "https://pbs.twimg.com/v.jpg", 1, 1))
        val exported = PostExport.of(post(media = media), local = { if (it.endsWith("a.jpg")) "file:///kept/a b.jpg" else null })
        val markdown = PostExport.markdown(null, listOf(exported))
        assertTrue("![](file:///kept/a%20b.jpg)" in markdown)
        assertTrue("[▶](https://pbs.twimg.com/v.jpg)" in markdown)
    }

    @Test
    fun `a quote is a block quote under the post`() {
        val markdown = PostExport.markdown(null, listOf(PostExport.of(post(quoted = post("11", "quoted text")))))
        assertTrue("> **Jane Doe** @jane" in markdown)
        assertTrue("> quoted text" in markdown)
    }

    @Test
    fun `a thread is its posts under a title, between rules`() {
        val markdown = PostExport.markdown("Folder", listOf(PostExport.of(post("1")), PostExport.of(post("2"), note = "mine")))
        assertTrue(markdown.startsWith("# Folder\n\n"))
        assertEquals(1, Regex("\n---\n").findAll(markdown).count())
        assertTrue("> mine" in markdown)
    }

    @Test
    fun `the printable page escapes what it shows`() {
        val html = PostExport.html("A <b>", listOf(PostExport.of(post(text = "x < y & \"z\""))))
        assertTrue("<h1>A &lt;b&gt;</h1>" in html)
        assertTrue("x &lt; y &amp; &quot;z&quot;" in html)
        assertFalse("<script" in html)
    }

    @Test
    fun `file names lose what file systems refuse`() {
        assertEquals("a b c.md", PostExport.fileName("a/b:c", "md"))
        assertEquals("xta.html", PostExport.fileName("  ..", "html"))
    }
}
