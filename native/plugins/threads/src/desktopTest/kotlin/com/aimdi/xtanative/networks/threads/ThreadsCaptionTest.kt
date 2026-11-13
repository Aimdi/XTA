package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tags, mentions and links in captions, as XTA's threads_rich_text_test pins them. */
class ThreadsCaptionTest {
    @Test
    fun handlesAndWebAddressesAreFound() {
        val hits = ThreadsCaption.hits("Hi @Alice and https://example.com/x, bye")
        assertEquals(listOf(Kind.MENTION to "Alice", Kind.LINK to "https://example.com/x"), hits.map { it.kind to it.value })
        assertEquals("@Alice", "Hi @Alice and https://example.com/x, bye".substring(hits[0].start, hits[0].end))
    }

    @Test
    fun tagsAreFoundInPlainTextButNotInsideALinkOrAWord() {
        val hits = ThreadsCaption.hits("Glaze day #Keramik #töpfern, see https://example.com/a#notes and C#7")
        assertEquals(listOf("Keramik", "töpfern"), hits.filter { it.kind == Kind.TAG }.map { it.value })
        val runs = ThreadsCaption.runs("see https://example.com/a#notes", emptyList())
        assertTrue(runs.none { it.kind == Kind.TAG }, "the # is part of the link")
    }

    @Test
    fun emailShapedTextIsNoMention() {
        assertTrue(ThreadsCaption.hits("write me@example.com please").none { it.kind == Kind.MENTION })
    }

    @Test
    fun trailingPunctuationIsNotPartOfAnAddress() {
        assertEquals("https://a.co/x", ThreadsCaption.trimUrl("https://a.co/x."))
        assertEquals("https://a.co/x", ThreadsCaption.trimUrl("https://a.co/x)"))
        assertEquals("https://a.co/x", ThreadsCaption.trimUrl("https://a.co/x!?\"'"))
    }

    @Test
    fun withoutMetasFragmentsTheRunsComeFromPatternsAndSpellTheCaption() {
        val caption = "Hi @alice see https://a.co/x."
        val runs = ThreadsCaption.runs(caption, emptyList())
        assertEquals(listOf(Kind.TEXT, Kind.MENTION, Kind.TEXT, Kind.LINK, Kind.TEXT), runs.map { it.kind })
        assertEquals("alice", runs[1].target)
        assertEquals("https://a.co/x", runs[3].target)
        assertEquals(caption, runs.joinToString("") { it.text })
    }

    @Test
    fun metasOwnFragmentsWinOverPatterns() {
        val fragments = listOf(ThreadsTextFragment(Kind.TEXT, "see "), ThreadsTextFragment(Kind.LINK, "example.com/a…", "https://example.com/article"))
        assertEquals(fragments, ThreadsCaption.runs("see example.com/a…", fragments))
    }

    @Test
    fun aCaptionWithNothingToTapIsOneRun() {
        assertEquals(listOf(ThreadsTextFragment(Kind.TEXT, "plain words")), ThreadsCaption.runs("plain words", emptyList()))
        assertTrue(ThreadsCaption.runs("", emptyList()).isEmpty())
    }
}
