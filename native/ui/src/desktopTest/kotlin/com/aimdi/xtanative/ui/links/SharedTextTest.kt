package com.aimdi.xtanative.ui.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SharedTextTest {
    private fun step(text: String, pixiv: Boolean = false, network: (String) -> Boolean = { false }, resolve: suspend (String) -> String? = { it }) =
        runBlocking { SharedText.step(text, pixiv, network) { resolve(it) } }

    @Test
    fun anXLinkWinsOverTheCaptionsOtherLinks() {
        val shared = "Look at https://example.com/story and https://x.com/maya/status/12?s=20"
        assertEquals(SharedStep.Link("https://x.com/maya/status/12", inPost = false), step(shared) { "https://x.com/maya/status/12" })
    }

    @Test
    fun aShortLinkThatLeadsNowhereCannotBeOpened() {
        assertEquals(SharedStep.Unable, step("https://t.co/abc") { null })
        assertEquals(SharedStep.Unable, step("https://t.co/abc") { error("offline") })
    }

    @Test
    fun aNetworksLinkOpensOnItsScreenFirst() {
        val seen = ArrayList<String>()
        val opened = step("https://www.threads.net/@maya/post/C1 and https://x.com/a", network = { url -> seen += url; url.contains("threads") })
        assertEquals(SharedStep.Opened, opened)
        assertEquals(listOf("https://www.threads.net/@maya/post/C1"), seen)
    }

    @Test
    fun anyOtherPageOpensAsAPostsLinkAndTextIsSearched() {
        assertEquals(SharedStep.Link("https://example.com/a", inPost = true), step("read https://example.com/a."))
        assertEquals(SharedStep.Search("hello there"), step("  hello \n there "))
        assertEquals(SharedStep.Unable, step("   "))
    }

    @Test
    fun aNumberIsAPixivWorkOnlyWhilePixivIsOn() {
        assertEquals(SharedStep.PixivWork("12345"), step("12345", pixiv = true))
        assertEquals(SharedStep.Search("12345"), step("12345", pixiv = false))
        assertEquals(SharedStep.Outside("https://www.pixiv.net/en/artworks/1"), step("https://www.pixiv.net/en/artworks/1", pixiv = true))
    }

    @Test
    fun aShareIsReadFromItsTextThenItsClipCutTo64KCharacters() {
        assertEquals("text", SharedText.handed("text", "clip", "content://a"))
        assertEquals("clip", SharedText.handed(null, "clip", "content://a"))
        assertEquals("content://a", SharedText.handed(null, null, "content://a"))
        assertEquals("", SharedText.handed(null, null, null))
        assertEquals(SharedText.MAX_CHARS, SharedText.handed("a".repeat(100_000), null, null).length)
    }
}
