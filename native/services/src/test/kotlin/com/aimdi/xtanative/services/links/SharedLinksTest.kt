package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SharedLinksTest {
    @Test
    fun xLinksAreTakenFromCaptionsAndTheirPunctuation() {
        listOf("x.com", "twitter.com", "mobile.twitter.com", "www.x.com", "fxtwitter.com", "fixupx.com").forEach { host ->
            assertEquals("https://$host/reader/status/123?s=20", SharedLinks.appLink("A post worth reading: (https://$host/reader/status/123?s=20)."), host)
        }
        assertEquals("https://x.com/reader", SharedLinks.appLink("https://example.com first\nhttps://x.com/reader"))
    }

    @Test
    fun unsupportedSharesDeceptiveHostsAndOtherSchemesAreRefused() {
        listOf(
            "", "a note", "content://x.com/reader", "javascript:alert(1)", "https://x.com.evil.example/reader",
            "https://evil.example/x.com/reader", "https://x.com@evil.example/reader", "https://evil@x.com/reader",
            "https://x.com:8080/reader", "https://notwitter.com/reader",
        ).forEach { assertNull(SharedLinks.appLink(it), it) }
    }

    @Test
    fun aShortenedXLinkIsResolvedWithoutFollowingRedirects() = runBlocking {
        val http = FakeHttp { response(302, headers = mapOf("location" to "https://x.com/reader/status/123")) }
        assertEquals("https://x.com/reader/status/123", SharedLinks.resolve("Read https://t.co/short", http))
        assertFalse(http.asked.single().followRedirects)
    }

    @Test
    fun shortLinksToOtherSitesAndRedirectLoopsAreRefused() = runBlocking {
        listOf("https://example.com/reader", "file:///secret", "https://t.co/loop").forEach { destination ->
            val http = FakeHttp { response(302, headers = mapOf("location" to destination)) }
            assertNull(SharedLinks.resolve("https://t.co/short", http), destination)
            assertTrue(http.asked.size <= 3, destination)
        }
    }

    @Test
    fun ordinaryXSharesNeedNoRequest() = runBlocking {
        val http = FakeHttp { error("no request expected") }
        assertEquals("https://x.com/reader/status/1", SharedLinks.resolve("https://x.com/reader/status/1", http))
    }

    @Test
    fun pixivLinksAreTakenOnlyWhilePixivIsOn() {
        val share = "Look at this https://www.pixiv.net/artworks/123 !"
        assertNull(SharedLinks.appLink(share))
        assertEquals("https://www.pixiv.net/artworks/123", SharedLinks.appLink(share, pixiv = true))
        assertEquals("pixiv://illusts/1", SharedLinks.appLink("pixiv://illusts/1", pixiv = true))
        assertNull(SharedLinks.appLink("https://www.pixiv.net.evil.example/artworks/1", pixiv = true))
        assertFalse(SharedLinks.readsAsXLink("https://www.pixiv.net/en/"))
        assertTrue(SharedLinks.readsAsXLink("https://x.com/en"))
    }

    @Test
    fun anotherNetworksLinkIsHandedToTheLinkOpenerCaptionAndAll() {
        listOf(
            "https://bsky.app/profile/reader.bsky.social/post/3k", "https://mastodon.social/@reader/111", "https://www.threads.net/@reader/post/C1",
            "https://www.reddit.com/r/flutter/comments/abc/title/", "https://reader.substack.com/p/a-post",
        ).forEach { assertEquals(SharedTarget.Web(it), SharedLinks.target("Worth a read: $it."), it) }
        assertEquals(SharedTarget.Search("https://user@example.com/page"), SharedLinks.target("https://user@example.com/page"))
    }

    @Test
    fun aShareIsRoutedToTheAppTheWebOrASearch() {
        assertEquals(SharedTarget.AppLink("https://x.com/a/status/1"), SharedLinks.target("https://example.com/story then https://x.com/a/status/1"))
        assertEquals(SharedTarget.Web("https://bsky.app/profile/a"), SharedLinks.target("so true https://bsky.app/profile/a"))
        assertEquals(SharedTarget.PixivId("12345"), SharedLinks.target(" 12345 ", pixiv = true))
        assertEquals(SharedTarget.Search("12345"), SharedLinks.target("12345"))
        assertEquals(SharedTarget.Search("two words"), SharedLinks.target("  two \n\t words "))
        assertEquals(SharedTarget.Empty, SharedLinks.target("   "))
        assertEquals(SharedTarget.Search("look https://user:pw@example.com/a"), SharedLinks.target("look https://user:pw@example.com/a"))
    }

    @Test
    fun aLongShareIsCutToWhatXSearchTakesWithoutSplittingACharacter() {
        val query = (SharedLinks.target("😀".repeat(600)) as SharedTarget.Search).query
        assertEquals(500, query.codePointCount(0, query.length))
        assertEquals("😀".repeat(500), query)
    }

    @Test
    fun everyWebLinkInAShareIsFoundInOrder() {
        assertEquals(listOf("https://a.example/x", "http://b.example/y"), SharedLinks.urls("first https://a.example/x, then http://b.example/y! and pixiv://illusts/1"))
    }
}
