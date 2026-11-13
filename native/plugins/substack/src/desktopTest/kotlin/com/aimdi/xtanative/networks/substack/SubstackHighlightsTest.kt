package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Highlights, post snapshots and the plugin's routes, as XTA's substack_highlights, substack_models and substack_links tests read them. */
class SubstackHighlightsTest {
    private val post = SubstackPost(
        id = "42", title = "On plans", slug = "on-plans", publicationBaseUrl = "https://field.substack.com", publicationName = "Field Notes",
        authorName = "Jordan", canonicalUrl = "https://field.substack.com/p/on-plans", reactionCount = 7, wordCount = 900,
    )

    @Test
    fun aSelectionIsOneQuotePerLineWithItsSpacesCollapsed() {
        assertEquals(listOf("first line", "second"), SubstackHighlights.quotes("  first \t line \n\n x \nsecond"))
    }

    @Test
    fun quotesAreMarkedOnWholeWordsUnlessThatIsTheOnlyPlaceTheyOccur() {
        val runs = listOf(SubstackRun("A plan "), SubstackRun("and an end", bold = true))
        val shown = SubstackHighlights.shown(runs, listOf("an"))
        assertEquals(listOf("A plan ", "and ", "an", " end"), shown.map { it.run.text })
        assertEquals(listOf(false, false, true, false), shown.map { it.highlighted })
        assertTrue(shown.drop(1).all { it.run.bold }, "a split run keeps its style")
        val inside = SubstackHighlights.shown(listOf(SubstackRun("planet")), listOf("lane"))
        assertEquals(listOf("p", "lane", "t"), inside.map { it.run.text }, "a quote found only inside a word still shows")
        assertEquals(listOf("中文", "测试"), SubstackHighlights.shown(listOf(SubstackRun("中文测试")), listOf("测试")).map { it.run.text })
        assertEquals(runs, SubstackHighlights.shown(runs, listOf("absent")).map { it.run })
    }

    @Test
    fun highlightsAreKeptByPostNewestLastAndBoundedOldestPostFirst(): Unit = runBlocking {
        val kept = MemoryKept()
        val highlights = SubstackHighlights(kept)
        highlights.add(post, listOf("one", "two", "one"))
        highlights.add(post, listOf("two", "three"))
        assertEquals(listOf("one", "two", "three"), highlights.of(post))
        highlights.remove(post, "two")
        assertEquals("""{"field.substack.com/p/on-plans":["one","three"]}""", kept.text)
        val many = (1..3).associate { "post-$it" to List(900) { n -> "$it-$n" } }
        assertEquals(listOf("post-2", "post-3"), SubstackHighlights.bounded(many).keys.toList())
        assertEquals(listOf("big"), SubstackHighlights.bounded(mapOf("big" to List(2500) { "q$it" })).keys.toList(), "the newest stays whatever its size")
    }

    @Test
    fun highlightsShareAsMarkdownWithTheirSourceAndQuoteMarksTrimmed() {
        assertEquals(
            "# On plans\n\nField Notes · Jordan\n\nhttps://field.substack.com/p/on-plans\n\n> A quote\n\n> heading",
            SubstackHighlights.markdown(post, listOf("A quote", "## heading")),
        )
    }

    @Test
    fun aSnapshotReadsBackAsThePostWithoutItsBody() {
        val snapshot = SubstackPostJson.encode(post.copy(bodyHtml = "<p>body</p>", audioUrl = "https://a.example/e.mp3"))
        assertNull(J(snapshot)["body_html"].raw)
        val back = SubstackPostJson.decode(J(snapshot))
        assertEquals(post.copy(audioUrl = "https://a.example/e.mp3"), back)
        val older = SubstackPostJson.decode(J.parse("""{"id":7,"slug":"s","title":"T","postDate":"2026-01-01","canonicalUrl":"https://x.example/p/s","audioUrl":"https://a/b"}"""))
        assertEquals(listOf("7", "2026-01-01", "https://x.example/p/s", "https://a/b"), listOf(older.id, older.postDate, older.canonicalUrl, older.audioUrl))
        assertEquals(listOf("42"), SubstackPostJson.list("""[{"id":"42","slug":"on-plans"},{"id":"","slug":"x"},3,"y"]""").map { it.id })
        assertEquals(listOf("a"), SubstackPostJson.publications("""[{"subdomain":"a","baseUrl":"https://a.substack.com"},{"subdomain":"","baseUrl":"x"}]""").map { it.id })
    }

    @Test
    fun routesNameEachScreenAndReadBackWhatTheyNamed() {
        val route = SubstackRoutes.post(post)
        assertEquals(SubstackRoute.Post(SubstackPostLink("https://field.substack.com", "on-plans"), post.key), SubstackRoutes.parse(route))
        assertEquals(SubstackRoute.Comments(SubstackPostLink("https://field.substack.com", "on-plans"), post.key), SubstackRoutes.parse(SubstackRoutes.comments(post)))
        val publication = SubstackPublication("field", "https://www.field.example", "Field & Notes", logoUrl = "https://cdn.example/l.png?w=64")
        assertEquals(SubstackRoute.Publication(publication), SubstackRoutes.parse(SubstackRoutes.publication(publication)))
        assertEquals(SubstackRoute.Note("n-1"), SubstackRoutes.parse("note?id=n-1"))
        assertEquals(SubstackRoute.Add, SubstackRoutes.parse(SubstackRoutes.ADD))
        listOf("post?base=javascript:x&slug=a", "post?base=https://a.example", "publication?base=ftp://x", "note?id=", "other").forEach { assertNull(SubstackRoutes.parse(it), it) }
    }

    @Test
    fun linksToPostsOpenTheReaderAndAnythingElseStaysForTheBrowser() {
        val followed = listOf("https://www.field.example")
        assertEquals("post?base=https%3A%2F%2Fastral.substack.com&slug=lessons", SubstackRoutes.forUrl("https://astral.substack.com/p/lessons?utm=x", followed))
        assertEquals("post?base=https%3A%2F%2Fastral.substack.com&slug=lessons", SubstackRoutes.forUrl("https://open.substack.com/pub/astral/p/lessons", followed))
        assertEquals("post?base=https%3A%2F%2Fwww.field.example&slug=week-one", SubstackRoutes.forUrl("https://www.field.example/p/week-one", followed))
        listOf("https://astral.substack.com/", "https://astral.substack.com/archive", "https://unknown.example/p/x", "https://substack.com/@writer", "astral.substack.com/p/x")
            .forEach { assertNull(SubstackRoutes.forUrl(it, followed), it) }
    }
}
