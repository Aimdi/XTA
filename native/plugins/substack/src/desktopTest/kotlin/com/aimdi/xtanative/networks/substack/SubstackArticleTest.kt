package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jsoup.Jsoup

/**
 * A body parsed once into what the reader shows: XTA's substack_blocks,
 * substack_open_parse, substack_article_navigation, substack_preview and
 * substack_read_time tests.
 */
class SubstackArticleTest {
    private fun blocks(html: String) = SubstackArticle.parse(html).blocks

    private fun post(audience: String? = null, body: String? = null, canonical: String? = "https://example.substack.com/p/a-post", words: Long? = null, audio: String? = null, type: String? = null) =
        SubstackPost("1", "A post", "a-post", "https://example.substack.com", "Example", audience = audience, bodyHtml = body, canonicalUrl = canonical, wordCount = words, audioUrl = audio, type = type)

    @Test
    fun paragraphsKeepTheirStylesLinksAndFootnoteMarkers() {
        val block = blocks(
            """<p>Plain <strong>bold <em>both</em></strong> <code>x()</code> <a href="https://example.com">link</a><a class="footnote-anchor" href="#footnote-1">1</a>.</p>""",
        ).single() as SubstackTextBlock
        assertEquals(SubstackTextBlock.Kind.PARAGRAPH, block.kind)
        assertEquals("Plain bold both x() link1.", block.text)
        assertEquals("xta-passage-0", block.anchor)
        val both = block.runs.first { it.text == "both" }
        assertTrue(both.bold && both.italic)
        assertTrue(block.runs.first { it.text == "x()" }.code)
        assertEquals("https://example.com", block.runs.first { it.text == "link" }.href)
        val marker = block.runs.first { it.text == "1" }
        assertEquals(1, marker.footnote)
        assertEquals("#footnote-1", marker.href)
    }

    @Test
    fun headingsQuotesNestedListsCodeDividersAndTablesComeOutInOrder() {
        val blocks = blocks(
            "<h2>Title</h2><blockquote><p>One</p><p>Two</p></blockquote>" +
                "<ol><li>First<ul><li>Inner</li></ul></li><li>Second</li></ol><pre>a\n  b</pre><hr>" +
                "<table><tr><th>H</th><td> cell </td></tr></table>",
        )
        val described = blocks.map { block ->
            when (block) {
                is SubstackTextBlock -> "${block.kind}:${block.level}:${block.marker.orEmpty()}:${block.text}"
                SubstackDividerBlock -> "divider"
                is SubstackTableBlock -> "table:${block.rows}"
                else -> "$block"
            }
        }
        assertEquals(
            listOf(
                "HEADING:2::Title", "QUOTE:0::One", "QUOTE:0::Two", "LIST_ITEM:0:1.:First", "LIST_ITEM:1:•:Inner",
                "LIST_ITEM:0:2.:Second", "CODE:0::a\n  b", "divider", "table:[[H, cell]]",
            ),
            described,
        )
    }

    @Test
    fun imagesHeldEmbedsWebOnlyGapsAndFootnotesAreTheirOwnBlocks() {
        val blocks = blocks(
            SubstackSynthetic.image(0, "https://cdn.example/a.jpg") +
                """<iframe src="https://www.youtube.com/embed/x"></iframe>""" +
                """<div class="latex-rendered" data-component-name="LatexBlockToDOM"></div>""" +
                """<div class="footnote"><a class="footnote-number" href="#a">3</a><div class="footnote-content"><p>Note.</p></div></div>""",
        )
        val image = blocks[0] as SubstackImageBlock
        assertEquals(Triple("https://cdn.example/a.jpg", 1456, 816), Triple(image.src, image.width, image.height))
        assertTrue(image.caption.joinToString("") { it.text }.startsWith("Figure 1."))
        assertEquals(SubstackEmbedBlock("youtube.com", "https://www.youtube.com/embed/x"), blocks[1])
        assertSame(SubstackWebOnlyBlock, blocks[2])
        val note = blocks[3] as SubstackTextBlock
        assertEquals(Triple(SubstackTextBlock.Kind.FOOTNOTE, "3", "Note."), Triple(note.kind, note.marker, note.text))
    }

    @Test
    fun theSyntheticLongPostLosesNoTextAndNoImage() {
        val article = SubstackArticle.parse(SubstackSynthetic.body())
        val text = article.blocks.filterIsInstance<SubstackTextBlock>()
        val images = article.blocks.filterIsInstance<SubstackImageBlock>()
        assertEquals(24, images.size)
        assertEquals(12, text.count { it.kind == SubstackTextBlock.Kind.FOOTNOTE })
        val words = Regex("[a-z]+")
        fun count(s: String) = words.findAll(s).count()
        val shown = text.sumOf { count(it.text) } + images.sumOf { image -> count(image.caption.joinToString("") { it.text }) }
        assertEquals(count(article.plainText), shown)
        assertTrue(text.count { it.anchor != null } > 200)
        assertEquals(40, article.headings.size)
    }

    @Test
    fun everyPassageContentsOrFindCanNameLeadsToTheBlockItIsDrawnIn() {
        val article = SubstackArticle.parse(
            """<h2>Start</h2><figure><img src="https://cdn.example/a.jpg"><figcaption>Caption</figcaption></figure>""" +
                "<ul><li><p>Item</p><ul><li>Nested</li></ul></li></ul><table><tr><td>Cell</td></tr></table><p>End</p>",
        )
        fun kind(index: Int?) = when (val block = article.blocks[index!!]) {
            is SubstackTextBlock -> block.text
            is SubstackImageBlock -> "image"
            is SubstackTableBlock -> "table"
            else -> "other"
        }
        assertEquals(listOf("Start", "image", "Item", "Nested", "table", "End"), article.passages.map { kind(article.blockOf(it)) })
        assertEquals(listOf("Start", "Caption", "Item", "Nested", "Cell", "End"), article.passages.map { it.text })
    }

    @Test
    fun aPassageFoldedIntoNoBlockOfItsOwnLeadsToTheOneBeforeIt() {
        val blocks = listOf(
            SubstackTextBlock(SubstackTextBlock.Kind.PARAGRAPH, listOf(SubstackRun("A")), anchors = listOf("xta-passage-0")),
            SubstackTextBlock(SubstackTextBlock.Kind.PARAGRAPH, listOf(SubstackRun("C")), anchors = listOf("xta-passage-2")),
        )
        val passages = listOf("xta-passage-0", "xta-passage-1", "xta-passage-2")
        assertEquals(0, SubstackBlocks.blockOfPassage(blocks, passages, "xta-passage-1"))
        assertEquals(1, SubstackBlocks.blockOfPassage(blocks, passages, "xta-passage-2"))
        assertNull(SubstackBlocks.blockOfPassage(blocks, passages, "unknown"))
    }

    @Test
    fun oneParseGivesTheSameSpeechTextAsReadingTheBodyAlone() {
        val bodies = listOf(
            SubstackSynthetic.body(sections = 8),
            "Just words, <em>no</em> blocks at all.",
            "<p>One<br>two</p><ul><li>a<ul><li>b</li></ul></li></ul><pre>x\n  y</pre>",
            "<table><tr><th>H</th><td>cell</td></tr></table><p>after</p>",
            """<p onclick="x()">Kept</p><script>gone()</script><style>p{}</style><button>no</button>""",
            """<p hidden>secret</p><p>A claim<a class="footnote-anchor" href="#f1">1</a>.</p>""",
        )
        bodies.forEach { body -> assertEquals(SubstackHtml.plainText(body), SubstackArticle.parse(body).plainText, body.take(40)) }
    }

    @Test
    fun anEmbedIsHeldBehindAPlaceholderUntilTheReaderAsksForIt() {
        val article = SubstackArticle.parse("""<p>Before</p><div class="youtube-wrap"><iframe src="https://www.youtube.com/embed/abc" allowfullscreen></iframe></div><p>After</p>""")
        assertEquals(1, Regex("<iframe").findAll(article.body).count())
        assertTrue("""<div class="xta-embed" data-host="youtube.com"><template><iframe""" in article.body)
        assertTrue("""src="https://www.youtube.com/embed/abc"""" in article.body)
        assertEquals("Before\n\nAfter", article.plainText)
        assertEquals(SubstackEmbedBlock("youtube.com", "https://www.youtube.com/embed/abc"), article.blocks.single { it is SubstackEmbedBlock })
        assertEquals("youtube.com", (SubstackArticle.parse("""<iframe src="//www.youtube.com/embed/x"></iframe>""").blocks.single() as SubstackEmbedBlock).host)
    }

    @Test
    fun contentsKeepPublisherTargetsAndTellDuplicateHeadingsApart() {
        val body = """<h2 id="intro">Introduction</h2><p>Start here.</p><h3><em>Details</em> &amp; links</h3><p>Next.</p><h2>Introduction</h2>"""
        val article = SubstackArticle.parse(body)
        assertEquals(listOf("Introduction", "Details & links", "Introduction"), article.headings.map { it.text })
        assertEquals(listOf(2, 3, 2), article.headings.map { it.headingLevel })
        assertEquals(3, article.headings.map { it.anchor }.toSet().size)
        assertTrue("id=\"intro\"" in article.body)
        assertEquals(article.body, SubstackArticle.parse(body).body, "the same body parses the same")
        article.headings.forEach { assertEquals(1, Jsoup.parseBodyFragment(article.body).select("[data-xta-block=${it.anchor}]").size) }
    }

    @Test
    fun nestedParagraphsAreIndexedOnceAndBareTextStillReads() {
        val nested = SubstackArticle.parse("<blockquote><p>Nested phrase</p></blockquote><ul><li>One item</li><li><p>Second item</p></li></ul>")
        assertEquals(listOf("Nested phrase", "One item", "Second item"), nested.passages.map { it.text })
        val plain = SubstackArticle.parse("Readable &amp; useful")
        assertEquals("Readable & useful", plain.passages.single().text)
        assertTrue(plain.headings.isEmpty())
        assertTrue("data-xta-block=\"xta-passage-0\"" in plain.body)
        assertEquals("Readable & useful", (plain.blocks.single() as SubstackTextBlock).text)
    }

    @Test
    fun findSeesOnlySanitizedVisibleWritingWithAllWordsInAnyOrder() {
        val article = SubstackArticle.parse(
            """<h2>GARDEN notes</h2><p>Rain waters the garden.</p><p>Another place.</p><script>Garden secret</script>
            <div class="paywall"><p>Garden forbidden</p></div><div hidden><p>Garden hidden</p></div>
            <p style="display: none">Garden invisible</p><p aria-hidden="true">Garden aria</p>""",
        )
        assertEquals("Rain waters the garden.", article.search("  garden RAIN ").single().text)
        assertEquals(2, article.search("garden").size)
        assertTrue(article.search("").isEmpty())
        assertTrue(article.search("nonexistent").isEmpty())
        assertFalse("<script>" in article.body)
    }

    @Test
    fun markersAPublisherLeftCannotCollideWithTheReadersOwn() {
        val article = SubstackArticle.parse("""<span data-xta-block="xta-passage-0">Label</span><p data-xta-block="malicious" onclick="run()">Actual writing</p>""")
        assertEquals(1, Jsoup.parseBodyFragment(article.body).select("[data-xta-block]").size)
        assertFalse("onclick" in article.body)
        assertEquals("xta-passage-0", article.passages.single().anchor)
        val literal = SubstackArticle.parse("&lt;img src=x onerror=run()&gt; &amp; ordinary text")
        assertEquals("<img src=x onerror=run()> & ordinary text", literal.passages.single().text, "a literal tag stays text")
        assertTrue(Jsoup.parseBodyFragment(literal.body).select("img").isEmpty())
    }

    @Test
    fun aResultIsCutAroundItsMatchWithoutMakingHugeRows() {
        val text = "${List(80) { "before" }.joinToString(" ")} needle ${List(80) { "after" }.joinToString(" ")}"
        val excerpt = SubstackPassage("p", text).excerpt("needle")
        assertTrue("needle" in excerpt)
        assertTrue(excerpt.length <= 242)
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("…"))
        assertEquals("short text", SubstackPassage("p", "short text").excerpt("text"))
    }

    @Test
    fun aParsedArticleIsReusedOnlyForTheSamePostDateAndBody() {
        val cache = SubstackParsedArticles(capacity = 2)
        val one = SubstackArticle.parse("<p>One</p>")
        val two = SubstackArticle.parse("<p>Two</p>")
        val three = SubstackArticle.parse("<p>Three</p>")
        cache.put("a\n2026-09-01", "<p>One</p>", one)
        assertSame(one, cache.get("a\n2026-09-01", "<p>One</p>"))
        assertNull(cache.get("a\n2026-09-01", "<p>One, edited</p>"))
        assertNull(cache.get("a\n2026-09-02", "<p>One</p>"))
        cache.put("a\n2026-09-01", "<p>One</p>", one)
        cache.put("b", "<p>Two</p>", two)
        cache.get("a\n2026-09-01", "<p>One</p>")
        cache.put("c", "<p>Three</p>", three)
        assertNull(cache.get("b", "<p>Two</p>"), "least recently used goes first")
        assertSame(one, cache.get("a\n2026-09-01", "<p>One</p>"))
        assertSame(three, cache.get("c", "<p>Three</p>"))

        val posts = SubstackParsedArticles()
        val read = post(body = "<p>Body</p>")
        assertSame(posts.of(read), posts.of(read.copy()))
        assertNotSame(posts.of(read), posts.of(read.copy(postDate = "2026-09-02T00:00:00Z")), "an edited post is parsed again")
        assertNull(posts.of(post()))
    }

    @Test
    fun aPaidPostWithoutABodyGetsThePaidPaneNotTheLiveSite() {
        assertEquals(SubstackReaderBody.PAYWALL, SubstackReading.bodyFor(post(audience = "only_paid")))
        assertEquals(SubstackReaderBody.PAYWALL, SubstackReading.bodyFor(post(audience = "only_paid", body = "  ", canonical = null)))
        assertEquals(SubstackReaderBody.ARTICLE, SubstackReading.bodyFor(post(audience = "only_paid", body = "<p>The opening.</p>")))
        assertTrue(SubstackReading.isPreview(post(audience = "only_paid", body = "<p>The opening.</p>")), "the free opening of a paid post ends in a note")
        assertFalse(SubstackReading.isPreview(post(audience = "everyone", body = "<p>All of it.</p>")))
        assertEquals(SubstackReaderBody.LIVE_SITE, SubstackReading.bodyFor(post(audience = "everyone")))
        assertEquals(SubstackReaderBody.EMPTY, SubstackReading.bodyFor(post(audience = "everyone", canonical = null)))
    }

    @Test
    fun aPostThatCannotBeReadShowsItsPageWhereAWebViewCan() {
        assertTrue(SubstackReading.fallsBackToSite(post(audience = "everyone"), webView = true), "as XTA's reader loads the publication's page")
        assertFalse(SubstackReading.fallsBackToSite(post(audience = "everyone"), webView = false), "the desktop keeps the failure, with Retry")
        assertFalse(SubstackReading.fallsBackToSite(post(audience = "everyone", canonical = null), webView = true), "no page to show")
    }

    @Test
    fun readTimeIsTheWordsAt230AMinuteRoundedUpAtLeastOne() {
        assertEquals(1, SubstackReading.readMinutes(post(words = 1)))
        assertEquals(1, SubstackReading.readMinutes(post(words = 230)))
        assertEquals(2, SubstackReading.readMinutes(post(words = 231)))
        assertEquals(10, SubstackReading.readMinutes(post(words = 2300)))
        assertEquals(2, SubstackReading.readMinutes(post(words = 23_000), plainText = List(460) { "word" }.joinToString(" ")), "the body counts instead of the API figure")
        assertNull(SubstackReading.readMinutes(post(words = 900, audio = "https://cdn.example/a.mp3")))
        assertNull(SubstackReading.readMinutes(post(words = 900, type = "video")))
        assertNull(SubstackReading.readMinutes(post()))
        assertNull(SubstackReading.readMinutes(post(words = 0)))
        assertNull(SubstackReading.readMinutes(post(words = 900), plainText = " "), "a body with no words gives no estimate")
    }

    @Test
    fun aLinkJumpsInsideTheArticleOnlyWhenItIsThisArticle() {
        val canonical = "https://example.substack.com/p/reading"
        assertEquals("chapter 2", SubstackReading.articleFragment("#chapter%202", canonical))
        assertEquals("notes", SubstackReading.articleFragment("/p/reading#notes", canonical))
        assertEquals("notes", SubstackReading.articleFragment("$canonical/?utm_source=reader#notes", canonical))
        assertEquals("notes", SubstackReading.articleFragment("#notes", null))
        assertEquals("ü", SubstackReading.articleFragment("#%C3%BC", canonical))
        assertNull(SubstackReading.articleFragment("https://elsewhere.substack.com/p/reading#notes", canonical))
        assertNull(SubstackReading.articleFragment("/p/other#notes", canonical))
        assertNull(SubstackReading.articleFragment("http://example.substack.com/p/reading#notes", canonical))
        assertNull(SubstackReading.articleFragment("/p/reading", canonical))
        assertNull(SubstackReading.articleFragment("$canonical#", canonical))
        assertNull(SubstackReading.articleFragment("#%FF", canonical))
        assertNull(SubstackReading.articleFragment("https://[invalid/#notes", canonical))
    }
}
