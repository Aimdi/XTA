package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** XTA's substack_highlights_test, the cases SubstackHighlightsTest leaves out. */
class SubstackHighlightRulesTest {
    private fun lit(text: String, quote: String) = SubstackHighlights.shown(listOf(SubstackRun(text)), listOf(quote)).filter { it.highlighted }.map { it.run.text }

    @Test
    fun highlightedTextIsSplitOutOfItsRunsKeepingTheirStyles() {
        val runs = listOf(SubstackRun("Read "), SubstackRun("this link", href = "https://e.x"), SubstackRun(" now."))
        val out = SubstackHighlights.shown(runs, listOf("Read this", "now"))
        assertEquals(
            listOf(Triple("Read ", true, null), Triple("this", true, "https://e.x"), Triple(" link", false, "https://e.x"), Triple(" ", false, null), Triple("now", true, null), Triple(".", false, null)),
            out.map { Triple(it.run.text, it.highlighted, it.run.href) },
        )
    }

    @Test
    fun aShortQuoteIsAWordNotInsideOtherWordsAndChineseMatchesAnywhere() {
        assertEquals(listOf("an", "an"), lit("An plan, an answer and an.", "an"))
        assertEquals(listOf("Haus", "Haus"), lit("Ein Haus, kein Haus.", "Haus"))
        assertEquals(listOf("世界"), lit("我们的世界很大", "世界"))
        assertEquals(listOf("read", "read"), lit("re-read, read", "read"), "a hyphen ends a word")
        assertEquals(listOf("eading"), lit("Reading it", "eading"), "no whole word: where it was selected")
    }

    @Test
    fun twoPostsReadOneAfterTheOtherKeepEachOthersHighlights(): Unit = runBlocking {
        val kept = MemoryKept()
        val highlights = SubstackHighlights(kept)
        fun post(slug: String) = SubstackPost(slug, "T", slug, "https://fieldnotes.substack.com", "Field Notes", canonicalUrl = "https://fieldnotes.substack.com/p/$slug")
        highlights.add(post("a"), listOf("from a"))
        highlights.add(post("b"), listOf("from b"))
        highlights.add(post("a"), listOf("again a"))
        val read = SubstackHighlights(kept)
        assertEquals(listOf("from b"), read.load()[post("b").key])
        assertEquals(listOf("from a", "again a"), read.load()[post("a").key])
    }
}
