package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class XLinksTest {
    @Test
    fun theArticleIdComesOutOfAnArticleLinkOnAnyXHost() {
        assertEquals("2080123456789", XLinks.articleId("https://x.com/i/article/2080123456789"))
        listOf("twitter.com", "www.x.com", "mobile.twitter.com").forEach { assertEquals("42", XLinks.articleId("https://$it/i/article/42"), it) }
        assertEquals("42", XLinks.articleId("https://x.com/i/article/42/some-title"))
    }

    @Test
    fun otherLinksAreNoArticles() {
        listOf(
            "https://x.com/someone/status/123", "https://x.com/i/lists/9", "https://x.com/i/article", "https://x.com/i",
            "https://example.com/i/article/42", "", "not a url at all ::::",
        ).forEach { assertNull(XLinks.articleId(it), it) }
        assertNull(XLinks.articleId(null))
    }

    @Test
    fun thePostsArticleCardIsItsFirstArticleLink() {
        assertEquals("https://x.com/i/article/7", XLinks.firstArticleLink(listOf("https://example.com", "https://x.com/i/article/7", "https://x.com/i/article/8")))
        assertNull(XLinks.firstArticleLink(listOf("https://example.com", null)))
        assertNull(XLinks.firstArticleLink(emptyList()))
    }

    @Test
    fun broadcastIdsComeFromEveryFormABroadcastLinkTakes() {
        assertEquals("1YqJvqJvqJvqJvqJvqJvqJ", XLinks.broadcastId("https://x.com/i/broadcasts/1YqJvqJvqJvqJvqJvqJvqJ"))
        assertEquals("1abc", XLinks.broadcastId("https://x.com/i/broadcast/1abc"))
        assertEquals("1abc", XLinks.broadcastId("https://pscp.tv/w/1abc"))
        assertEquals("1abc", XLinks.broadcastId("https://www.pscp.tv/w/1abc"))
        listOf("twitter.com", "www.x.com", "mobile.twitter.com", "mobile.x.com").forEach {
            assertEquals("1abc", XLinks.broadcastId("https://$it/i/broadcasts/1abc"), it)
        }
        assertEquals("1disp", XLinks.broadcastId("x.com/i/broadcasts/1disp"))
    }

    @Test
    fun aTruncatedOrForeignLinkIsNoBroadcast() {
        listOf(
            "x.com/i/broadcasts/1YqJvq…", "https://x.com/i/article/42", "https://x.com/someone/status/123",
            "https://x.com/i/broadcasts", "https://example.com/i/broadcasts/1abc", "",
        ).forEach { assertNull(XLinks.broadcastId(it), it) }
        assertNull(XLinks.broadcastId(null))
    }

    @Test
    fun spacesAreToldFromBroadcasts() {
        assertEquals("1MnxnMDeQLeJO", XLinks.spaceId("https://x.com/i/spaces/1MnxnMDeQLeJO"))
        assertEquals("1abc", XLinks.spaceId("https://x.com/i/space/1abc"))
        assertEquals("1disp", XLinks.spaceId("x.com/i/spaces/1disp"))
        assertNull(XLinks.spaceId("x.com/i/spaces/1Owx…"))
        assertNull(XLinks.spaceId("https://x.com/i/broadcasts/1abc"))
        assertNull(XLinks.broadcastId("https://x.com/i/spaces/1abc"))
    }

    @Test
    fun liveLinksAreFoundInAPostBody() {
        assertEquals("1abc", XLinks.broadcastIdInText("watch https://x.com/i/broadcasts/1abc tonight"))
        assertEquals("1txt", XLinks.broadcastIdInText("live at x.com/i/broadcast/1txt"))
        assertEquals("1abc", XLinks.spaceIdInText("join https://x.com/i/spaces/1abc now"))
        assertEquals("1txt", XLinks.spaceIdInText("live at x.com/i/spaces/1txt."))
        assertNull(XLinks.spaceIdInText(null))
    }

    @Test
    fun liveWatchLinksCoverBroadcastsSpacesAndPeriscope() {
        listOf(
            "https://x.com/i/broadcasts/1abc", "https://x.com/i/broadcast/1solo", "https://x.com/i/spaces/1room",
            "https://www.x.com/i/space/1room", "x.com/i/spaces/1disp", "https://pscp.tv/w/1abc",
        ).forEach { assertTrue(XLinks.isLiveWatchUrl(it), it) }
        listOf("https://x.com/i/broadcasts/1YqJvq…", "https://x.com/someone/status/1", "https://x.com/i/lists/1", null)
            .forEach { assertFalse(XLinks.isLiveWatchUrl(it), it) }
    }

    @Test
    fun grokSharesAndLiveLinksNeedABrowserByName() {
        assertEquals("abc", XLinks.grokShareId("https://x.com/i/grok/share/abc"))
        assertTrue(XLinks.needsNamedBrowser("https://x.com/i/grok/share/abc"))
        assertTrue(XLinks.needsNamedBrowser("https://x.com/i/spaces/1room"))
        assertFalse(XLinks.needsNamedBrowser("https://x.com/someone/status/1"))
    }

    @Test
    fun articleAndLiveLinksAreLeftOutOfAPostsText() {
        assertTrue(XLinks.leftOutOfText("https://x.com/i/article/1"))
        assertTrue(XLinks.leftOutOfText("https://x.com/i/broadcasts/1abc"))
        assertTrue(XLinks.leftOutOfText("https://example.com", shownByCard = true))
        assertFalse(XLinks.leftOutOfText("https://example.com"))
    }

    @Test
    fun trackingParametersAreStripped() {
        assertEquals("https://x.com/a/status/1", XLinks.clean("https://x.com/a/status/1?s=20&t=xyz"))
        assertEquals("https://example.com/p?id=7", XLinks.clean("https://example.com/p?utm_source=tw&utm_medium=social&id=7"))
        assertEquals("https://example.com/p?s=size-m", XLinks.clean("https://example.com/p?s=size-m"))
        assertEquals("https://example.com/plain", XLinks.clean("https://example.com/plain"))
        assertEquals("https://x.com/search?q=hello", XLinks.clean("https://x.com/search?q=hello&s=1"))
        assertEquals("https://shop.com/x#frag", XLinks.clean("https://shop.com/x?fbclid=abc#frag"))
        assertEquals("not a url at all", XLinks.clean("not a url at all"))
    }

    @Test
    fun theRestOfTheCommonClickIdsGoToo() {
        assertEquals("https://shop.com/x?keep=yes", XLinks.clean("https://shop.com/x?msclkid=1&gbraid=2&keep=yes"))
        assertEquals("https://shop.com/x", XLinks.clean("https://shop.com/x?ttclid=tik&igsh=inst&ocid=ms"))
        assertEquals("https://news.example/a?id=9", XLinks.clean("https://news.example/a?mtm_campaign=x&pk_source=y&hsa_cam=1&id=9"))
        assertEquals("https://shop.com/x", XLinks.clean("https://shop.com/x?_hsenc=a&li_fat_id=b&srsltid=c"))
    }

    @Test
    fun preparingFollowsTheSwitchAndStripsWhenUnset() {
        val dirty = "https://example.com/a?utm_source=x&keep=1"
        assertEquals("https://example.com/a?keep=1", XLinks.prepare(dirty, true))
        assertEquals(dirty, XLinks.prepare(dirty, false))
        assertEquals("https://example.com/a?keep=1", XLinks.prepare(dirty, null))
    }

    @Test
    fun listIdsComeFromListLinksAndBareNumbers() {
        assertEquals("1234567890123456789", XLinks.listId("https://x.com/i/lists/1234567890123456789"))
        assertEquals("42", XLinks.listId("https://twitter.com/i/lists/42/"))
        assertEquals("987654321", XLinks.listId("  987654321  "))
        assertNull(XLinks.listId("https://x.com/SomeUser/lists"))
        assertNull(XLinks.listId("https://x.com/i/lists/not-a-number"))
        assertNull(XLinks.listId("hello"))
    }

    @Test
    fun xLinksAreReadAsListsProfilesAndPosts() {
        assertEquals(XLink.XList("123"), XLinks.parse("https://x.com/i/lists/123"))
        assertEquals(XLink.Profile("DogsTrust"), XLinks.parse("https://x.com/DogsTrust"))
        assertEquals(XLink.Profile("DogsTrust"), XLinks.parse("https://x.com/DogsTrust/lists"))
        assertEquals(XLink.Profile("DogsTrust", XProfileTab.MEDIA), XLinks.parse("https://x.com/DogsTrust/media"))
        assertEquals(XLink.Post("DogsTrust", "1"), XLinks.parse("https://x.com/DogsTrust/status/1"))
        // /i/… is X's reserved path, never a profile called "i".
        assertEquals(XLink.Post(null, "9"), XLinks.parse("https://x.com/i/topics/tweet/9"))
        assertEquals(XLink.Unknown, XLinks.parse("https://x.com/i/flow/login"))
        assertEquals(XLink.Post("a", "5", photo = 2), XLinks.parse("https://x.com/a/status/5/photo/2"))
        assertEquals(XLink.Post("a", "5", direct = true), XLinks.parse("https://fxtwitter.com/a/status/5.jpg"))
        assertEquals(XLink.Community("147", about = true), XLinks.parse("https://x.com/i/communities/147/about"))
        assertEquals(XLink.GrokShare("https://x.com/i/grok/share/abc"), XLinks.parse("https://x.com/i/grok/share/abc"))
    }

    @Test
    fun broadcastsAndSpacesAreReadInsteadOfFailingOpen() {
        assertEquals(XLink.Live("https://x.com/i/broadcasts/1abc", isSpace = false), XLinks.parse("https://x.com/i/broadcasts/1abc"))
        assertEquals(XLink.Live("https://x.com/i/spaces/1room", isSpace = true), XLinks.parse("https://x.com/i/spaces/1room"))
        assertEquals(XLink.Live("https://x.com/i/spaces/1solo", isSpace = true), XLinks.parse("https://twitter.com/i/space/1solo"))
        assertEquals(XLink.Live("https://x.com/i/broadcasts/1solo", isSpace = false), XLinks.parse("https://x.com/i/broadcast/1solo"))
        assertEquals(XLink.Live("https://x.com/i/spaces/1room", isSpace = true), XLinks.parse("https://mobile.x.com/i/spaces/1room"))
        assertEquals(XLink.Unknown, XLinks.parse("https://x.com/i/broadcasts/1YqJvq…"))
        assertEquals(XLink.Unknown, XLinks.parse("https://x.com/i/spaces/1Owx..."))
        assertEquals(XLink.Live("https://x.com/i/broadcasts/1vod", isSpace = false), XLinks.parse("https://pscp.tv/w/1vod"))
    }

    @Test
    fun aShortLinkIsFollowedByOneRequestThatDoesNotFollowIt() = runBlocking {
        val http = FakeHttp { response(301, headers = mapOf("location" to "https://x.com/reader/status/123")) }
        assertEquals(XLink.Post("reader", "123"), XLinks.resolve("https://t.co/short", http))
        assertEquals(false, http.asked.single().followRedirects)

        val loop = FakeHttp { response(302, headers = mapOf("location" to "https://t.co/again")) }
        assertEquals(XLink.Unknown, XLinks.resolve("https://t.co/short", loop))
        assertEquals(1, loop.asked.size)

        val nowhere = FakeHttp { response(200) }
        assertEquals(XLink.Unknown, XLinks.resolve("https://t.co/short", nowhere))
    }

    @Test
    fun aProfileLinksScreenNameIsReadWithoutResolving() {
        assertEquals("jack", XLinks.profileScreenName("https://X.com/jack"))
        assertNull(XLinks.profileScreenName("https://x.com/jack/status/1"))
        assertNull(XLinks.profileScreenName("https://example.com/jack"))
    }

    @Test
    fun aPostIsSharedUnderTheReadersHostAndUnderIWithoutAnAuthor() {
        val post = XPost(id = "42", author = XUser("1", "maya", "Maya"), createdAt = null, text = "")
        assertEquals("https://x.com/maya/status/42", XLinks.shareUrl(post))
        assertEquals("https://fixupx.com/maya/status/42", XLinks.shareUrl(post, "https://fixupx.com//"))
        assertEquals("https://x.com/i/status/42", XLinks.shareUrl(post.copy(author = post.author.copy(screenName = " "))))
        assertNull(XLinks.shareUrl(post.copy(id = " ")))
    }

    @Test
    fun theWaybackMachineIsAskedOnlyForRealHandlesAndIds() {
        assertEquals("https://web.archive.org/web/*/twitter.com/jack/status/20*", XLinks.waybackUrl("jack", "20"))
        assertNull(XLinks.waybackUrl("not a handle", "20"))
        assertNull(XLinks.waybackUrl("jack", "20x"))
    }
}
